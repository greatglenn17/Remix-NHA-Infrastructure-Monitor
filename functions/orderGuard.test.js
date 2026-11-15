const test = require("node:test");
const assert = require("node:assert");
const { read, isApproved, approvedDataOf, concernsOrders, orderFix } = require("./orderGuard");

const VO = "Variation Order No. 1 (Additive) - PHP 1,000,000.00";
const json = (documentName, status = "Pending", more = {}) => JSON.stringify({ documentName, status, remarks: "", isCoreChecklist: 1, fileUrl: "", ...more });
const doc = (documentName, status, more = {}) => ({ type: "pending_documents", isDeleted: false, updatedByUid: "eng", data: json(documentName, status), ...more });
const fe = (now, approvedData = null) => orderFix({ now, role: "FIELD_ENGINEER", approvedData });

test("an order is a live pending document whose name says so, the way the phones read it", () => {
  for (const name of [VO, "time extension no. 2 - 30 Days", "Partial Work Suspension Order No. 3", "WORK RESUMPTION ORDER NO. 3",
    "2nd Continuance of Work Suspension Order No. 3", "Varıatİon Order", "Time Extenſion"]) {
    assert.ok(read(doc(name)).order, name);
  }
  assert.deepStrictEqual(read(doc("As-built plan")), { order: null });
  assert.deepStrictEqual(read(doc(VO, "Pending", { isDeleted: true })), { order: null });
  assert.deepStrictEqual(read(doc(VO, "Pending", { type: "weekly_reports" })), { order: null });
  assert.deepStrictEqual(read(null), { order: null });
  assert.ok(isApproved(read(doc(VO, "APPROVED")).order));
  assert.ok(!isApproved(read(doc(VO, "Signed")).order));
  assert.strictEqual(approvedDataOf(doc(VO, "Approved")), json(VO, "Approved"));
  assert.strictEqual(approvedDataOf(doc(VO, "Approved", { isDeleted: true })), null);
});

test("data the app and the website never write is malformed, whatever a phone would make of it", () => {
  const live = (data) => read({ type: "pending_documents", data });
  for (const data of [undefined, 5, "not json", json(VO, "Approved") + "}", "{'documentName':'" + VO + "','status':'Approved'}", "null", "[]",
    JSON.stringify({ documentName: { x: "Time Extension No. 9 - 300 Days" }, status: "Approved" }),
    JSON.stringify({ documentName: VO, status: ["Approved"] }),
    JSON.stringify({ documentName: VO, status: "Approved", remarks: { toString: 0 } }),
    JSON.stringify({ status: "Approved" }),
    // A phone keeps the status or remarks it has when the data leaves one out: an approved order stays approved there.
    JSON.stringify({ documentName: VO, remarks: "" }), JSON.stringify({ documentName: VO, status: "Pending" }),
    JSON.stringify({ documentName: VO, status: null, remarks: "" })]) {
    assert.deepStrictEqual(live(data), { malformed: true, order: null }, String(data));
  }
  assert.deepStrictEqual(live(JSON.stringify({ documentName: "As-built plan" })), { order: null });
  // As a phone writes it: its own key order, the slash escaped.
  assert.deepStrictEqual(live('{"fileUrl":"attachments\\/x.pdf","isCoreChecklist":1,"remarks":"","status":"Approved","documentName":"' + VO + '"}').order,
    { name: VO, status: "Approved", remarks: "" });
  assert.ok(concernsOrders(null, { type: "pending_documents", data: "not json" }));
  assert.ok(concernsOrders(doc(VO), doc("As-built plan")));
  assert.ok(!concernsOrders(doc("As-built plan", "Approved"), doc("As-built plan", "Approved", { isDeleted: true })));
});

test("with no approval on record, a Field Engineer adds an order and works on it, and their other documents are theirs", () => {
  for (const now of [doc(VO), doc(VO, "Notarized"), doc(VO, "Pending", { isDeleted: true }),
    doc("As-built plan", "Approved"), doc("As-built plan", "Approved", { isDeleted: true }), doc(VO, "Approved", { type: "weekly_reports" })]) {
    assert.strictEqual(fe(now), null);
  }
});

test("with no approval on record, an approved order goes back to Pending and malformed data is marked deleted", () => {
  const fix = fe(doc(VO, "approved", { data: json(VO, "approved", { fileUrl: "attachments/x.pdf" }) }));
  assert.strictEqual(fix.name, VO);
  assert.deepStrictEqual(JSON.parse(fix.patch.data), { ...JSON.parse(json(VO)), fileUrl: "attachments/x.pdf" });
  assert.deepStrictEqual(fe({ type: "pending_documents", data: json(VO, "Approved") + "}" }).patch, { isDeleted: true });
  assert.strictEqual(fe({ type: "pending_documents", isDeleted: true, data: "not json" }), null);
});

test("an order an approver approved stays as approved: any other state is put back", () => {
  const approved = json(VO, "Approved"), back = { name: VO, patch: { isDeleted: false, data: approved } };
  for (const now of [doc(VO, "Pending"), doc(VO.replace("1,000", "9,000"), "Approved"), doc("Scanned letter", "Approved"),
    doc(VO, "Approved", { isDeleted: true }), doc(VO, "Approved", { data: json(VO, "Approved", { remarks: "Date Approved: 2020-01-01" }) }),
    doc(VO, "Approved", { data: JSON.stringify({ documentName: VO, status: ["Approved"] }) }), doc(VO, "Approved", { data: "not json" })]) {
    assert.deepStrictEqual(fe(now, approved), back);
  }
  // The same order re-sent as it is, or with something that is not the order's changed.
  assert.strictEqual(fe(doc(VO, "Approved"), approved), null);
  assert.strictEqual(fe(doc(VO, "Approved", { data: json(VO, "Approved", { fileUrl: "attachments/x.pdf" }) }), approved), null);
});

test("an approver's own write is left alone; an unknown writer is no approver", () => {
  for (const role of ["ENGINEER_ADMIN", "SUPER_ADMIN"]) {
    assert.strictEqual(orderFix({ now: doc(VO, "Approved"), role, approvedData: null }), null);
    assert.strictEqual(orderFix({ now: doc(VO, "Pending", { isDeleted: true }), role, approvedData: json(VO, "Approved") }), null);
  }
  for (const role of ["", "VIEWER", undefined]) assert.ok(orderFix({ now: doc(VO, "Approved"), role, approvedData: null }));
});
