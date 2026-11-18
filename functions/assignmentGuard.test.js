const test = require("node:test");
const assert = require("node:assert");
const { assignedKey, assignmentFix } = require("./assignmentGuard");

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
