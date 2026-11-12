package com.example.ui.dialogs

import com.example.data.model.WeeklyReport
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The Saturday-to-Friday reporting week a new weekly report starts on, written the way the week picker
 * writes a chosen range ("Oct 19, 2026 - Oct 25, 2026"), so every screen that reads it parses it.
 */
object WeekRange {
    private val format = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    fun containing(day: LocalDate): String {
        val saturday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        return "${saturday.format(format)} - ${saturday.plusDays(6).format(format)}"
    }

    /**
     * No other report in [all] is for a later week than [report]'s. Only then does the project's target and actual
     * follow a report that was added or edited, so a late entry or a fix to an older week does not set the project back.
     * Labels that don't read count as the earliest.
     */
    fun isLatest(report: WeeklyReport, all: List<WeeklyReport>): Boolean {
        fun start(r: WeeklyReport) = com.example.util.BlueBookForms.weekStart(r.reportingWeek) ?: LocalDate.MIN
        return all.none { it.uuid != report.uuid && start(it) > start(report) }
    }

    /** Weekly reports in week order: by first day (labels that don't parse first), then as entered. */
    val byWeek: Comparator<WeeklyReport> = compareBy({ parse(it.reportingWeek)?.first }, { it.id })

    /** First and last day of a range; null for labels written another way (older "Week 41: ..." reports). */
    fun parse(range: String): Pair<LocalDate, LocalDate>? = runCatching {
        val (start, end) = range.split(" - ").map { LocalDate.parse(it.trim(), format) }
        start to end
    }.getOrNull()
}
