// node --test auditAlert.test.js (no emulator needed)
const test = require("node:test");
const assert = require("node:assert");
const { auditAlert, skipReason, MAX_AGE_MS } = require("./auditAlert");

const NOW = 1_794_292_093_231;
const ONGOING = { name: "Sample Resettlement", actualAccomplishment: 41.25 };
const entry = (fields, extra = {}) => ({
  type: "audit_logs", isDeleted: false, updatedByUid: "u1",
  data: JSON.stringify({ timestamp: NOW, user: "Engr. Ana", device: "Website", details: "Updated issue from 2026-10-28", ...fields }),
  ...extra,
});

test("an issue edit by a Field Engineer reads like the app's alert", () => {
  assert.deepEqual(auditAlert(entry({ actionType: "Issue Creation" }), "FIELD_ENGINEER", NOW, ONGOING), {
    title: "Project Activity by Field Engineer", message: "Engr. Ana: Updated issue from 2026-10-28 (Sample Resettlement)", device: "Website",
  });
});

test("only on-going projects alert: not finished (100%), deleted or missing", () => {
  const alert = project => auditAlert(entry({ actionType: "Issue Creation" }), "FIELD_ENGINEER", NOW, project);
  assert.notEqual(alert({ actualAccomplishment: 99.99, status: "Behind Schedule" }), null);
  assert.notEqual(alert({ status: "Suspended" }), null);
  assert.equal(alert({ ...ONGOING, actualAccomplishment: 100 }), null);
  assert.equal(alert({ ...ONGOING, isDeleted: true }), null);
  assert.equal(alert(null), null);
});

test("titles follow the action, in the app's order", () => {
  const title = (action, role = "ENGINEER_ADMIN") => auditAlert(entry({ actionType: action }), role, NOW, ONGOING).title;
  assert.equal(title("Weekly Report Submitted"), "Report Submitted by Engineer Admin");
  assert.equal(title("Payment Added"), "Disbursement Added by Engineer Admin");
  assert.equal(title("Weather Log"), "Weather Entry by Engineer Admin");
  assert.equal(title("Issue Deleted"), "Site Issue Logged by Engineer Admin");
  assert.equal(title("Project Update"), "Project Activity by Engineer Admin");
  assert.equal(title("Lot Status"), "System Alert (Lot Status) by Engineer Admin");
});

test("no alert for the Super Admin's own action, sign-ins, unknown actors or other records", () => {
  assert.equal(auditAlert(entry({ actionType: "Issue Creation" }), "SUPER_ADMIN", NOW, ONGOING), null);
  assert.equal(auditAlert(entry({ actionType: "User Login" }), "FIELD_ENGINEER", NOW, ONGOING), null);
  assert.equal(auditAlert(entry({ actionType: "Issue Creation" }), "", NOW, ONGOING), null);
  assert.equal(auditAlert(entry({ actionType: "Issue Creation" }, { type: "project_issues" }), "FIELD_ENGINEER", NOW, ONGOING), null);
  assert.equal(auditAlert(entry({ actionType: "Issue Creation" }, { data: "oops" }), "FIELD_ENGINEER", NOW, ONGOING), null);
});

test("the log says why an entry sent nothing", () => {
  const why = (role, project, fields = {}) => skipReason(entry({ actionType: "Issue Creation", ...fields }), role, NOW, project);
  assert.equal(why("FIELD_ENGINEER", ONGOING), null);
  assert.equal(why("FIELD_ENGINEER", null), "project not found");
  assert.equal(why("FIELD_ENGINEER", { actualAccomplishment: 100 }), "project finished");
  assert.equal(why("", ONGOING), "editor has no role in users");
  assert.equal(why("SUPER_ADMIN", ONGOING), "the Super Admin's own change");
  assert.equal(why("FIELD_ENGINEER", ONGOING, { timestamp: NOW - MAX_AGE_MS - 1 }), "entry more than a day old");
});

test("an entry uploaded more than a day late stays quiet", () => {
  assert.notEqual(auditAlert(entry({ actionType: "Issue Creation", timestamp: NOW - MAX_AGE_MS }), "FIELD_ENGINEER", NOW, ONGOING), null);
  assert.equal(auditAlert(entry({ actionType: "Issue Creation", timestamp: NOW - MAX_AGE_MS - 1 }), "FIELD_ENGINEER", NOW, ONGOING), null);
});
