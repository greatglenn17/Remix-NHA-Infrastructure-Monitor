package com.example.util

import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.ProjectStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The contractor's approved bar chart with cash flow: each item's amount is spread evenly over the
 * days of its bars, which is how the office's Excel builds the monthly cash flow and S-curve.
 * Days are contract days: day 1 is the start date, a month is 30 days, a chart cell is 5 days.
 */
object BarChart {
    const val CELL_DAYS = 5
    const val MONTH_DAYS = 30
    const val CELLS_PER_MONTH = MONTH_DAYS / CELL_DAYS

    /** Contract days [startDay] to [endDay], both included. */
    data class Bar(val startDay: Int, val endDay: Int) {
        val days: Int get() = (endDay - startDay + 1).coerceAtLeast(0)
    }

    /** [marks] is the website's coloured days of the item, kept as its JSON so a save from the phone does not lose them. */
    data class Item(val name: String, val amount: Double, val bars: List<Bar>, val marks: String = "") {
        val days: Int get() = bars.sumOf { it.days }

        /** Share of the item planned done by the end of contract [day], 0 to 1. */
        fun plannedShare(day: Int): Double =
            if (days <= 0) 0.0 else bars.sumOf { (minOf(day, it.endDay) - it.startDay + 1).coerceIn(0, it.days) }.toDouble() / days
    }

    /**
     * One approved chart; a revised chart applies from its [effective] date (yyyy-MM-dd). [colors] is the chart's own
     * colours, named and drawn on the website's Bar chart page, kept as their JSON: the phone does not draw them.
     */
    data class Version(val label: String, val effective: String, val items: List<Item>, val colors: String = "") {
        val total: Double get() = items.sumOf { it.amount }
        val lastDay: Int get() = items.flatMap { it.bars }.maxOfOrNull { it.endDay } ?: 0
        fun plannedAmount(day: Int): Double = items.sumOf { it.amount * it.plannedShare(day) }
        fun plannedPct(day: Int): Double = if (total <= 0) 0.0 else plannedAmount(day) / total * 100
    }

    /** One column of the chart's footer: monthly and cumulative accomplishment (%) and cash flow (PHP). */
    data class Month(val no: Int, val monthlyPct: Double, val cumulativePct: Double, val monthlyCash: Double, val cumulativeCash: Double)

    /** The footer rows, Month 1 to the month holding the last bar. */
    fun months(version: Version): List<Month> {
        val count = (version.lastDay + MONTH_DAYS - 1) / MONTH_DAYS
        var previous = 0.0
        return (1..count).map { m ->
            val cash = version.plannedAmount(m * MONTH_DAYS)
            val monthly = cash - previous
            previous = cash
            val pct = { amount: Double -> if (version.total <= 0) 0.0 else amount / version.total * 100 }
            Month(m, pct(monthly), pct(cash), monthly, cash)
        }
    }

    /** Contract day of the chart cell labelled [cell] (5, 10 … 30) in [month]: its first and last day. */
    fun cellStart(month: Int, cell: Int): Int = (month - 1) * MONTH_DAYS + cell - CELL_DAYS + 1
    fun cellEnd(month: Int, cell: Int): Int = (month - 1) * MONTH_DAYS + cell

    /** Month and cell label (5 … 30) holding contract [day]. */
    fun cellOf(day: Int): Pair<Int, Int> {
        val d = day.coerceAtLeast(1) - 1
        return (d / MONTH_DAYS + 1) to ((d % MONTH_DAYS) / CELL_DAYS + 1) * CELL_DAYS
    }

    /** Month and day of the month (1 … 30) holding contract [day], as the bar chart form enters a bar's ends. */
    fun monthDayOf(day: Int): Pair<Int, Int> {
        val d = day.coerceAtLeast(1) - 1
        return (d / MONTH_DAYS + 1) to (d % MONTH_DAYS + 1)
    }

    /** Versions in effective-date order; blank or unreadable JSON is no chart. */
    fun parse(json: String): List<Version> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val v = arr.getJSONObject(i)
            val items = v.getJSONArray("items")
            Version(v.optString("label"), v.optString("effective"), (0 until items.length()).map { j ->
                val it = items.getJSONObject(j)
                val bars = it.getJSONArray("bars")
                Item(it.getString("name"), it.optDouble("amount", 0.0), (0 until bars.length()).map { k ->
                    bars.getJSONArray(k).let { b -> Bar(b.getInt(0), b.getInt(1)) }
                }, it.optJSONArray("marks")?.toString().orEmpty())
            }, v.optJSONArray("colors")?.toString().orEmpty())
        }.sortedBy { it.effective }
    }.getOrDefault(emptyList())

    /** One of a chart's own colours, named on the website (e.g. Suspension No. 1); [work] is whether work is done on its days. */
    data class Colour(val name: String, val argb: Int, val work: Boolean)

    /** A day of the plain bar in [dayFills]; a coloured day is its colour's index. */
    const val WORK = -1

    /** The chart's colours as the website reads them: one that is not #RRGGBB shows as its first new colour, #FFD966. */
    fun colours(version: Version): List<Colour> = runCatching {
        val arr = JSONArray(version.colors)
        (0 until arr.length()).map { i ->
            val c = arr.optJSONObject(i) ?: JSONObject()
            val hex = c.optString("color").takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) } ?: "#FFD966"
            Colour(c.optString("name"), (0xFF000000 or hex.substring(1).toLong(16)).toInt(), c.opt("work") == true)
        }
    }.getOrDefault(emptyList())

    /** An item's coloured days as saved by the website: from day, to day, colour index; one that does not read is left out. */
    fun marks(item: Item): List<Triple<Int, Int, Int>> = runCatching {
        val arr = JSONArray(item.marks)
        (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONArray(i)?.takeIf { it.length() == 3 } ?: return@mapNotNull null
            val (a, b, k) = (0..2).map { m.opt(it) as? Int ?: return@mapNotNull null }
            Triple(a, b, k).takeIf { a in 1..b && b <= 120 * MONTH_DAYS && k >= 0 }
        }
    }.getOrDefault(emptyList())

    /**
     * What each of an item's first [days] days shows, as on the website's sheet: null, [WORK] or a colour's index.
     * A coloured day shows only where it agrees with the bar: on it for a colour work is done on, off it for one with none.
     */
    fun dayFills(item: Item, colours: List<Colour>, days: Int): List<Int?> {
        val out = arrayOfNulls<Int>(days)
        fun put(a: Int, b: Int, value: Int, on: Boolean?) {
            for (d in maxOf(a, 1)..minOf(b, days)) if (on == null || (out[d - 1] == WORK) == on) out[d - 1] = value
        }
        item.bars.forEach { put(it.startDay, it.endDay, WORK, null) }
        marks(item).forEach { (a, b, k) -> colours.getOrNull(k)?.let { put(a, b, k, it.work) } }
        return out.toList()
    }

    fun toJson(versions: List<Version>): String = JSONArray(versions.map { v ->
        JSONObject().put("label", v.label).put("effective", v.effective).put("items", JSONArray(v.items.map { item ->
            JSONObject().put("name", item.name).put("amount", item.amount)
                .put("bars", JSONArray(item.bars.map { JSONArray(listOf(it.startDay, it.endDay)) }))
                .apply { if (item.marks.isNotEmpty()) put("marks", JSONArray(item.marks)) }
        })).apply { if (v.colors.isNotEmpty()) put("colors", JSONArray(v.colors)) }
    }).toString()

    /** The chart in force on [date]: the latest one effective by then, else the first. */
    fun versionOn(versions: List<Version>, date: LocalDate): Version? =
        versions.lastOrNull { isoDate(it.effective)?.let { e -> !e.isAfter(date) } ?: true } ?: versions.firstOrNull()

    /**
     * Contract day of [date] for [item] (the start date is day 1), less the days of approved suspensions that
     * pause it and took effect on or after [effective]: a revised chart already draws earlier ones as idle months.
     * A Full suspension pauses every item; a Partial one only the items it names. A null [item] counts Full ones only.
     * The [extended] days an approved time extension adds to the chart cover suspended days, less those drawn before [effective].
     */
    fun contractDay(start: LocalDate, effective: LocalDate, documents: List<PendingDocument>, date: LocalDate, item: String? = null, extended: Int = 0): Int {
        val covered = (extended - suspendedDays(documents, start, effective.minusDays(1), item)).coerceAtLeast(0)
        return (ChronoUnit.DAYS.between(start, date) + 1 - (suspendedDays(documents, effective, date, item) - covered).coerceAtLeast(0)).toInt()
    }

    /** Days from [from] through [until] that approved suspensions (and their continuances) pause [item]; null: Full ones only. */
    fun suspendedDays(documents: List<PendingDocument>, from: LocalDate, until: LocalDate, item: String? = null): Long =
        periods(documents)
            .filter { (doc, _) -> pausedItems(doc, documents)?.let { item != null && item in it } ?: true }
            .sumOf { (_, period) ->
                val (begin, end) = period
                if (begin.isBefore(from)) 0L
                else ChronoUnit.DAYS.between(begin, minOf(end ?: until.plusDays(1), until.plusDays(1))).coerceAtLeast(0)
            }

    /**
     * Each approved suspension (and continuance) with when it stops the clock: its Effectivity up to (not including)
     * its resumption order's Effectivity or the day after its End (its last suspended day), whichever comes first;
     * a null end means it is still suspended.
     */
    private fun periods(documents: List<PendingDocument>): List<Pair<PendingDocument, Pair<LocalDate, LocalDate?>>> {
        val approved = documents.filter { it.status.equals("Approved", ignoreCase = true) }
        return approved.filter { it.documentName.contains("Work Suspension", ignoreCase = true) }.mapNotNull { doc ->
            val begin = field(doc.remarks, "Effectivity") ?: return@mapNotNull null
            doc to (begin to listOfNotNull(ProjectSchedule.suspensionEnd(doc.remarks)?.plusDays(1), resumptionOf(doc, begin, documents)?.second).minOrNull())
        }
    }

    /**
     * The approved resumption order of the suspension or continuance [doc], begun on [begin], with its Effectivity:
     * the first of its number to take effect on or after that first day (an earlier one resumed work before it was
     * ordered); a Partial resumption leaves a Full suspension on.
     */
    private fun resumptionOf(doc: PendingDocument, begin: LocalDate, documents: List<PendingDocument>): Pair<PendingDocument, LocalDate>? {
        val partial = isPartial(doc.documentName)
        return documents.filter { it.status.equals("Approved", ignoreCase = true) && it.documentName.contains("Work Resumption", ignoreCase = true) }
            .mapNotNull { r -> field(r.remarks, "Effectivity")?.let { r to it } }.sortedBy { it.second }
            .firstOrNull { (r, on) -> !on.isBefore(begin) && orderNo(r.documentName) == orderNo(doc.documentName) && (partial || !isPartial(r.documentName)) }
    }

    /**
     * An approved suspension's or continuance's remarks as it took effect (the website's inEffect): one its resumption
     * order cut short ends the day before that order's Effectivity, the Duration following, with a note of the order
     * and of the days approved. The record itself keeps what was approved. Any other order's remarks come back unchanged.
     */
    fun inEffect(doc: PendingDocument, documents: List<PendingDocument>): String {
        if (!doc.status.equals("Approved", ignoreCase = true) || !doc.documentName.contains("Work Suspension", ignoreCase = true)) return doc.remarks
        val begin = field(doc.remarks, "Effectivity") ?: return doc.remarks
        val (by, on) = resumptionOf(doc, begin, documents) ?: return doc.remarks
        if (ProjectSchedule.suspensionEnd(doc.remarks)?.let { on.isAfter(it) } == true) return doc.remarks
        val order = ProjectSchedule.orderLabel(by.documentName)
        if (on == begin) return "${doc.remarks} | No days: $order took effect on its first day"
        val approved = Regex("Duration: (\\d+)").find(doc.remarks)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return ProjectSchedule.withEndDate(doc.remarks, on.minusDays(1).toString()) + " | Ended by $order" + (if (approved > 0) " (approved for $approved days)" else "")
    }

    /**
     * Days a Time Extension for a suspension asks for: the days approved suspensions and continuances stopped work
     * ([periods], the effectivity date as day 1, cut short by a resumption), less the days of time extensions
     * already logged for a suspension (" - N Days" names, "suspension" in the reason). Still-open ones count nothing yet.
     */
    fun suspensionExtensionDays(documents: List<PendingDocument>): Int {
        val suspended = periods(documents).sumOf { (_, period) -> period.second?.let { ChronoUnit.DAYS.between(period.first, it).toInt().coerceAtLeast(0) } ?: 0 }
        val claimed = documents.filter { it.documentName.contains("Time Extension", ignoreCase = true) && isForSuspension(it.remarks) }
            .sumOf { Regex(" - (\\d+) Days", RegexOption.IGNORE_CASE).find(it.documentName)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
        return (suspended - claimed).coerceAtLeast(0)
    }

    /** A time extension reason that names a suspension ("suspension", "suspensyon"). */
    fun isForSuspension(reason: String) = reason.contains("suspen", ignoreCase = true)

    /** True when an approved Full suspension (or continuance) is in effect on [date]; Partial ones leave the project going. */
    fun isSuspendedOn(documents: List<PendingDocument>, date: LocalDate): Boolean = periods(documents).any { (doc, period) ->
        val (begin, end) = period
        pausedItems(doc, documents) == null && !date.isBefore(begin) && (end == null || date.isBefore(end))
    }

    /**
     * [project]'s status on [date]: Suspended while an approved Full suspension is in effect (until its End date or its
     * resumption order's Effectivity), else Behind Schedule at any negative slippage, else On-going.
     * The behind-schedule flag still waits for [ProjectAttention.WARNING_SLIPPAGE].
     */
    fun withScheduleStatus(project: Project, documents: List<PendingDocument>, date: LocalDate): Project = when {
        project.isCompleted -> project
        isSuspendedOn(documents, date) -> project.copy(status = ProjectStatus.SUSPENDED.label)
        ProjectAttention.slippage(project) < 0 -> project.copy(status = ProjectStatus.BEHIND_SCHEDULE.label)
        else -> project.copy(status = ProjectStatus.ONGOING.label)
    }

    /**
     * [project] as of [date]: its target from the chart; then, unless finished, the revised completion from approved time
     * extensions (the stored date can lag them, and the dashboard, flags and header must match the Summary's Key dates)
     * and its status from its suspension orders. A finished project keeps its stored date, the day it was completed.
     */
    fun withSchedule(project: Project, documents: List<PendingDocument>, date: LocalDate): Project =
        withScheduleStatus(withRevisedCompletion(withPlannedTarget(project, documents, date), documents), documents, date)

    private fun withRevisedCompletion(project: Project, documents: List<PendingDocument>): Project =
        if (project.isCompleted) project
        else ProjectSchedule.revisedCompletion(project.dateStarted, project.contractDurationDays, documents)
            ?.let { project.copy(completionDateRevised = it) } ?: project

    /**
     * The work items a Partial suspension pauses ("Items: A; B" in its remarks; a continuance without its own list
     * takes its suspension's), or null for a Full one, which pauses everything.
     */
    fun pausedItems(doc: PendingDocument, documents: List<PendingDocument>): Set<String>? {
        if (!isPartial(doc.documentName)) return null
        val own = itemsOf(doc.remarks)
        if (own != null || !doc.documentName.contains("Continuance", ignoreCase = true)) return own.orEmpty()
        return documents.firstOrNull {
            !it.documentName.contains("Continuance", ignoreCase = true) && ProjectSchedule.isSuspension(it) &&
                orderNo(it.documentName) == orderNo(doc.documentName)
        }?.let { itemsOf(it.remarks) }.orEmpty()
    }

    /** The work items of the latest chart, for picking what a Partial suspension pauses; empty without a chart. */
    fun itemNames(project: Project): List<String> = parse(project.barChartJson).lastOrNull()?.items?.map { it.name }.orEmpty()

    /** " | Items: A; B" for a Partial suspension's remarks; empty when none are picked. */
    fun itemsRemark(items: Collection<String>): String = if (items.isEmpty()) "" else " | Items: " + items.joinToString("; ")

    private fun itemsOf(remarks: String): Set<String>? =
        Regex("Items: ([^|]*)").find(remarks)?.groupValues?.get(1)?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()

    /** The chart in force on a date, its contract day (Full suspensions out) and each item's own contract day. */
    data class Plan(val version: Version, val day: Int, val itemDays: List<Int>) {
        val plannedPct: Double
            get() = if (version.total <= 0) 0.0
            else version.items.zip(itemDays).sumOf { (item, d) -> item.amount * item.plannedShare(d) } / version.total * 100
        /** Each item's planned accomplishment (% of the item, 2 decimals). */
        val itemTargets: Map<String, Double>
            get() = version.items.zip(itemDays).associate { (item, d) -> item.name to Math.round(item.plannedShare(d) * 10000) / 100.0 }
    }

    /** The plan on [date]; null without a chart or start date. */
    fun planOn(project: Project, documents: List<PendingDocument>, date: LocalDate): Plan? {
        val version = versionOn(parse(project.barChartJson), date) ?: return null
        val start = isoDate(project.dateStarted) ?: return null
        val effective = isoDate(version.effective)?.takeIf { it.isAfter(start) } ?: start
        // shortcut: an extension is taken to be for the suspensions, as the office grants them; one for another cause would hide a later suspension too.
        val extended = minOf(ProjectSchedule.addedDays(documents), version.lastDay - project.contractDurationDays).coerceAtLeast(0)
        return Plan(
            version, contractDay(start, effective, documents, date, extended = extended),
            version.items.map { contractDay(start, effective, documents, date, it.name, extended) }
        )
    }

    /** Planned accomplishment (%) on [date], or null when the project has no chart. */
    fun targetOn(project: Project, documents: List<PendingDocument>, date: LocalDate): Double? =
        planOn(project, documents, date)?.plannedPct

    /** Each item's planned accomplishment (% of the item, 2 decimals) on [date], or null without a chart. */
    fun itemTargetsOn(project: Project, documents: List<PendingDocument>, date: LocalDate): Map<String, Double>? =
        planOn(project, documents, date)?.itemTargets

    /** [project] with today's target from its chart; unchanged without a chart, or once completed. */
    fun withPlannedTarget(project: Project, documents: List<PendingDocument>, date: LocalDate): Project {
        if (project.isCompleted) return project
        val target = targetOn(project, documents, date) ?: return project
        return project.copy(targetAccomplishment = Math.round(target * 100) / 100.0)
    }

    private fun field(remarks: String, key: String): LocalDate? =
        Regex("$key: (\\d{4}-\\d{2}-\\d{2})").find(remarks)?.groupValues?.get(1)?.let(::isoDate)

    private fun isPartial(name: String) = ProjectSchedule.orderLabel(name).contains("Partial", ignoreCase = true)

    private fun orderNo(name: String) = Regex("No\\. ?(\\S+?)(?:\\s|$|\\))").find(name)?.groupValues?.get(1)

    private fun isoDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()
}
