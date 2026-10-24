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

    /**
     * A cloud document's `assignedUids` as stored locally (a JSON array, possibly "[]"),
     * or null when the document has no list yet; null never removes or hides anything.
     */
    fun storedList(hasField: Boolean, raw: Any?): String? {
        if (!hasField || raw !is List<*>) return null
        return AssignedUids.toJson(raw.filterIsInstance<String>())
    }

    /**
     * The `assignedUids` a record upload carries, copied from its local project, or null
     * while the project's list is unknown on this phone (""); the record then waits.
     * An explicit "[]" is sent.
     */
    fun recordUploadList(localAssignedUids: String): List<String>? =
        if (localAssignedUids.isBlank()) null else AssignedUids.parse(localAssignedUids)

    /** Dashboard filter: the Super Admin sees every project, others only projects naming their uid. */
    fun isVisible(isSuperAdmin: Boolean, myUid: String?, assignedUids: String): Boolean =
        isSuperAdmin || (myUid != null && AssignedUids.contains(assignedUids, myUid))

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

    /**
     * The project to save from the edit dialog. With [users] (the Super Admin changed
     * the assignment) the list and names come from them; otherwise the assignment is
     * taken from the [current] row, never from the dialog's older copy, so an edit
     * can't overwrite a newer assignment or clear a list this phone hasn't seen.
     */
    fun mergeAssignment(edited: com.example.data.model.Project, current: com.example.data.model.Project?, users: List<com.example.data.model.RegisteredUser>?): com.example.data.model.Project =
        when {
            users != null -> edited.copy(
                assignedUids = AssignedUids.toJson(users.map { it.uid }),
                assignedStaff = users.joinToString(", ") { it.name }
            )
            current != null -> edited.copy(assignedUids = current.assignedUids, assignedStaff = current.assignedStaff)
            else -> edited
        }
}
