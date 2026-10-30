// Run against the emulator only: npm test (firebase emulators:exec with a demo project).
const test = require("node:test");
const assert = require("node:assert");
const { getFirestore } = require("firebase-admin/firestore");
const { purgeOldRecordTombstones } = require("./index");

const DAY = 24 * 60 * 60 * 1000;

test("purges only record tombstones older than 30 days", async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST, "emulator only");
  const db = getFirestore();
  const now = Date.now();
  await db.doc("records/oldDeleted").set({ isDeleted: true, updatedAt: now - 31 * DAY });
  await db.doc("records/newDeleted").set({ isDeleted: true, updatedAt: now - 29 * DAY });
  await db.doc("records/oldLive").set({ isDeleted: false, updatedAt: now - 90 * DAY });
  await db.doc("projects/oldDeleted").set({ isDeleted: true, updatedAt: now - 90 * DAY });

  await purgeOldRecordTombstones.run({}, {});

  const exists = async (path) => (await db.doc(path).get()).exists;
  assert.strictEqual(await exists("records/oldDeleted"), false);
  assert.strictEqual(await exists("records/newDeleted"), true);
  assert.strictEqual(await exists("records/oldLive"), true);
  assert.strictEqual(await exists("projects/oldDeleted"), true);
});
