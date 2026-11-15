// What the server does about two live projects with one name and location. The phones match a project that is not in
// the cloud yet by its name (ProjectReconciler), so which of two same-name documents is the older one must not be
// something a client can say: it is decided here, from the server's own creation times.

/** updatedByUid of the guard's own writes, which it then leaves alone. */
const GUARD_UID = "duplicate-guard";

/** A project made on the phone or the website less than this long before it reached the cloud holds little work yet. */
const FRESH_MS = 10 * 60 * 1000;

// Every space, control and invisible character counts as a space here. The phones' key (ProjectReconciler.key) trims
// and collapses fewer of them, and not the same ones on Android as in its tests, so two names equal there must be
// equal here; two names that differ only by such a character are one name here, which costs nothing.
const normalized = (text) => String(text ?? "").toLowerCase().replace(/[\s\p{Z}\p{C}]+/gu, " ").trim();

/** The name and location without case and spacing: never finer than ProjectReconciler.key. */
const key = (project) => `${normalized(project.name)}|${normalized(project.location)}`;

/** What the phones and the website show: not marked deleted, and with a name. */
const isLive = (project) => !!project && project.isDeleted !== true && typeof project.name === "string" && project.name !== "";

/**
 * What to do about one write of a project (`before` is null for a new document), given the other projects
 * (`others`: [{ id, data, createdMs }], createdMs the server's creation time), or null for nothing. `of` is the oldest
 * other live project with the same name and location:
 * - { kind: "extra", of }: the project just became live under the name of the older project `of`;
 * - { kind: "revived", of }: a deleted project was brought back under a name that only newer projects now have. As
 *   the oldest it would pass for the original on the phones, so it is not for the emptiness test of an extra;
 * - { kind: "rename", of, name, location }: it was renamed into `of`'s name, and these are the old ones.
 * A project that already shared its name before this write is left alone, and so are the guard's own writes (the
 * rules let no client sign a write as the guard).
 */
function guardAction({ id, before, after, createdMs, others }) {
  if (!isLive(after) || after.updatedByUid === GUARD_UID) return null;
  const same = others
    .filter((other) => other.id !== id && isLive(other.data) && key(other.data) === key(after))
    .sort((a, b) => a.createdMs - b.createdMs || (a.id < b.id ? -1 : 1));
  if (same.length === 0) return null;
  const of = same[0].id;
  if (isLive(before)) return key(before) === key(after) ? null : { kind: "rename", of, name: before.name, location: before.location ?? "" };
  if (same[0].createdMs < createdMs || (same[0].createdMs === createdMs && of < id)) return { kind: "extra", of };
  return before ? { kind: "revived", of } : null;
}

/**
 * Whether an extra project may be deleted without asking: it reached the cloud within FRESH_MS of being made
 * (`createdAt` is the client's own time, so one made offline days ago, or claiming to be, is never deleted), and it
 * has no bar chart and no record but its audit entries (`records`: [{ type, isDeleted }]).
 */
function isEmptyExtra(project, createdMs, records) {
  const made = Number(project.createdAt);
  return Number.isFinite(made) && Math.abs(createdMs - made) < FRESH_MS && !project.barChartJson
    && records.every((record) => record.type === "audit_logs" || record.isDeleted === true);
}

module.exports = { GUARD_UID, FRESH_MS, key, isLive, guardAction, isEmptyExtra };
