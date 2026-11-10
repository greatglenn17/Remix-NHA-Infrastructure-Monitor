// The Super Admin's alert for a new audit log entry, from the app's ProjectViewModel.logAuditAction.
const ROLE_LABELS = {
  SUPER_ADMIN: "Super Admin", ENGINEER_ADMIN: "Engineer Admin", FIELD_ENGINEER: "Field Engineer",
  VIEWER: "Viewer", SUPER_VIEWER: "Super Viewer",
};
// A phone that was offline uploads its entries late; older ones than this are history, not news.
const MAX_AGE_MS = 24 * 60 * 60 * 1000;

/** Project.isCompleted is work done at 100%; until then the project is On-going, Behind Schedule or Suspended. */
const isOngoing = project => !!project && project.isDeleted !== true && !((Number(project.actualAccomplishment) || 0) >= 100);

/**
 * The alert for one cloud record, or null when none goes out: not a new audit entry, a project that is finished,
 * deleted or missing, the Super Admin's own action, a sign-in or sign-out, an unknown actor, or an entry more than a
 * day old. `actorRole` is the editor's users/{uid} role; `project` is projects/{projectUuid}'s data.
 */
function auditAlert(record, actorRole, now, project) {
  if (!record || record.type !== "audit_logs" || record.isDeleted === true) return null;
  if (!isOngoing(project)) return null;
  if (!actorRole || actorRole === "SUPER_ADMIN") return null;
  let d;
  try { d = JSON.parse(record.data || "{}"); } catch { return null; }
  const action = String(d.actionType || "");
  if (action === "User Login" || action === "User Logout") return null;
  if (typeof d.timestamp === "number" && now - d.timestamp > MAX_AGE_MS) return null;
  const by = ROLE_LABELS[actorRole] || actorRole;
  const has = word => action.toLowerCase().includes(word);
  const title =
    has("registration") ? "New User Registration Alert"
      : has("elevation") ? "Role Elevation Alert"
        : has("creation") || has("project") ? `Project Activity by ${by}`
          : has("report") ? `Report Submitted by ${by}`
            : has("payment") ? `Disbursement Added by ${by}`
              : has("weather") ? `Weather Entry by ${by}`
                : has("issue") ? `Site Issue Logged by ${by}`
                  : `System Alert (${action}) by ${by}`;
  const name = project.name ? ` (${project.name})` : "";
  return { title, message: `${d.user || "Someone"}: ${d.details || action}${name}`, device: String(d.device || "") };
}

module.exports = { auditAlert, MAX_AGE_MS };
