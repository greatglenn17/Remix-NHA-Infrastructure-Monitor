/**
 * Whether an account may add and edit data (the `approved` claim).
 * - The Super Admin always is.
 * - Otherwise the profile's `approved` flag decides once it is set (only the Super Admin can set it).
 * - A profile without the flag belongs to an account from before approvals existed when the account
 *   already holds a role claim and no `approved` claim: it stays approved. A brand-new account has
 *   no claims yet, so it starts unapproved.
 */
function isApproved(profile, currentClaims, isSuperAdmin) {
  if (isSuperAdmin) return true;
  if (typeof profile.approved === "boolean") return profile.approved;
  const claims = currentClaims || {};
  return typeof claims.role === "string" && !("approved" in claims);
}

module.exports = { isApproved };
