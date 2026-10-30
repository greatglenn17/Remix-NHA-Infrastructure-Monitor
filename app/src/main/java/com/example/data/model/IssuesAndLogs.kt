package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "project_issues",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["uuid"])]
)
data class ProjectIssue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val date: String,
    val description: String,
    val actionTaken: String,
    val remarks: String,
    val loggedBy: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Pending",
    val isCritical: Boolean = false,
    // When the issue must be settled by; blank when there is no deadline.
    val targetDate: String = "",
    // Who took the action: one of ISSUE_ACTION_BY, or blank on issues logged before it existed.
    val actionBy: String = "",
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/** The two "Action taken" columns of the monthly report's issues table. */
val ISSUE_ACTION_BY = listOf("Implementing Office", "Contractor/Developer")

/** Still pending after its target date. */
fun ProjectIssue.isOverdue(today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
    status.contains("Pending", ignoreCase = true) &&
        runCatching { java.time.LocalDate.parse(targetDate.trim()) }.getOrNull()?.isBefore(today) == true

@Entity(
    tableName = "audit_logs",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val user: String,
    val device: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
    val actionType: String,
    val oldValue: String = "",
    val newValue: String = "",
    val details: String = ""
)

@Entity(
    tableName = "project_payments",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["uuid"])]
)
data class ProjectPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val dvNo: String = "",
    val date: String,
    val periodCovered: String,
    val grossAmount: Double,
    val percentage: Double,
    val balanceAmount: Double,
    val balancePercentage: Double,
    val fileUrl: String = "",
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)
