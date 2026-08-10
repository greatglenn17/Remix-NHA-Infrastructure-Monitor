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
    MANAGE_SDP_PLANS
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
            Permission.MANAGE_SDP_PLANS -> false
            else -> true
        }
        UserRole.VIEWER -> false
    }
}

data class UserAccount(
    val id: String,
    val name: String,
    val title: String,
    val role: UserRole,
    val office: String = "National Housing Authority",
    val assignedProjectId: Long? = null,
    val isDemoAccount: Boolean = false
)

val DefaultUserAccount = UserAccount(
    id = "nha_officer",
    name = "Engr. Glenn C. Aprovechado",
    title = "Principal Engineer C",
    office = "Bulacan District Office",
    role = UserRole.SUPER_ADMIN,
    isDemoAccount = false
)

val SampleUsers = emptyList<UserAccount>()

