package com.example.util

import com.example.data.model.PendingDocument
import com.example.data.model.Project
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

    data class Item(val name: String, val amount: Double, val bars: List<Bar>) {
        val days: Int get() = bars.sumOf { it.days }

        /** Share of the item planned done by the end of contract [day], 0 to 1. */
        fun plannedShare(day: Int): Double =
            if (days <= 0) 0.0 else bars.sumOf { (minOf(day, it.endDay) - it.startDay + 1).coerceIn(0, it.days) }.toDouble() / days
    }

    /** One approved chart; a revised chart applies from its [effective] date (yyyy-MM-dd). */
    data class Version(val label: String, val effective: String, val items: List<Item>) {
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
                })
            })
        }.sortedBy { it.effective }
    }.getOrDefault(emptyList())

    fun toJson(versions: List<Version>): String = JSONArray(versions.map { v ->
        JSONObject().put("label", v.label).put("effective", v.effective).put("items", JSONArray(v.items.map { item ->
            JSONObject().put("name", item.name).put("amount", item.amount)
                .put("bars", JSONArray(item.bars.map { JSONArray(listOf(it.startDay, it.endDay)) }))
        }))
    }).toString()

    /** The chart in force on [date]: the latest one effective by then, else the first. */
    fun versionOn(versions: List<Version>, date: LocalDate): Version? =
        versions.lastOrNull { isoDate(it.effective)?.let { e -> !e.isAfter(date) } ?: true } ?: versions.firstOrNull()

    /**
     * Contract day of [date] (the start date is day 1), less the days of approved Full suspensions
     * that took effect on or after [effective]: a revised chart already draws earlier ones as idle months.
     */
    fun contractDay(start: LocalDate, effective: LocalDate, documents: List<PendingDocument>, date: LocalDate): Int =
        (ChronoUnit.DAYS.between(start, date) + 1 - suspendedDays(documents, effective, date)).toInt()

    /** Days from [from] through [until] covered by approved Full suspensions and their continuances. */
    fun suspendedDays(documents: List<PendingDocument>, from: LocalDate, until: LocalDate): Long {
        val approved = documents.filter { it.status.equals("Approved", ignoreCase = true) }
        val resumptions = approved.filter { it.documentName.contains("Work Resumption", ignoreCase = true) }
        return approved
            .filter { it.documentName.contains("Work Suspension", ignoreCase = true) }
            .filterNot { ProjectSchedule.orderLabel(it.documentName).contains("Partial", ignoreCase = true) }
            .sumOf { doc ->
                val begin = field(doc.remarks, "Effectivity") ?: return@sumOf 0L
                if (begin.isBefore(from)) return@sumOf 0L
                // Work resumes on the End date; without one, on its resumption order, else it is still suspended.
                val end = field(doc.remarks, "End")
                    ?: resumptions.firstOrNull { orderNo(it.documentName) == orderNo(doc.documentName) }?.let { field(it.remarks, "Effectivity") }
                    ?: until.plusDays(1)
                ChronoUnit.DAYS.between(begin, minOf(end, until.plusDays(1))).coerceAtLeast(0)
            }
    }

    /** The chart in force on [date] and the contract day it is read at; null without a chart or start date. */
    fun planOn(project: Project, documents: List<PendingDocument>, date: LocalDate): Pair<Version, Int>? {
        val version = versionOn(parse(project.barChartJson), date) ?: return null
        val start = isoDate(project.dateStarted) ?: return null
        val effective = isoDate(version.effective)?.takeIf { it.isAfter(start) } ?: start
        return version to contractDay(start, effective, documents, date)
    }

    /** Planned accomplishment (%) on [date], or null when the project has no chart. */
    fun targetOn(project: Project, documents: List<PendingDocument>, date: LocalDate): Double? =
        planOn(project, documents, date)?.let { (v, day) -> v.plannedPct(day) }

    /** Each item's planned accomplishment (% of the item, 2 decimals) on [date], or null without a chart. */
    fun itemTargetsOn(project: Project, documents: List<PendingDocument>, date: LocalDate): Map<String, Double>? =
        planOn(project, documents, date)?.let { (v, day) ->
            v.items.associate { it.name to Math.round(it.plannedShare(day) * 10000) / 100.0 }
        }

    /** [project] with today's target from its chart; unchanged without a chart, or once completed. */
    fun withPlannedTarget(project: Project, documents: List<PendingDocument>, date: LocalDate): Project {
        if (project.isCompleted) return project
        val target = targetOn(project, documents, date) ?: return project
        return project.copy(targetAccomplishment = Math.round(target * 100) / 100.0)
    }

    private fun field(remarks: String, key: String): LocalDate? =
        Regex("$key: (\\d{4}-\\d{2}-\\d{2})").find(remarks)?.groupValues?.get(1)?.let(::isoDate)

    private fun orderNo(name: String) = Regex("No\\. ?(\\S+?)(?:\\s|$|\\))").find(name)?.groupValues?.get(1)

    private fun isoDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()
}
