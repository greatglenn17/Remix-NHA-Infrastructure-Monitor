package com.example.util

import com.example.data.model.ACCOMPLISHMENT_LINES
import com.example.data.model.HOUSING_BILLING_TYPES
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.ProjectAbstract
import com.example.data.model.ProjectPayment
import com.example.data.model.WeeklyReport
import com.example.data.model.accomplishmentLine
import com.example.ui.detail.sections.scopeItems
import com.example.ui.detail.tabs.parseAccomplishmentItemsList
import java.time.LocalDate
import java.util.Locale

/**
 * The Abstract of Physical Accomplishment the contractor attaches before a billing (the district's billing format),
 * printed from an uploaded abstract on the Actual Accomplishment tab, and the rule that what is billed never passes
 * the work done. web/logic.js has the website's copy (latestWeekly, partAmounts, billingAheadErrors, abstractUnits,
 * billingAbstract); change both together.
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
     * of its part's contract amount, and of the contract cost for all parts, against the actual, at 2 decimals: that
     * of the latest of [abstracts] (the Actual Accomplishment tab), or for a project without one the latest weekly
     * report's. Only billings with a housing type count, and only a new or raised one is checked, so a billing
     * already saved can still be corrected.
     */
    fun aheadErrors(
        project: Project, documents: List<PendingDocument>, payments: List<ProjectPayment>, weekly: List<WeeklyReport>,
        new: ProjectPayment, old: ProjectPayment? = null, abstracts: List<ProjectAbstract> = emptyList()
    ): List<String> {
        if (new.billingType.isBlank() || (old != null && old.billingType == new.billingType && round2(new.grossAmount) <= round2(old.grossAmount))) return emptyList()
        val abstract = ProjectAbstracts.latest(abstracts)
        val last = latestWeekly(weekly)
        val actuals = abstract?.let(ProjectAbstracts::lineActuals)
            ?: last?.let { parseAccomplishmentItemsList(it.accomplishmentItemsJson) }.orEmpty().map { it.actualPct }
        val source = abstract?.let { " of the abstract as of ${ProjectSchedule.displayDate(it.asOf)}" } ?: last?.let { " of ${it.reportingWeek}" }.orEmpty()
        val billed = payments.filter { it.billingType.isNotBlank() && it.uuid != old?.uuid } + new
        val line = accomplishmentLine(new.billingType)
        val k = ACCOMPLISHMENT_LINES.indexOf(line)
        return listOf(
            Check(line, billed.filter { accomplishmentLine(it.billingType) == line }.sumOf { it.grossAmount }, partAmounts(project, documents, day(new.date))[k], actuals.getOrNull(k) ?: 0.0),
            Check("Overall", billed.sumOf { it.grossAmount }, ContractCost.current(project.scopeOfWork, project.contractCostOriginal, documents), abstract?.overallPct ?: last?.actualAccomplishmentPct ?: project.actualAccomplishment)
        ).filter { it.of > 0 && round2(it.billed / it.of * 100) > round2(it.actualPct) }.map {
            "${it.name}: billed to date ${"%.2f".format(Locale.US, round2(it.billed / it.of * 100))}% would pass the actual ${"%.2f".format(Locale.US, round2(it.actualPct))}%$source."
        }
    }

    /** What to do about [aheadErrors], after them in the billing form. */
    fun aheadHint(abstracts: List<ProjectAbstract>): String =
        if (abstracts.isEmpty()) "Save the newer weekly report first, or lower this billing." else "Upload the newer abstract first, or lower this billing."

    /** [billed] of the contract amount [of], against the actual accomplishment. */
    private class Check(val name: String, val billed: Double, val of: Double, val actualPct: Double)

    /** The project's number of units as typed ("370 units"), else its saleable lots, else 1 (a lump sum). */
    fun units(project: Project, saleable: Int = 0): Int =
        Regex("\\d+").find(project.numberOfUnits.replace(",", ""))?.value?.toIntOrNull()?.takeIf { it > 0 } ?: saleable.takeIf { it > 0 } ?: 1

    /** A line of the abstract: [previous] is what the saved billings of its type came to, [thisPeriod] this billing as typed. */
    data class Line(val no: String, val name: String, val unit: String, val type: String, val amount: Double, val previous: Double, val thisPeriod: Double)

    /** A part with a contract amount; housing has a [heading] row (item no., name) over its two milestones. */
    data class Part(val name: String, val heading: Pair<String, String>?, val lines: List<Line>)

    /** [targetPct] is the plan on the abstract's "As of" date. */
    data class Abstract(val targetPct: Double, val parts: List<Part>)

    /**
     * This billing as typed on the tab, never saved: the period [covered], the land acquisition [lots], the land
     * [development] amount in pesos, and the Milestone 1 and 2 housing units. A negative figure counts as 0.
     */
    data class Period(val covered: String = "", val lots: Double = 0.0, val development: Double = 0.0, val m1: Double = 0.0, val m2: Double = 0.0)

    /**
     * The billing abstract of the uploaded [abstract]. The amounts are the abstract's own (ProjectAbstracts.lineAmounts),
     * housing as Milestone 1 (80%) and Milestone 2 (20%). Previous is the gross of the saved billings of each type dated
     * on or before its "As of" date; this period is [period], a quantity as quantity x (line amount / [units]).
     */
    fun of(project: Project, documents: List<PendingDocument>, payments: List<ProjectPayment>, abstract: ProjectAbstract, period: Period, units: Int): Abstract {
        val (acquisition, development, housing) = ProjectAbstracts.lineAmounts(abstract)
        val asOf = day(abstract.asOf)
        // A billing's date is yyyy-MM-dd text: compared as days, so one without a date is not counted.
        val before = payments.filter { p -> day(p.date)?.let { asOf != null && !it.isAfter(asOf) } == true }
        fun line(no: String, name: String, unit: String, type: String, amount: Double, thisPeriod: Double) = Line(
            no, name, unit, type, amount,
            previous = round2(before.filter { it.billingType == type }.sumOf { it.grossAmount }),
            thisPeriod = round2(thisPeriod)
        )
        fun counted(no: String, name: String, unit: String, type: String, amount: Double, quantity: Double) =
            line(no, name, unit, type, amount, quantity.coerceAtLeast(0.0) * (amount / units))
        val m1 = round2(housing * 0.8)
        return Abstract(
            targetPct = asOf?.let { BarChart.targetOn(project, documents, it) } ?: project.targetAccomplishment,
            parts = listOf(
                Part("Land Acquisition Cost", null, listOf(counted("1", "LAND ACQUISITION COST", "LOT", HOUSING_BILLING_TYPES[0], acquisition, period.lots))),
                Part("Land Development", null, listOf(line("2", "LAND DEVELOPMENT", "LOT", HOUSING_BILLING_TYPES[1], development, period.development.coerceAtLeast(0.0)))),
                Part("Housing Construction", "3" to "HOUSING CONSTRUCTION", listOf(
                    counted("3.1", "MILESTONE 1 (80%)", "UNIT", HOUSING_BILLING_TYPES[2], m1, period.m1),
                    counted("3.2", "MILESTONE 2 (20%)", "UNIT", HOUSING_BILLING_TYPES[3], round2(housing - m1), period.m2)
                ))
            ).filter { part -> part.lines.any { it.amount > 0 } }
        )
    }
}
