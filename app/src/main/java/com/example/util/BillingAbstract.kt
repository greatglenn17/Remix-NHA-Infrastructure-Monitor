package com.example.util

import com.example.data.model.ACCOMPLISHMENT_LINES
import com.example.data.model.HOUSING_BILLING_TYPES
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.ProjectPayment
import com.example.data.model.WeeklyReport
import com.example.data.model.accomplishmentLine
import com.example.ui.detail.sections.scopeItems
import com.example.ui.detail.tabs.parseAccomplishmentItemsList
import java.time.LocalDate
import java.util.Locale

/**
 * A billing's Abstract of Physical Accomplishment (the district's billing format, by period covered), made from the
 * billings, and the rule that what is billed never passes the work done. web/logic.js has the website's copy
 * (latestWeekly, partAmounts, billingAheadErrors, abstractUnits, billingAbstract); change both together.
 */
object BillingAbstract {
    private fun round2(v: Double) = Math.round(v * 100) / 100.0
    private fun day(text: String) = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    /** The report of the latest week, the one the project's target and actual follow; null with none. */
    fun latestWeekly(weekly: List<WeeklyReport>): WeeklyReport? =
        weekly.maxWithOrNull(compareBy { BlueBookForms.weekStart(it.reportingWeek) ?: LocalDate.MIN })

    /** A new report's Actual % starts from the latest report's, by line, so a week without a new abstract keeps the last actual. */
    fun carriedActuals(weekly: List<WeeklyReport>): Map<String, Double> =
        latestWeekly(weekly)?.let { parseAccomplishmentItemsList(it.accomplishmentItemsJson) }.orEmpty().associate { it.itemDescription to it.actualPct }

    /**
     * The contract amount of each accomplishment line on [asOf], from the bar chart in force; without one from the
     * Scope of Works (an item without an amount takes its weight of the current cost).
     */
    fun partAmounts(project: Project, documents: List<PendingDocument>, asOf: LocalDate?): List<Double> {
        val version = asOf?.let { BarChart.planOn(project, documents, it)?.version }?.takeIf { it.total > 0 }
        val cost = ContractCost.current(project.scopeOfWork, project.contractCostOriginal, documents)
        val items = version?.items?.map { it.name to it.amount }
            ?: if (project.scopeOfWork.isBlank()) emptyList()
            else scopeItems(project.scopeOfWork).map { (name, amount, weight) -> name to if (amount > 0) amount else weight * cost / 100 }
        return ACCOMPLISHMENT_LINES.map { line -> round2(items.filter { accomplishmentLine(it.first) == line }.sumOf { it.second }) }
    }

    /**
     * What is billed never passes the work done. With [new] saved (in place of [old] when editing), the billed share
     * of its part's contract amount, and of the contract cost for all parts, against the actual of the latest weekly
     * report, at 2 decimals. Only billings with a housing type count, and only a new or raised one is checked, so a
     * billing already saved can still be corrected.
     */
    fun aheadErrors(
        project: Project, documents: List<PendingDocument>, payments: List<ProjectPayment>, weekly: List<WeeklyReport>,
        new: ProjectPayment, old: ProjectPayment? = null
    ): List<String> {
        if (new.billingType.isBlank() || (old != null && old.billingType == new.billingType && round2(new.grossAmount) <= round2(old.grossAmount))) return emptyList()
        val last = latestWeekly(weekly)
        val actuals = last?.let { parseAccomplishmentItemsList(it.accomplishmentItemsJson) }.orEmpty()
        val billed = payments.filter { it.billingType.isNotBlank() && it.uuid != old?.uuid } + new
        val line = accomplishmentLine(new.billingType)
        val k = ACCOMPLISHMENT_LINES.indexOf(line)
        return listOf(
            Check(line, billed.filter { accomplishmentLine(it.billingType) == line }.sumOf { it.grossAmount }, partAmounts(project, documents, day(new.date))[k], actuals.getOrNull(k)?.actualPct ?: 0.0),
            Check("Overall", billed.sumOf { it.grossAmount }, ContractCost.current(project.scopeOfWork, project.contractCostOriginal, documents), last?.actualAccomplishmentPct ?: project.actualAccomplishment)
        ).filter { it.of > 0 && round2(it.billed / it.of * 100) > round2(it.actualPct) }.map {
            "${it.name}: billed to date ${"%.2f".format(Locale.US, round2(it.billed / it.of * 100))}% would pass the actual " +
                "${"%.2f".format(Locale.US, round2(it.actualPct))}%${last?.let { r -> " of ${r.reportingWeek}" }.orEmpty()}."
        }
    }

    /** [billed] of the contract amount [of], against the actual accomplishment. */
    private class Check(val name: String, val billed: Double, val of: Double, val actualPct: Double)

    /** The project's number of units as typed ("370 units"), else its saleable lots, else 1 (a lump sum). */
    fun units(project: Project, saleable: Int = 0): Int =
        Regex("\\d+").find(project.numberOfUnits.replace(",", ""))?.value?.toIntOrNull()?.takeIf { it > 0 } ?: saleable.takeIf { it > 0 } ?: 1

    /** A line of the abstract: [previous] is what the billings of its type before this one came to, [thisPeriod] this billing. */
    data class Line(val no: String, val name: String, val unit: String, val type: String, val amount: Double, val previous: Double, val thisPeriod: Double)

    /** A part with a contract amount; housing has a [heading] row (item no., name) over its two milestones. */
    data class Part(val name: String, val heading: Pair<String, String>?, val lines: List<Line>)

    /** [targetPct] is the plan on the billing's date. */
    data class Abstract(val targetPct: Double, val parts: List<Part>)

    /**
     * The abstract for [payment]. Earlier billings are those dated before it; on the same date by uuid, which the
     * website holds too and an edit does not change.
     */
    fun of(project: Project, documents: List<PendingDocument>, payments: List<ProjectPayment>, payment: ProjectPayment): Abstract {
        val (acquisition, development, housing) = partAmounts(project, documents, day(payment.date))
        val ordered = payments.sortedWith(compareBy({ it.date }, { it.uuid }))
        val before = ordered.take(ordered.indexOfFirst { it.uuid == payment.uuid }.coerceAtLeast(0))
        fun line(no: String, name: String, unit: String, type: String, amount: Double) = Line(
            no, name, unit, type, amount,
            previous = round2(before.filter { it.billingType == type }.sumOf { it.grossAmount }),
            thisPeriod = if (payment.billingType == type) payment.grossAmount else 0.0
        )
        val m1 = round2(housing * 0.8)
        return Abstract(
            targetPct = day(payment.date)?.let { BarChart.targetOn(project, documents, it) } ?: project.targetAccomplishment,
            parts = listOf(
                Part("Land Acquisition Cost", null, listOf(line("1", "LAND ACQUISITION COST", "LOT", HOUSING_BILLING_TYPES[0], acquisition))),
                Part("Land Development", null, listOf(line("2", "LAND DEVELOPMENT", "LOT", HOUSING_BILLING_TYPES[1], development))),
                Part("Housing Construction", "3" to "HOUSING CONSTRUCTION", listOf(
                    line("3.1", "MILESTONE 1 (80%)", "UNIT", HOUSING_BILLING_TYPES[2], m1),
                    line("3.2", "MILESTONE 2 (20%)", "UNIT", HOUSING_BILLING_TYPES[3], round2(housing - m1))
                ))
            ).filter { part -> part.lines.any { it.amount > 0 } }
        )
    }
}
