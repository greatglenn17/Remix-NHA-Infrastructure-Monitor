// Run against the emulator only: npm test (firebase emulators:exec with a demo project).
process.env.DUPLICATE_GUARD_WAIT_MS = "0";
const test = require("node:test");
const assert = require("node:assert");
const { getFirestore } = require("firebase-admin/firestore");
const { guardProjectDocument, stampProjectCreationTimes } = require("./index");
const { GUARD_UID } = require("./duplicateGuard");

const db = () => getFirestore();
const base = { name: "Bulakan Housing", location: "Bulakan, Bulacan", revision: 1 };

/** Writes projects/{id} (merged) and runs the trigger on that write, as Firestore would; returns the project after it. */
async function write(id, data) {
  const ref = db().doc(`projects/${id}`);
  const before = await ref.get();
  await ref.set(data, { merge: true });
  await guardProjectDocument.run({ before, after: await ref.get() }, { params: { projectId: id } });
  return (await ref.get()).data();
}

test.beforeEach(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "emulator only");
  await db().recursiveDelete(db().collection("projects"));
  await db().recursiveDelete(db().collection("records"));
});

test("a project gets the server's creation time, whatever the client sent", async () => {
  const created = await write("a", { ...base, serverCreatedAt: 0 });
  assert.strictEqual(created.serverCreatedAt, (await db().doc("projects/a").get()).createTime.toMillis());
  assert.strictEqual((await write("a", { contractor: "X", serverCreatedAt: 5 })).serverCreatedAt, created.serverCreatedAt);
});

test("a new, empty project under an older one's name is removed", async () => {
  await write("a-old", base);
  await db().doc("records/audit").set({ projectUuid: "b-new", type: "audit_logs", isDeleted: false });
  const extra = await write("b-new", { ...base, name: " bulakan  housing", createdAt: Date.now() });
  assert.strictEqual(extra.isDeleted, true);
  assert.strictEqual(extra.duplicateOf, "a-old");
  assert.strictEqual(extra.revision, 2);
  assert.strictEqual(extra.updatedByUid, GUARD_UID);
  assert.notStrictEqual((await db().doc("projects/a-old").get()).get("isDeleted"), true);
});

test("a project from before the guard is stamped on any project's write", async () => {
  await db().doc("projects/old").set(base);
  await write("other", { name: "Other", location: "Elsewhere" });
  const old = await db().doc("projects/old").get();
  assert.strictEqual(old.get("serverCreatedAt"), old.createTime.toMillis());
});

test("a removed copy that a phone sends again is kept", async () => {
  await write("a-old", base);
  assert.strictEqual((await write("b-new", { ...base, createdAt: Date.now() })).isDeleted, true);
  const again = await write("b-new", { isDeleted: false, updatedByUid: "creator" });
  assert.deepStrictEqual([again.isDeleted, again.duplicateOf, again.updatedByUid], [false, "a-old", "creator"]);
});

test("an older deleted project brought back under a newer project's name is deleted again", async () => {
  await write("mine", { name: "Mine", location: "Here", isDeleted: true, duplicateOf: "x", revision: 3 });
  await write("victim", base);
  const back = await write("mine", { name: base.name, location: base.location, isDeleted: false, revision: 4 });
  assert.deepStrictEqual([back.isDeleted, back.revision, back.updatedByUid, "duplicateOf" in back], [true, 5, GUARD_UID, false]);
  assert.notStrictEqual((await db().doc("projects/victim").get()).get("isDeleted"), true);
});

test("an extra project with data, or made offline a while ago, is kept", async () => {
  await write("a-old", base);
  await db().doc("records/report").set({ projectUuid: "b-data", type: "weekly_reports", isDeleted: false });
  assert.notStrictEqual((await write("b-data", { ...base, createdAt: Date.now() })).isDeleted, true);
  assert.notStrictEqual((await write("c-backdated", { ...base, createdAt: 0 })).isDeleted, true);
});

test("a rename into another project's name is put back", async () => {
  await write("victim", base);
  await write("mine", { name: "Mine", location: "Here", revision: 4 });
  const renamed = await write("mine", { name: base.name, location: base.location, revision: 5 });
  assert.deepStrictEqual([renamed.name, renamed.location, renamed.revision, renamed.updatedByUid], ["Mine", "Here", 6, GUARD_UID]);
  assert.notStrictEqual(renamed.isDeleted, true);
});

test("a rename already changed again is not put back", async () => {
  await write("victim", base);
  await write("mine", { name: "Mine", location: "Here", revision: 4 });
  const ref = db().doc("projects/mine");
  const before = await ref.get();
  await ref.set({ name: base.name, location: base.location, revision: 5 }, { merge: true });
  const after = await ref.get();
  await ref.set({ name: "Mine 2", revision: 6 }, { merge: true });
  await guardProjectDocument.run({ before, after }, { params: { projectId: "mine" } });
  assert.deepStrictEqual([(await ref.get()).get("name"), (await ref.get()).get("revision")], ["Mine 2", 6]);
});

test("the daily job stamps the projects from before the guard", async () => {
  await db().doc("projects/old").set(base);
  await stampProjectCreationTimes.run({}, {});
  const old = await db().doc("projects/old").get();
  assert.strictEqual(old.get("serverCreatedAt"), old.createTime.toMillis());
});
