const test = require("node:test");
const assert = require("node:assert");
const { GUARD_UID, FRESH_MS, key, guardAction, isEmptyExtra } = require("./duplicateGuard");

const project = (more = {}) => ({ name: "Bulakan Housing", location: "Bulakan, Bulacan", ...more });
const other = (id, createdMs, more = {}) => ({ id, createdMs, data: project(more) });

test("key ignores case, spacing and invisible characters: whatever ProjectReconciler.key calls one name is one here", () => {
  // Kotlin's trim() drops these at the ends; a name ending in one must not pass for a free name.
  for (const hidden of ["\u00a0", "\u001f", "\u001c", "\ufeff", "\u200b", "\u2003", "\t"]) {
    assert.strictEqual(key(project({ name: `${hidden}Bulakan${hidden} Housing${hidden}` })), key(project()), JSON.stringify(hidden));
  }
  assert.strictEqual(key({ name: " Bulakan  Housing ", location: "bulakan,  Bulacan" }), key(project()));
  assert.notStrictEqual(key(project({ location: "Malolos" })), key(project()));
});

test("a new project under an older project's name is the extra one", () => {
  const act = guardAction({ id: "new", before: null, after: project(), createdMs: 200, others: [other("old", 100), other("older", 50)] });
  assert.deepStrictEqual(act, { kind: "extra", of: "older" });
});

test("the older of two is left alone, whatever creation time its document claims", () => {
  assert.strictEqual(guardAction({ id: "old", before: null, after: project({ createdAt: 999 }), createdMs: 100, others: [other("new", 200, { createdAt: 0 })] }), null);
});

test("a deleted project brought back under a taken name is the extra one, or as the older one is not brought back", () => {
  const before = project({ isDeleted: true });
  assert.deepStrictEqual(guardAction({ id: "b", before, after: project(), createdMs: 200, others: [other("a", 100)] }), { kind: "extra", of: "a" });
  assert.deepStrictEqual(guardAction({ id: "b", before, after: project(), createdMs: 50, others: [other("c", 300), other("a", 100)] }), { kind: "revived", of: "a" });
  assert.deepStrictEqual(guardAction({ id: "b", before: { location: "x" }, after: project(), createdMs: 50, others: [other("a", 100)] }), { kind: "revived", of: "a" });
});

test("a rename into another live project's name is undone", () => {
  const before = project({ name: "Mine", location: "Here" });
  assert.deepStrictEqual(guardAction({ id: "p", before, after: project(), createdMs: 50, others: [other("victim", 100)] }), { kind: "rename", of: "victim", name: "Mine", location: "Here" });
});

test("nothing to do: a free name, a deleted or nameless twin, an edit that keeps a shared name, a deleted project, the guard's own write", () => {
  const base = { id: "p", before: project({ name: "Mine" }), after: project(), createdMs: 50 };
  assert.strictEqual(guardAction({ ...base, others: [other("x", 100, { name: "Else" })] }), null);
  assert.strictEqual(guardAction({ ...base, others: [other("x", 100, { isDeleted: true }), other("y", 100, { name: "" }), { id: "z", createdMs: 1, data: { location: "Bulakan, Bulacan" } }] }), null);
  assert.strictEqual(guardAction({ ...base, before: project({ contractor: "A" }), others: [other("x", 100)] }), null);
  assert.strictEqual(guardAction({ ...base, after: project({ isDeleted: true }), others: [other("x", 100)] }), null);
  assert.strictEqual(guardAction({ ...base, after: project({ updatedByUid: GUARD_UID }), others: [other("x", 100)] }), null);
  assert.strictEqual(guardAction({ ...base, others: [{ id: "p", createdMs: 1, data: project() }] }), null);
});

test("only a project that just reached the cloud with nothing but audit entries is deleted without asking", () => {
  const now = 1_700_000_000_000, audit = { type: "audit_logs" };
  assert.strictEqual(isEmptyExtra({ createdAt: now - 5000 }, now, [audit]), true);
  assert.strictEqual(isEmptyExtra({ createdAt: now - 5000 }, now, []), true);
  assert.strictEqual(isEmptyExtra({ createdAt: now - 5000 }, now, [audit, { type: "weekly_reports", isDeleted: true }]), true);
  assert.strictEqual(isEmptyExtra({ createdAt: now - 5000 }, now, [audit, { type: "weekly_reports" }]), false);
  assert.strictEqual(isEmptyExtra({ createdAt: now - FRESH_MS - 1 }, now, [audit]), false, "made offline a while ago");
  assert.strictEqual(isEmptyExtra({ createdAt: 0 }, now, []), false, "a backdated one is never deleted, only reported");
  assert.strictEqual(isEmptyExtra({}, now, []), false);
  assert.strictEqual(isEmptyExtra({ createdAt: now, barChartJson: "" }, now, []), true);
  assert.strictEqual(isEmptyExtra({ createdAt: now, barChartJson: "[{}]" }, now, []), false, "a bar chart is work");
});
