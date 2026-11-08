package com.example.data.model

enum class UserRole(val label: String, val description: String) {
    SUPER_ADMIN(
        label = "Super Admin",
        description = "Full Administrative Access: System config, user role management, backup/restore, audit log purging, and full project control."
    ),
    ENGINEER_ADMIN(
        label = "Engineer Admin",
        description = "Full Project Management: Create/edit/delete projects, approve/finalize reports, manage variation orders, time extensions & payments."
    ),
    FIELD_ENGINEER(
        label = "Field Engineer",
        description = "Field Operations Access: Submit and edit weekly/monthly reports, payments, weather logs, site issues, and upload documents."
    ),
    VIEWER(
        label = "Viewer",
        description = "Read-Only Access: View project dashboards, reports, weather logs, issues, and payments."
    ),
    // Heads and management: every project, read-only. Only the Super Admin gives it.
    SUPER_VIEWER(
        label = "Super Viewer",
        description = "View every project, read-only: dashboards, reports, documents, photos and payments."
    )
}

enum class Permission {
    CREATE_PROJECT,
    EDIT_PROJECT,
    DELETE_PROJECT,
    SUBMIT_REPORT,
    EDIT_REPORT,
    DELETE_REPORT,
    LOG_ISSUE,
    EDIT_ISSUE,
    DELETE_ISSUE,
    MANAGE_PAYMENTS,
    UPLOAD_DOCUMENT,
    SUBMIT_WEATHER,
    BACKUP_RESTORE,
    MANAGE_USERS,
    VIEW_AUDIT_LOGS,
    MANAGE_SDP_PLANS,
    // Approve or reject time extensions, suspensions, resumptions and variation orders.
    APPROVE_ORDER,
    // Enter or revise the approved bar chart the target % follows.
    MANAGE_BAR_CHART
}

fun UserRole.hasPermission(permission: Permission): Boolean {
    return when (this) {
        UserRole.SUPER_ADMIN -> true
        UserRole.ENGINEER_ADMIN -> permission != Permission.MANAGE_USERS
        UserRole.FIELD_ENGINEER -> when (permission) {
            Permission.CREATE_PROJECT,
            Permission.DELETE_PROJECT,
            Permission.DELETE_REPORT,
            Permission.DELETE_ISSUE,
            Permission.BACKUP_RESTORE,
            Permission.MANAGE_USERS,
            Permission.MANAGE_SDP_PLANS,
            Permission.APPROVE_ORDER,
            Permission.MANAGE_BAR_CHART -> false
            else -> true
        }
        UserRole.VIEWER, UserRole.SUPER_VIEWER -> false
    }
}

data class UserAccount(
    val id: String,
    val name: String,
    val title: String,
    val role: UserRole,
    val office: String = "National Housing Authority",
    val assignedProjectId: Long? = null,
    val isDemoAccount: Boolean = false,
    // The Super Admin's "See all projects" switch: other projects open view-only.
    val seesAllProjects: Boolean = false,
    // A new account the Super Admin has not approved yet: it works as a Viewer until then.
    val awaitingApproval: Boolean = false
)

/** Opens every project: the Super Admin and Super Viewer always, anyone else when the Super Admin allowed it. */
val UserAccount.seesAll: Boolean
    get() = role == UserRole.SUPER_ADMIN || role == UserRole.SUPER_VIEWER || seesAllProjects

/** This account on [project]: a project not assigned to it shows as for a Viewer. */
fun UserAccount.forProject(project: Project?, myUid: String?): UserAccount =
    if (project == null || com.example.data.remote.AssignmentPolicy.isVisible(role == UserRole.SUPER_ADMIN, myUid, project.assignedUids, project.createdByUid)) this
    else copy(role = UserRole.VIEWER)

val DefaultUserAccount = UserAccount(
    id = "nha_officer",
    name = "Engr. Glenn C. Aprovechado",
    title = "Principal Engineer C",
    office = "Bulacan District Office",
    role = UserRole.SUPER_ADMIN,
    isDemoAccount = false
)

val SampleUsers = emptyList<UserAccount>()

