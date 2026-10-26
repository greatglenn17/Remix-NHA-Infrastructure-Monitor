package com.example.util

import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.ProjectStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Red, amber, grey; declaration order is the rank. */
enum class AttentionLevel { CRITICAL, WARNING, STALE }

enum class AttentionKind { BEHIND, BILLING_AHEAD, WAITING_APPROVAL, DUE_SOON, OVERDUE, NO_RECENT_UPDATE }

data class AttentionFlag(val kind: AttentionKind, val level: AttentionLevel, val message: String)

/** Which signals make a project need the Super Admin's attention (spec: "Needs attention rules"). */
object ProjectAttention {
    const val WARNING_SLIPPAGE = -10.0
    const val CRITICAL_SLIPPAGE = -15.0
    const val BILLING_GAP_POINTS = 10.0
    const val DUE_SOON_DAYS = 30
    const val STALE_DAYS = 14
    val ORDER_TYPES = listOf("Variation Order", "Time Extension", "Work Suspension", "Work Resumption")

    fun flags(
        project: Project,
        documents: List<PendingDocument>,
        billedTotal: Double,
        latestReportAt: LocalDate?,
        today: LocalDate
    ): List<AttentionFlag> {
        val flags = mutableListOf<AttentionFlag>()
        val finished = project.actualAccomplishment >= 100.0

        val slip = slippage(project)
        val markedBehind = project.status == ProjectStatus.BEHIND_SCHEDULE.label
        if (slip <= WARNING_SLIPPAGE || markedBehind) {
            val level = if (slip <= CRITICAL_SLIPPAGE) AttentionLevel.CRITICAL else AttentionLevel.WARNING
            val message = if (slip < 0) "%.1f%% behind schedule".format(-slip) else "Marked behind schedule"
            flags += AttentionFlag(AttentionKind.BEHIND, level, message)
        }

        val cost = ContractCost.current(project.scopeOfWork, project.contractCostOriginal, documents)
        if (cost > 0) {
            val billedPercent = billedTotal / cost * 100
            if (billedPercent - project.actualAccomplishment > BILLING_GAP_POINTS) {
                flags += AttentionFlag(
                    AttentionKind.BILLING_AHEAD, AttentionLevel.WARNING,
                    "Billed %.1f%%, work done %.1f%%".format(billedPercent, project.actualAccomplishment)
                )
            }
        }

        val waiting = documents.count { doc ->
            !doc.status.equals("Approved", ignoreCase = true) &&
                ORDER_TYPES.any { doc.documentName.contains(it, ignoreCase = true) }
        }
        if (waiting > 0) {
            val noun = if (waiting == 1) "order" else "orders"
            flags += AttentionFlag(AttentionKind.WAITING_APPROVAL, AttentionLevel.WARNING, "$waiting $noun waiting approval")
        }

        if (!finished) {
            parse(ProjectSchedule.effectiveCompletion(project.completionDateOriginal, project.completionDateRevised))?.let { due ->
                val days = ChronoUnit.DAYS.between(today, due)
                if (days < 0) {
                    flags += AttentionFlag(AttentionKind.OVERDUE, AttentionLevel.CRITICAL, "Overdue by ${days(-days)}")
                } else if (days <= DUE_SOON_DAYS) {
                    flags += AttentionFlag(AttentionKind.DUE_SOON, AttentionLevel.WARNING, "Due in ${days(days)}")
                }
            }

            val suspended = project.status == ProjectStatus.SUSPENDED.label
            val since = latestReportAt ?: parse(project.dateStarted)
            if (!suspended && since != null) {
                val days = ChronoUnit.DAYS.between(since, today)
                if (days >= STALE_DAYS) {
                    flags += AttentionFlag(AttentionKind.NO_RECENT_UPDATE, AttentionLevel.STALE, "No field update in ${days(days)}")
                }
            }
        }

        return flags.sortedBy { it.level }
    }

    /** Variance rounded to 2 decimals, so -10.0 / -15.0 boundaries are not missed by floating-point error. */
    fun slippage(project: Project): Double = Math.round(project.variance * 100) / 100.0

    private fun days(n: Long) = if (n == 1L) "1 day" else "$n days"

    private fun parse(date: String): LocalDate? = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
}
