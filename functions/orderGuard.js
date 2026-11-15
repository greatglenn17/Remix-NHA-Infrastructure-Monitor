// Who may approve an order. An order (variation order, time extension, work suspension or resumption) is a
// pending_documents record like any other document, known only by its name, and its status sits inside the record's
// `data` string, which firestore.rules cannot read. The app and the website keep a Field Engineer from approving one
// or changing an approved one in their forms only. So the server keeps, apart from the record and out of every
// client's reach, what an approver last made of each order (index.js: guardOrderRecord), and puts back a record that
// someone else's write made differ from it.

/** updatedByUid of the guard's own writes; the rules let no client write a uid that is not theirs. */
const ORDER_GUARD_UID = "order-guard";

/** UserRole.kt: APPROVE_ORDER. */
const APPROVERS = ["SUPER_ADMIN", "ENGINEER_ADMIN"];

/**
 * How long before the guard's first run a record still counts as written before there was a guard: the write that
 * set off that run, and any other still waiting for its own run, is newer than this.
 */
const LEGACY_MARGIN_MS = 10 * 60 * 1000;

/** What the phones look for in a document's name, without case (ProjectAttention.ORDER_TYPES). */
const ORDER_NAMES = ["variation order", "time extension", "work suspension", "work resumption"];

// Kotlin's ignoreCase, letter by letter: upper case, then lower case. A plain toLowerCase() would miss a name the
// phones still read as an order (a dotless or dotted I, a long s).
const fold = (text) => [...text].map((c) => c.toUpperCase().toLowerCase()[0] ?? c).join("");

/**
 * How a record reads: { order } with the order it holds ({ name, status, remarks }) or null (deleted, another type, a
 * name that is no order's), or { malformed: true } for a live pending_documents record whose data is not what the app
 * and the website write (strict JSON of an object, the name as text, the status and remarks as text or absent). The phones read such data
 * more loosely than this server can, so what it would be to them is not known here.
 */
function read(record) {
  if (!record || record.type !== "pending_documents" || record.isDeleted === true) return { order: null };
  let data;
  try {
    data = JSON.parse(record.data);
  } catch {
    return { malformed: true, order: null };
  }
  const text = (value) => value == null || typeof value === "string";
  if (data === null || typeof data !== "object" || Array.isArray(data)
    || typeof data.documentName !== "string" || !text(data.status) || !text(data.remarks)) return { malformed: true, order: null };
  if (!ORDER_NAMES.some((order) => fold(data.documentName).includes(order))) return { order: null };
  return { order: { name: data.documentName, status: data.status ?? "", remarks: data.remarks ?? "" } };
}

const isApproved = (order) => !!order && fold(order.status) === "approved";

/** The data string of the approved order a record holds, or null: what an approver's write leaves on record. */
const approvedDataOf = (record) => (isApproved(read(record).order) ? record.data : null);

/** Whether a write is about an order at all, as far as the record before or after it shows. */
const concernsOrders = (before, after) => [before, after].some((record) => read(record).malformed || !!read(record).order);

/**
 * What to do about a record as it is `now`, last written by an account with `role`, when `approvedData` is the data
 * string of the order as an approver last approved it (null: no approver has, or one took the approval back or
 * deleted it). Null when there is nothing to do, else { name, patch }:
 * - approved by an approver: the record must still be that order, by its name (it carries the amount or the days),
 *   its status and its remarks (the dates). Anything else, a delete included, is put back.
 * - not approved: a record that reads as an approved order goes back to Pending, and one with malformed data is
 *   marked deleted.
 * An approver's own write is theirs to make; so are a file attached to an order and everything about a document that
 * is no order.
 */
function orderFix({ now, role, approvedData }) {
  if (!now || now.type !== "pending_documents" || APPROVERS.includes(role)) return null;
  const is = read(now);
  if (approvedData != null) {
    const was = read({ type: "pending_documents", data: approvedData }).order;
    const same = was && is.order && is.order.name === was.name && is.order.status === was.status && is.order.remarks === was.remarks;
    return same ? null : { name: was?.name ?? "An order", patch: { isDeleted: false, data: approvedData } };
  }
  if (is.malformed) return { name: "A document", patch: { isDeleted: true } };
  if (!isApproved(is.order)) return null;
  return { name: is.order.name, patch: { data: JSON.stringify({ ...JSON.parse(now.data), status: "Pending" }) } };
}

module.exports = { ORDER_GUARD_UID, APPROVERS, LEGACY_MARGIN_MS, read, isApproved, approvedDataOf, concernsOrders, orderFix };
