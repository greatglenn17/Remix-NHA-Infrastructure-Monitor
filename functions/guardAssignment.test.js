// Run against the emulator only: npm test (firebase emulators:exec with a demo project).
const test = require("node:test");
const assert = require("node:assert");
const { getFirestore } = require("firebase-admin/firestore");
const { guardRecordAssignment } = require("./index");

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

test.before(() => assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "emulator only"));
test.beforeEach(async () => { await ref().delete(); await db().doc("projects/assign-guard-p").delete(); });
test.after(async () => { await ref().delete(); await db().doc("projects/assign-guard-p").delete(); });

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
