// Run against the emulator only: npm test (firebase emulators:exec with a demo project).
const test = require("node:test");
const assert = require("node:assert");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const { guardOrderRecord } = require("./index");
const { ORDER_GUARD_UID, LEGACY_MARGIN_MS } = require("./orderGuard");

const db = () => getFirestore();
const VO = "Variation Order No. 1 (Additive) - PHP 1,000,000.00";
const json = (status, more = {}) => JSON.stringify({ documentName: VO, status, remarks: "", isCoreChecklist: 1, fileUrl: "", ...more });
const record = (uid, status, more = {}) => ({
  uuid: "vo", type: "pending_documents", projectUuid: "p", assignedUids: ["fe"], revision: 1, updatedAt: 1, updatedByUid: uid, isDeleted: false,
  data: json(status), ...more,
});
const ref = (id = "vo") => db().doc(`records/${id}`);
const stored = async (id = "vo") => (await ref(id).get()).data();
const onRecord = async (id = "vo") => (await db().doc(`orderGuard/${id}`).get()).get("approvedData");
const statusOf = (data) => JSON.parse(data.data).status;
/** [status, who wrote it last, deleted] of the record as it is. */
const state = async (id = "vo") => {
  const now = await stored(id);
  return [statusOf(now), now.updatedByUid, now.isDeleted];
};

/** One write as a client makes it (the phone replaces the record, the website and a delete merge into it), not yet seen by the trigger. */
async function write(data, merge = false, id = "vo") {
  const before = await ref(id).get();
  await ref(id).set(data, { merge });
  return { before, after: await ref(id).get(), id };
}
const guard = (event) => guardOrderRecord.run(event, { params: { recordId: event.id } });
/** The phone's and the website's delete: a merged tombstone that keeps the data. */
const tombstone = (uid, revision) => write({ isDeleted: true, revision, updatedAt: revision, updatedByUid: uid }, true);

test.before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST, "emulator only");
  for (const [uid, role] of [["fe", "FIELD_ENGINEER"], ["ea", "ENGINEER_ADMIN"], ["sa", "SUPER_ADMIN"]]) {
    await getAuth().deleteUser(uid).catch(() => {});
    await getAuth().createUser({ uid });
    await getAuth().setCustomUserClaims(uid, { role, approved: true, allProjects: false });
  }
});

test.beforeEach(async () => {
  for (const name of ["records", "orderGuard", "orderGuardStart"]) await db().recursiveDelete(db().collection(name));
});

test("a Field Engineer's approval goes back to Pending; an Engineer Admin's and the Super Admin's stay", async () => {
  await guard(await write(record("ea", "Submitted")));
  await guard(await write(record("fe", "Approved", { revision: 2, data: json("Approved", { fileUrl: "attachments/x.pdf" }) })));
  let now = await stored();
  assert.deepStrictEqual([JSON.parse(now.data), now.revision, now.updatedByUid, now.isDeleted],
    [JSON.parse(json("Pending", { fileUrl: "attachments/x.pdf" })), 3, ORDER_GUARD_UID, false]);
  assert.strictEqual(await onRecord(), null);

  for (const uid of ["ea", "sa"]) {
    await guard(await write(record(uid, "Approved", { revision: 4 })));
    now = await stored();
    assert.deepStrictEqual([statusOf(now), now.revision, now.updatedByUid], ["Approved", 4, uid]);
    assert.strictEqual(await onRecord(), now.data);
    await guard(await write(record("ea", "Submitted", { revision: 5 })));
    assert.strictEqual(await onRecord(), null);
  }
});

test("an order added as approved by a Field Engineer, or by an account that is gone, stays as Pending", async () => {
  for (const uid of ["fe", "nobody"]) {
    await ref().delete();
    await guard(await write(record(uid, "approved")));
    assert.deepStrictEqual(await state(), ["Pending", ORDER_GUARD_UID, false]);
  }
});

test("an approved order a Field Engineer deletes, changes, renames or sends back is put back as approved", async () => {
  await guard(await write(record("ea", "Approved")));
  const approved = (await stored()).data;
  const changes = [
    () => tombstone("fe", 2),
    () => write(record("fe", "Approved", { data: approved.replace("1,000,000", "9,000,000") })),
    () => write(record("fe", "Approved", { data: json("Approved", { remarks: "Date Approved: 2020-01-01" }) })),
    () => write(record("fe", "Approved", { data: json("Approved", { documentName: "Scanned letter" }) })),
    () => write(record("fe", "Submitted")),
    () => write(record("fe", "Approved", { data: json(["Approved"]) })),
    () => write(record("fe", "Approved", { data: approved + "}" })),
    // No status: a phone that has the order as approved keeps it so, under the new amount.
    () => write(record("fe", "", { data: JSON.stringify({ documentName: VO.replace("1,000,000", "9,000,000"), remarks: "" }) })),
  ];
  for (const change of changes) {
    const event = await change();
    await guard(event);
    const now = await stored();
    assert.deepStrictEqual([now.data, now.isDeleted, now.updatedByUid], [approved, false, ORDER_GUARD_UID]);
    // The guard's own write, seen by the trigger in its turn, is left as it is.
    await guard({ before: event.after, after: await ref().get(), id: "vo" });
    assert.deepStrictEqual(await stored(), now);
  }
});

test("an approved order an Engineer Admin deleted comes back from a Field Engineer as Pending only", async () => {
  await guard(await write(record("ea", "Approved")));
  await guard(await tombstone("ea", 2));
  assert.deepStrictEqual(await state(), ["Approved", "ea", true]);
  await guard(await write(record("fe", "Approved", { revision: 3 })));
  assert.deepStrictEqual(await state(), ["Pending", ORDER_GUARD_UID, false]);
});

test("data that is not what the app writes is marked deleted when a Field Engineer sends it", async () => {
  for (const data of [json("Approved") + "}", "{'documentName':'Time Extension No. 9 - 300 Days','status':'Approved'}",
    JSON.stringify({ documentName: { x: "Time Extension No. 9 - 300 Days" }, status: "Approved" }), json("Approved", { remarks: { toString: 0 } }),
    JSON.stringify({ documentName: VO, remarks: "" })]) {
    await ref().delete();
    await guard(await write(record("fe", "", { data })));
    const now = await stored();
    assert.deepStrictEqual([now.isDeleted, now.updatedByUid, now.revision], [true, ORDER_GUARD_UID, 2], data);
  }
});

test("runs that come late or out of order end the same way", async () => {
  // A Field Engineer approves, then attaches a file, before either run.
  for (const order of [[0, 1], [1, 0]]) {
    await ref().delete();
    const events = [await write(record("fe", "Approved")), await write(record("fe", "Approved", { revision: 2, data: json("Approved", { fileUrl: "x.pdf" }) }))];
    for (const i of order) await guard(events[i]);
    assert.deepStrictEqual(await state(), ["Pending", ORDER_GUARD_UID, false]);
  }

  // An Engineer Admin approves, and a Field Engineer's phone sends the same order again before that run.
  for (const order of [[0, 1], [1, 0]]) {
    await ref().delete();
    await db().doc("orderGuard/vo").delete();
    const events = [await write(record("ea", "Approved")), await write(record("fe", "Approved", { revision: 2 }))];
    for (const i of order) await guard(events[i]);
    assert.deepStrictEqual(await state(), ["Approved", "fe", false]);
    assert.strictEqual(await onRecord(), json("Approved"));
  }

  // An Engineer Admin takes the approval back and a Field Engineer goes on with the order; the run for the approval comes last.
  await ref().delete();
  await db().doc("orderGuard/vo").delete();
  const approval = await write(record("ea", "Approved"));
  const takenBack = await write(record("ea", "Submitted", { revision: 2 }));
  const signed = await write(record("fe", "Signed", { revision: 3 }));
  for (const event of [signed, takenBack, approval]) await guard(event);
  assert.deepStrictEqual(await state(), ["Signed", "fe", false]);
  assert.strictEqual(await onRecord(), null);
  // The same three, all written before the first run, the runs in order.
  await ref().delete();
  await db().doc("orderGuard/vo").delete();
  const late = [await write(record("ea", "Approved")), await write(record("ea", "Submitted", { revision: 2 })), await write(record("fe", "Signed", { revision: 3 }))];
  for (const event of late) await guard(event);
  assert.deepStrictEqual([await state(), (await stored()).revision], [["Signed", "fe", false], 3]);
  assert.strictEqual(await onRecord(), null);

  // An Engineer Admin approves and a Field Engineer changes the order before that run: put back in either order of the runs.
  for (const order of [[0, 1], [1, 0]]) {
    await ref().delete();
    await db().doc("orderGuard/vo").delete();
    const events = [await write(record("ea", "Approved")), await write(record("fe", "Signed", { revision: 2 }))];
    for (const i of order) await guard(events[i]);
    assert.deepStrictEqual(await state(), ["Approved", ORDER_GUARD_UID, false]);
  }

  // The guard put an order back to Pending before the run for the approval that came first: it corrects itself.
  await ref().delete();
  await db().doc("orderGuard/vo").delete();
  const first = await write(record("ea", "Approved"));
  const resent = await write(record("fe", "Approved", { revision: 2 }));
  const again = await write(record("fe", "Approved", { revision: 3, data: json("Approved", { fileUrl: "x.pdf" }) }));
  await guard(again);
  assert.deepStrictEqual(await state(), ["Pending", ORDER_GUARD_UID, false]);
  await guard(first);
  await guard(resent);
  const now = await stored();
  assert.deepStrictEqual([now.data, now.updatedByUid, now.isDeleted], [json("Approved"), ORDER_GUARD_UID, false]);
});

test("an order approved before there was a guard is kept as it stood, whoever wrote it last", async () => {
  await ref().set(record("fe", "Approved"));
  await ref("vo2").set(record("fe", "Approved", { uuid: "vo2" }));
  await ref("new").set(record("fe", "Pending", { uuid: "new" }));
  // The guard's first run is taken to be a moment from now: what is written before that is from before the guard.
  await db().doc("orderGuardStart/start").set({ at: Timestamp.fromMillis(Date.now() + LEGACY_MARGIN_MS + 1500) });
  await new Promise((resolve) => setTimeout(resolve, 2000));

  await guard(await write(record("fe", "Approved", { revision: 2, data: json("Approved").replace("1,000,000", "9,000,000") })));
  assert.deepStrictEqual([(await stored()).data, (await stored()).updatedByUid], [json("Approved"), ORDER_GUARD_UID]);
  // The first run put the other approved order on record too, and only that.
  assert.strictEqual(await onRecord("vo2"), json("Approved"));
  assert.strictEqual((await db().doc("orderGuard/new").get()).exists, false);
  await guard(await write(record("fe", "Signed", { uuid: "vo2", revision: 2 }), false, "vo2"));
  assert.deepStrictEqual(await state("vo2"), ["Approved", ORDER_GUARD_UID, false]);
  // After it, a Field Engineer's approval is theirs.
  await guard(await write(record("fe", "Approved", { uuid: "new", revision: 2 }), false, "new"));
  assert.deepStrictEqual(await state("new"), ["Pending", ORDER_GUARD_UID, false]);
});

test("what a Field Engineer may do is left alone", async () => {
  await guard(await write(record("fe", "Pending")));
  await guard(await write(record("fe", "Notarized", { revision: 2, data: json("Notarized", { remarks: "Signed by the contractor" }) })));
  assert.deepStrictEqual(await state(), ["Notarized", "fe", false]);
  await guard(await tombstone("fe", 3));
  assert.deepStrictEqual(await state(), ["Notarized", "fe", true]);

  // A file of theirs that is no order, approved and then deleted.
  await ref().delete();
  await guard(await write(record("fe", "", { data: JSON.stringify({ documentName: "As-built plan", status: "Approved", remarks: "" }) })));
  await guard(await tombstone("fe", 2));
  assert.deepStrictEqual(await state(), ["Approved", "fe", true]);

  // But a file titled with an order's words is an order to every phone and to the website, so it is one here.
  await ref().delete();
  await guard(await write(record("fe", "", { data: JSON.stringify({ documentName: "Request Letter for Time Extension", status: "Approved", remarks: "" }) })));
  assert.deepStrictEqual(await state(), ["Pending", ORDER_GUARD_UID, false]);

  // The server copying the assigned accounts onto an order a Field Engineer wrote as approved before the guard's run.
  await ref().delete();
  await write(record("fe", "Approved"));
  await guard(await write({ assignedUids: ["fe", "other"] }, true));
  assert.deepStrictEqual(await state(), ["Approved", "fe", false]);
});
