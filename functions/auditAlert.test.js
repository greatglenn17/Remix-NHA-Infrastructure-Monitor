// node --test auditAlert.test.js (no emulator needed)
const test = require("node:test");
const assert = require("node:assert");
const { auditAlert, MAX_AGE_MS } = require("./auditAlert");

const NOW = 1_794_292_093_231;
const entry = (fields, extra = {}) => ({
  type: "audit_logs", isDeleted: false, updatedByUid: "u1",
  data: JSON.stringify({ timestamp: NOW, user: "Engr. Ana", device: "Website", details: "Updated issue from 2026-10-28", ...fields }),
  ...extra,
});

test("an issue edit by a Field Engineer reads like the app's alert", () => {
  assert.deepEqual(auditAlert(entry({ actionType: "Issue Creation" }), "FIELD_ENGINEER", NOW), {
    title: "Project Activity by Field Engineer", message: "Engr. Ana: Updated issue from 2026-10-28", device: "Website",
  });
});

test("titles follow the action, in the app's order", () => {
  const title = (action, role = "ENGINEER_ADMIN") => auditAlert(entry({ actionType: action }), role, NOW).title;
  assert.equal(title("Weekly Report Submitted"), "Report Submitted by Engineer Admin");
  assert.equal(title("Payment Added"), "Disbursement Added by Engineer Admin");
  assert.equal(title("Weather Log"), "Weather Entry by Engineer Admin");
  assert.equal(title("Issue Deleted"), "Site Issue Logged by Engineer Admin");
  assert.equal(title("Project Update"), "Project Activity by Engineer Admin");
  assert.equal(title("Lot Status"), "System Alert (Lot Status) by Engineer Admin");
});

test("no alert for the Super Admin's own action, sign-ins, unknown actors or other records", () => {
  assert.equal(auditAlert(entry({ actionType: "Issue Creation" }), "SUPER_ADMIN", NOW), null);
  assert.equal(auditAlert(entry({ actionType: "User Login" }), "FIELD_ENGINEER", NOW), null);
  assert.equal(auditAlert(entry({ actionType: "Issue Creation" }), "", NOW), null);
  assert.equal(auditAlert(entry({ actionType: "Issue Creation" }, { type: "project_issues" }), "FIELD_ENGINEER", NOW), null);
  assert.equal(auditAlert(entry({ actionType: "Issue Creation" }, { data: "oops" }), "FIELD_ENGINEER", NOW), null);
});

test("an entry uploaded more than a day late stays quiet", () => {
  assert.notEqual(auditAlert(entry({ actionType: "Issue Creation", timestamp: NOW - MAX_AGE_MS }), "FIELD_ENGINEER", NOW), null);
  assert.equal(auditAlert(entry({ actionType: "Issue Creation", timestamp: NOW - MAX_AGE_MS - 1 }), "FIELD_ENGINEER", NOW), null);
});
