const test = require("node:test");
const assert = require("node:assert");
const { assignedKey, assignmentFix, sameButAssignment } = require("./assignmentGuard");

test("assignedKey ignores order and treats a missing list as empty", () => {
  assert.strictEqual(assignedKey(["b", "a"]), assignedKey(["a", "b"]));
  assert.strictEqual(assignedKey(undefined), assignedKey([]));
  assert.strictEqual(assignedKey("a"), "[]");
});

test("a record with an older copy of the list takes its project's", () => {
  const project = { assignedUids: ["fe", "ea"] };
  assert.deepStrictEqual(assignmentFix({ assignedUids: ["fe"] }, project), ["fe", "ea"]);
  assert.deepStrictEqual(assignmentFix({}, project), ["fe", "ea"]);
  assert.deepStrictEqual(assignmentFix({ assignedUids: ["fe", "ea", "gone"] }, project), ["fe", "ea"]);
  assert.deepStrictEqual(assignmentFix({ assignedUids: ["fe"] }, { assignedUids: [] }), []);
});

test("nothing to do: the same list in any order, a project with no list, no project", () => {
  assert.strictEqual(assignmentFix({ assignedUids: ["ea", "fe"] }, { assignedUids: ["fe", "ea"] }), null);
  assert.strictEqual(assignmentFix({ assignedUids: ["fe"] }, {}), null);
  assert.strictEqual(assignmentFix({ assignedUids: ["fe"] }, null), null);
  assert.strictEqual(assignmentFix({}, { assignedUids: [] }), null);
});

test("sameButAssignment: only a different list, in any field order, is the same state", () => {
  const r = { uuid: "r", revision: 4, updatedAt: 40, data: "{}", isDeleted: false, assignedUids: ["fe"] };
  assert.ok(sameButAssignment(r, { assignedUids: ["fe", "ea"], isDeleted: false, data: "{}", updatedAt: 40, revision: 4, uuid: "r" }));
  assert.ok(sameButAssignment(r, { uuid: "r", revision: 4, updatedAt: 40, data: "{}", isDeleted: false }));
  assert.ok(!sameButAssignment(r, { ...r, revision: 5 }));
  assert.ok(!sameButAssignment(r, { ...r, data: "{\"status\":\"Approved\"}" }));
  assert.ok(!sameButAssignment(r, { ...r, isDeleted: true }));
});
