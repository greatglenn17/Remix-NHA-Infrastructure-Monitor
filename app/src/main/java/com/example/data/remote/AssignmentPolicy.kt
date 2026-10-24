package com.example.data.remote

import com.example.data.model.AssignedUids

/**
 * Who syncs which project. Only the Super Admin sees every project and sets
 * `assignedUids`; everyone else syncs the projects assigned to their account, and a
 * Viewer never writes.
 */
object AssignmentPolicy {
    const val SUPER_ADMIN = "SUPER_ADMIN"
    const val VIEWER = "VIEWER"

    fun isSuperAdmin(role: String?): Boolean = role == SUPER_ADMIN

    /**
     * Whether a local project missing from the cloud should be removed from this phone
     * only (never tombstoned): the user is not on its list any more and nothing on this
     * phone still has to reach the cloud. A project whose list still names the user is
     * kept, since it will arrive with a later snapshot.
     */
    fun shouldRemoveLocally(
        isSuperAdmin: Boolean,
        myUid: String,
        localAssignedUids: String,
        inServerSnapshot: Boolean,
        hasPendingUpload: Boolean,
        createdByMe: Boolean
    ): Boolean =
        !isSuperAdmin && !inServerSnapshot && !hasPendingUpload && !createdByMe &&
            !AssignedUids.contains(localAssignedUids, myUid)

    /** A missing role claim counts as a writer, as in the cloud rules. */
    fun canWrite(role: String?): Boolean = role != VIEWER

    /**
     * The `assignedUids` to send with a project upload, or null to leave the field out.
     * The Super Admin sends the local list unless this phone never received one; others
     * never change it, and name only themselves when they create the document.
     */
    fun projectUploadFields(isSuperAdmin: Boolean, exists: Boolean, myUid: String, localAssignedUids: String): List<String>? =
        when {
            isSuperAdmin -> if (localAssignedUids.isBlank()) null else AssignedUids.parse(localAssignedUids)
            exists -> null
            else -> listOf(myUid)
        }
}
