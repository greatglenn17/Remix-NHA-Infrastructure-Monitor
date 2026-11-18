// Every record carries a copy of its project's assignedUids, and the rules let an account other than the Super Admin
// read a record only when that copy names it. syncProjectAssignmentToRecords copies the list when the project's
// changes; this keeps a record written afterwards with an older copy (a phone whose project is behind) from hiding
// it from the people now assigned.

/** An assignedUids value as a sorted JSON string for comparison; a missing or non-array value counts as []. */
function assignedKey(uids) {
  return JSON.stringify(Array.isArray(uids) ? [...uids].sort() : []);
}

/**
 * The list `record` must carry: its project's, when the record's copy differs from it; null for nothing to do,
 * including while the project has no list (a missing field never becomes []).
 */
function assignmentFix(record, project) {
  if (!record || !project || !Array.isArray(project.assignedUids)) return null;
  return assignedKey(record.assignedUids) === assignedKey(project.assignedUids) ? null : project.assignedUids;
}

module.exports = { assignedKey, assignmentFix };
