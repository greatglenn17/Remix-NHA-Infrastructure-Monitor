package com.example

import com.example.data.model.WeeklyReport
import com.example.ui.dialogs.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekRangeTest {

    @Test
    fun friday_belongsToTheWeekThatStartedOnSaturday() {
        assertEquals("Oct 24, 2026 - Oct 30, 2026", WeekRange.containing(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun midweek_givesTheSameSaturdayToFridayRange() {
        assertEquals("Oct 24, 2026 - Oct 30, 2026", WeekRange.containing(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun saturday_startsANewWeek_acrossMonths() {
        assertEquals("Oct 31, 2026 - Nov 6, 2026", WeekRange.containing(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun weekAcrossNewYear_showsEachYear() {
        assertEquals("Dec 26, 2026 - Jan 1, 2027", WeekRange.containing(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun byWeek_ordersByTheWeek_notTheOrderEntered() {
        fun r(id: Long, week: String) = WeeklyReport(
            id = id, projectId = 1, reportingWeek = week, daysElapsed = 0, remainingDays = 0,
            targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0, manpowerJson = "[]", equipmentJson = "[]",
            activitiesJson = "[]", issuesJson = "[]", accomplishmentItemsJson = "[]",
            documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = "S"
        )
        // The Oct 10 week was back-filled after the Oct 17 one.
        val reports = listOf(r(1, "Oct 3, 2026 - Oct 9, 2026"), r(2, "Oct 17, 2026 - Oct 23, 2026"), r(3, "Oct 10, 2026 - Oct 16, 2026"), r(4, "Week 41: old"))
        assertEquals(listOf(4L, 1L, 3L, 2L), reports.sortedWith(WeekRange.byWeek).map { it.id })
        assertEquals(2L, reports.maxWithOrNull(WeekRange.byWeek)?.id)
    }

    @Test
    fun isLatest_onlyWhenNoOtherReportIsForALaterWeek() {
        fun r(week: String) = WeeklyReport(
            projectId = 1, reportingWeek = week, daysElapsed = 0, remainingDays = 0,
            targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0, manpowerJson = "[]", equipmentJson = "[]",
            activitiesJson = "[]", issuesJson = "[]", accomplishmentItemsJson = "[]",
            documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = "S"
        )
        val older = r("Week 42: October 17 - October 23, 2026")
        val middle = r("Oct 24, 2026 - Oct 30, 2026")
        val newest = r("Oct 31, 2026 - Nov 6, 2026")
        val all = listOf(older, middle, newest)
        assertEquals(listOf(false, false, true), all.map { WeekRange.isLatest(it, all) })
        // The report itself does not count, before or after its week is edited.
        assertEquals(true, WeekRange.isLatest(newest.copy(reportingWeek = "Nov 7, 2026 - Nov 13, 2026"), all))
        assertEquals(false, WeekRange.isLatest(newest.copy(reportingWeek = "Oct 10, 2026 - Oct 16, 2026"), all))
        // A back-filled week, a second report for the newest week, the only report, and a label that does not read.
        assertEquals(false, WeekRange.isLatest(r("Oct 10, 2026 - Oct 16, 2026"), all))
        assertEquals(true, WeekRange.isLatest(r("Oct 31, 2026 - Nov 6, 2026"), all))
        assertEquals(true, WeekRange.isLatest(older, listOf(older)))
        assertEquals(false, WeekRange.isLatest(r("Week 41: old"), all))
    }
}
