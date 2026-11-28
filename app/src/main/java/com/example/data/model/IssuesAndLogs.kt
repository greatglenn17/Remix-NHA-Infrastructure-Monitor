package com.example.data.model

import androidx.room.ColumnInfo
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

/** AddIssueDialog's statuses: an issue is pending until it is resolved. */
val ISSUE_STATUSES = listOf("Pending", "Resolved")

private val SETTLED = Regex("resolved|closed|approved|no action", RegexOption.IGNORE_CASE)

/**
 * Settled: the Issues tab's Resolved filter, and out of the open count. Closed, Approved and No Action, saved before
 * the two statuses, read as Resolved too; the website reads them the same (isResolvedIssue).
 */
val ProjectIssue.isResolved: Boolean get() = SETTLED.containsMatchIn(status)

/** The status an issue shows and its dialog offers: Pending until resolved. */
val ProjectIssue.shownStatus: String get() = if (isResolved) "Resolved" else "Pending"

/** Not resolved after its target date. */
fun ProjectIssue.isOverdue(today: java.time.LocalDate = java.time.LocalDate.now()): Boolean =
    !isResolved &&
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
    // A community facility's deductions from the check (facilityDeductions); they leave the billed share and balances alone.
    // The SQL default lets a billing synced without them (an older website or app) still be stored.
    @ColumnInfo(defaultValue = "0") val recoupmentAmount: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val vatAmount: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val withholdingAmount: Double = 0.0,
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/** What is paid after retention. */
val ProjectPayment.netAmount: Double get() = grossAmount * (1 - retentionPct / 100)

/** The check: the net after retention less recoupment, VAT and withholding (logic.js paidAmount). */
val ProjectPayment.paidAmount: Double get() = netAmount - recoupmentAmount - vatAmount - withholdingAmount

/**
 * A community facility's recoupment (15%, but no more than the advance [left] to recoup), VAT (5% ÷ 1.12) and withholding
 * tax (2% ÷ 1.12) of [gross], to the centavo, as on the website. Without an advance ([left] null) the recoupment is the full 15%.
 */
fun facilityDeductions(gross: Double, left: Double? = null): List<Double> =
    listOf(gross * 0.15, gross * 0.05 / 1.12, gross * 0.02 / 1.12).map { Math.round(it * 100) / 100.0 }
        .let { (r, v, w) -> listOf(minOf(r, left ?: Double.POSITIVE_INFINITY), v, w) }

/**
 * A community facility's advance payment (logic.js ADVANCE_PAYMENT): its first billing, 15% of the original contract, paid
 * in full and paid back through the recoupment of the billings after it. It is not work billed, so it stays out of the
 * billed share and balances (ProjectDao leaves it out of the billings).
 */
const val ADVANCE_PAYMENT = "Advance payment"
val ProjectPayment.isAdvance: Boolean get() = billingType == ADVANCE_PAYMENT
fun advanceAmount(originalCost: Double) = Math.round(originalCost * 15) / 100.0

/** What is still to recoup of the [advances], from every billing in [payments] but [except] (the one being edited); null without an advance. */
fun advanceLeft(advances: List<ProjectPayment>, payments: List<ProjectPayment>, except: ProjectPayment? = null): Double? =
    if (advances.isEmpty()) null
    else maxOf(0.0, Math.round((advances.sumOf { it.grossAmount } - payments.filter { it.uuid != except?.uuid }.sumOf { it.recoupmentAmount }) * 100) / 100.0)

/**
 * [payments] with each billing's share and balance after it on the net. A billing saves them from the gross, with
 * the contract cost of that day and every billing before it (in the order added), so the net balance after it is
 * the saved one plus the retention held so far.
 * Given [cost] (the current contract cost), every share and balance is on it instead, so the rows add up to the total
 * under them even after a variation order changed the cost; the website shows the same. Each share is the step in the
 * running total rounded as it is shown, so the shown shares add up to the shown total. These run in billing-date order,
 * as on the website, so a billing entered late still lands in its own month.
 */
fun netBillings(payments: List<ProjectPayment>, cost: Double = 0.0): List<ProjectPayment> {
    if (cost > 0) {
        fun r2(x: Double) = String.format(java.util.Locale.US, "%.2f", x).toDouble()
        var paid = 0.0
        var shown = 0.0
        val byId = payments.sortedWith(compareBy({ it.date }, { it.uuid })).associate { p ->
            paid += p.netAmount
            val upTo = r2(paid / cost * 100)
            val share = r2(upTo - shown)
            shown = upTo
            p.id to p.copy(percentage = share, balanceAmount = cost - paid, balancePercentage = r2(100 - upTo))
        }
        return payments.map { byId.getValue(it.id) }
    }
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

/** Land development is billed by the amount accomplished; the other housing types by whole lots or units. */
fun billedByAmount(type: String): Boolean = type == "Land development"

/** A billing's lots or units as typed; for one billed by amount, its gross over the amount per lot (2 decimals). */
val ProjectPayment.billedUnits: Double
    get() = if (units != 0) units.toDouble() else if (unitPrice > 0) Math.round(grossAmount / unitPrice * 100) / 100.0 else 0.0

/** A count of lots or units as text: "40", "7.23". */
fun unitsText(count: Double): String = java.math.BigDecimal.valueOf(count).stripTrailingZeros().toPlainString()

fun billingUnitName(type: String): String = if (type.startsWith("Land")) "lot" else "housing unit"
