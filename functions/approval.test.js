// node --test approval.test.js (no emulator needed)
const test = require("node:test");
const assert = require("node:assert");
const { isApproved } = require("./approval");

test("super admin is always approved", () => {
  assert.equal(isApproved({ approved: false }, {}, true), true);
});

test("the profile flag decides once set", () => {
  assert.equal(isApproved({ approved: true }, {}, false), true);
  assert.equal(isApproved({ approved: false }, { role: "FIELD_ENGINEER", approved: true }, false), false);
});

test("an account from before approvals keeps working", () => {
  assert.equal(isApproved({}, { role: "FIELD_ENGINEER", allProjects: false }, false), true);
});

test("a brand-new account starts unapproved", () => {
  assert.equal(isApproved({ role: "FIELD_ENGINEER" }, undefined, false), false);
  assert.equal(isApproved({}, {}, false), false);
});

test("an account already marked unapproved stays so without the flag", () => {
  assert.equal(isApproved({}, { role: "FIELD_ENGINEER", approved: false }, false), false);
});
