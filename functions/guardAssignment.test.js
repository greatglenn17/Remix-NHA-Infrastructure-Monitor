// Run against the emulator only: npm test (firebase emulators:exec with a demo project).
const test = require("node:test");
const assert = require("node:assert");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");
const { guardRecordAssignment, guardOrderRecord } = require("./index");

const db = () => getFirestore();
const project = (assignedUids) => db().doc("projects/assign-guard-p").set(assignedUids ? { name: "P", assignedUids } : { name: "P" });
const ref = () => db().doc("records/assign-guard-r");
const record = (assignedUids, more = {}) => ({
  uuid: "assign-guard-r", type: "sdp_lot_progress", projectUuid: "assign-guard-p", revision: 4, updatedAt: 40, updatedByUid: "sa",
  isDeleted: false, data: "{}", ...(assignedUids ? { assignedUids } : {}), ...more,
});
/** One client write, then the trigger on it, as the server runs it. */
async function write(data) {
  const before = await ref().get();
  await ref().set(data);
  await guardRecordAssignment.run({ before, after: await ref().get() }, { params: { recordId: "assign-guard-r" } });
  return (await ref().get()).data();
}

const SA = "assign-guard-sa";
const clear = async () => { for (const path of ["records/assign-guard-r", "projects/assign-guard-p", "orderGuard/assign-guard-r"]) await db().doc(path).delete(); };
test.before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST, "emulator only");
  await getAuth().deleteUser(SA).catch(() => {});
  await getAuth().createUser({ uid: SA });
  await getAuth().setCustomUserClaims(SA, { role: "SUPER_ADMIN", approved: true, allProjects: true });
});
test.beforeEach(clear);
test.after(async () => { await clear(); await getAuth().deleteUser(SA).catch(() => {}); });

test("a record uploaded with an older copy of the list takes the project's, and nothing else changes", async () => {
  await project(["sa", "ea"]);
  const now = await write(record(["sa"]));
  assert.deepStrictEqual(now.assignedUids, ["sa", "ea"]);
  assert.deepStrictEqual({ ...now, assignedUids: ["sa"] }, record(["sa"]));
});

test("a record with no list, or a list changed to an older one, takes the project's", async () => {
  await project(["sa", "ea"]);
  assert.deepStrictEqual((await write(record(null))).assignedUids, ["sa", "ea"]);
  assert.deepStrictEqual((await write(record(["sa"], { revision: 5 }))).assignedUids, ["sa", "ea"]);
});

test("left alone: the project's list in another order, an edit that keeps the record's list, a project with no list", async () => {
  await project(["sa", "ea"]);
  assert.deepStrictEqual((await write(record(["ea", "sa"]))).assignedUids, ["ea", "sa"]);
  await ref().set(record(["sa"]));
  assert.deepStrictEqual((await write(record(["sa"], { revision: 5 }))).assignedUids, ["sa"]);
  await project(null);
  assert.deepStrictEqual((await write(record(["fe"], { revision: 6 }))).assignedUids, ["fe"]);
});

test("a record whose project is not in the cloud, or with no project, is left alone", async () => {
  assert.deepStrictEqual((await write(record(["sa"]))).assignedUids, ["sa"]);
  assert.deepStrictEqual((await write(record(["fe"], { projectUuid: "" }))).assignedUids, ["fe"]);
});

test("an order given its project's list is not raised again by the order guard on the same write", async () => {
  await project([SA, "ea"]);
  const order = JSON.stringify({ documentName: "Variation Order No. 1 (Additive) - PHP 1,000,000.00", status: "Submitted", remarks: "", isCoreChecklist: 1, fileUrl: "" });
  const before = await ref().get();
  await ref().set(record([SA], { type: "pending_documents", updatedByUid: SA, data: order }));
  const event = { before, after: await ref().get() }, context = { params: { recordId: "assign-guard-r" } };
  await guardRecordAssignment.run(event, context); // the shorter run usually commits first
  await guardOrderRecord.run(event, context);
  const now = (await ref().get()).data();
  assert.deepStrictEqual([now.assignedUids, now.revision, now.updatedAt, now.updatedByUid], [[SA, "ea"], 4, 40, SA]);
});
