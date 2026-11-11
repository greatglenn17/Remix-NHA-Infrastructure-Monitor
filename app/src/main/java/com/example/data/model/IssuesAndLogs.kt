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
    indices = [Index(value = ["projectId"]), Index(value = ["uuid"])]
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
    val details: String = "",
    // Only entries with a project sync; sign-ins and account changes stay on the phone.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
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
    // Housing billings: what is billed (one of HOUSING_BILLING_TYPES), how many lots or units, and the amount per one.
    val billingType: String = "",
    val units: Int = 0,
    val unitPrice: Double = 0.0,
    // Percent of the gross held back; balances stay on the gross.
    val retentionPct: Double = 0.0,
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/** What is paid after retention. */
val ProjectPayment.netAmount: Double get() = grossAmount * (1 - retentionPct / 100)

/**
 * [payments] with each billing's share and balance after it on the net. A billing saves them from the gross, with
 * the contract cost of that day and every billing before it (in the order added), so the net balance after it is
 * the saved one plus the retention held so far.
 */
fun netBillings(payments: List<ProjectPayment>): List<ProjectPayment> {
    var held = 0.0
    val byId = payments.sortedBy { it.id }.associate { p ->
        held += p.grossAmount - p.netAmount
        val cost = if (p.percentage > 0) p.grossAmount * 100 / p.percentage else 0.0
        p.id to p.copy(
            percentage = p.percentage * (1 - p.retentionPct / 100),
            balanceAmount = p.balanceAmount + held,
            balancePercentage = if (cost > 0) p.balancePercentage + held / cost * 100 else p.balancePercentage
        )
    }
    return payments.map { byId.getValue(it.id) }
}

/** What a housing project bills for; land is billed per lot, M1 and M2 per housing unit. */
val HOUSING_BILLING_TYPES = listOf("Land acquisition", "Land development", "M1 - Housing unit", "M2 - Housing unit")

fun billingUnitName(type: String): String = if (type.startsWith("Land")) "lot" else "housing unit"
