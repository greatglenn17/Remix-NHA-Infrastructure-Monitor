package com.example

import com.example.data.model.DailyHourlyWeather
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.WeeklyReport
import com.example.util.BlueBookForms
import com.example.util.ContractLogs
import com.example.util.Preparer
import com.example.util.Signatories
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueBookFormsTest {

    private val project = Project(
        name = "St. Ildephonsus <Phase 1>", location = "Brgy. Anyatam, San Ildefonso, Bulacan",
        implementingOffice = "Bulacan District Office", contractor = "ARR Construction", scopeOfWork = "",
        contractCostOriginal = 495_623_331.0, contractCostRevised = 495_623_331.0, contractDurationDays = 405,
        dateStarted = "2018-10-01", completionDateOriginal = "2019-11-09", completionDateRevised = "",
        boardResolution = "NHA-BR-6252 dated December 12, 2018", noticeToProceed = "2018-09-24"
    )

    private fun doc(name: String, status: String, remarks: String) =
        PendingDocument(projectId = 1, documentName = name, status = status, remarks = remarks)

    private val docs = listOf(
        doc("Work Suspension No. 1 (Full)", "Approved", "Duration: 146 Days | Effectivity: 2019-01-03 | End: 2019-05-29 | Reason: Road widening"),
        doc("Work Resumption No. 1", "Approved", "Effectivity: 2019-05-30 | Remarks: "),
        doc("Time Extension No. 1 - 146 Days", "Approved", "Reason: Right of way"),
        doc("Time Extension No. 2 - 130 Days", "Pending", "Reason: Rain"),
        doc("Variation Order No. 1 (Additive) - PHP 0", "Approved", "Reason: Realignment")
    )

    private fun day(date: String, csv: String) =
        DailyHourlyWeather(projectId = 1, date = date, dayOfWeek = LocalDate.parse(date).dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }, hourlyConditionsCsv = csv)

    private fun hours(rainyWorkingHours: Int) = List(24) { h -> if (h in 8 until 8 + rainyWorkingHours) "RAINY" else "FAIR" }.joinToString(",")

    @Test
    fun orders_becomeTheProfileLists() {
        val logs = ContractLogs.from(project, docs)
        assertEquals(listOf("Work Suspension No. 1 (Full)" to "January 3, 2019 – May 29, 2019; Reason: Road widening"), logs.suspensions)
        assertEquals(listOf("Work Resumption No. 1" to "May 30, 2019"), logs.resumptions)
        assertEquals(listOf("1" to 146, "2" to 130), logs.extensions.map { it.no to it.days })
        assertEquals("(for approval)", logs.extensions[1].status)
        assertEquals("Right of way", logs.extensions[0].reason)
        assertEquals(listOf("Variation Order No. 1" to "Php 0; Reason: Realignment"), logs.variations)
    }

    @Test
    fun aDayIsUnworkable_whenMostWorkingHoursAreRainy() {
        val counts = BlueBookForms.workableDays(
            listOf(day("2026-07-01", hours(5)), day("2026-07-02", hours(4)), day("2026-08-01", hours(9)))
        )
        assertEquals(1 to 1, counts[YearMonth.of(2026, 7)])
        assertEquals(0 to 1, counts[YearMonth.of(2026, 8)])
    }

    @Test
    fun profile_isA4AndEscapesText() {
        val html = BlueBookForms.projectProfile(project, ContractLogs.from(project, docs), Preparer("Glenn C. Aprovechado", "Principal Engineer C", "Bulacan District Office"), LocalDate.of(2026, 5, 31))
        assertTrue(html.contains("size: A4"))
        assertTrue(html.contains("St. Ildephonsus &lt;Phase 1&gt;"))
        assertFalse(html.contains("<Phase 1>"))
        assertTrue(html.contains("September 24, 2018"))
        assertTrue(html.contains("GLENN C. APROVECHADO"))
    }

    @Test
    fun weeklyIssues_includeIssuesLoggedThatWeek() {
        assertEquals(LocalDate.of(2026, 10, 26), BlueBookForms.weekStart("Oct 26, 2026 - Nov 1, 2026"))
        assertEquals(LocalDate.of(2026, 7, 20), BlueBookForms.weekStart("Week 30: July 20 - July 26, 2026"))
        val report = WeeklyReport(
            projectId = 1, reportingWeek = "Oct 26, 2026 - Nov 1, 2026", daysElapsed = 0, remainingDays = 0,
            targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0, manpowerJson = "[]", equipmentJson = "[]",
            activitiesJson = "[]", issuesJson = "[]", accomplishmentItemsJson = "[]", documentsIssuedReceivedJson = "[]",
            attachedPhotoUrlsJson = "[]", submittedByStaff = ""
        )
        fun issue(date: String, text: String) = com.example.data.model.ProjectIssue(
            projectId = 1, date = date, description = text, actionTaken = "Coordinated", remarks = "", loggedBy = "", targetDate = "2026-11-05"
        )
        val week = BlueBookForms.weekIssues(
            report, listOf(com.example.data.model.WeeklyIssue("Typed in report", "None")),
            listOf(issue("2026-10-25", "Before"), issue("2026-10-28", "Right of way"), issue("2026-11-01", "Sunday"), issue("2026-11-02", "After"))
        )
        assertEquals(listOf("Typed in report", "Right of way", "Sunday"), week.map { it.description })
        assertEquals("Target: November 5, 2026", week[1].remarks)
    }

    @Test
    fun monthly_listsLoggedMonthsWithTotals_andPutsActionsUnderWhoTookThem() {
        fun issue(text: String, by: String) = com.example.data.model.ProjectIssue(
            projectId = 1, date = "2019-03-10", description = text, actionTaken = "Act on $text", remarks = "", loggedBy = "", actionBy = by
        )
        val html = BlueBookForms.monthlyReport(
            project, YearMonth.of(2019, 3), null, emptyList(), emptyList(),
            listOf(issue("Office", "Implementing Office"), issue("Contractor", "Contractor/Developer"), issue("Old", "")),
            ContractLogs.from(project, docs), emptyList(),
            listOf(day("2019-01-05", hours(5)), day("2019-03-01", hours(0)), day("2019-03-02", hours(9)), day("2019-04-01", hours(9))),
            null, Signatories("", "", "", "", "", "")
        )
        // October 2018 start: January is contract month 4, March month 6; February and April are left out.
        assertTrue(html.contains("<tr><td class='c'>4</td><td>January 2019</td><td class='n'>0</td><td class='n'>1</td></tr>"))
        assertTrue(html.contains("<tr><td class='c'>6</td><td>March 2019</td><td class='n'>1</td><td class='n'>1</td></tr>"))
        assertFalse(html.contains("February 2019"))
        assertFalse(html.contains("April 2019"))
        assertTrue(html.contains("<tr class='tot'><td colspan='2'>GRAND TOTAL</td><td class='n'>1</td><td class='n'>2</td></tr>"))
        assertTrue(html.contains("1. Office</td><td>March 10, 2019</td><td>Act on Office</td><td></td>"))
        assertTrue(html.contains("2. Contractor</td><td>March 10, 2019</td><td></td><td>Act on Contractor</td>"))
        assertTrue(html.contains("3. Old</td><td>March 10, 2019</td><td>Act on Old</td><td></td>"))
        assertTrue(html.contains("Variation Order No. 1: Php 0; Reason: Realignment"))
        assertTrue(html.contains("Reason: Road widening"))
        assertTrue(html.contains("Right of way"))
        // Every section after II. Project Status is numbered, through VIII. Recommendations.
        listOf("III. STATUS OF PAYMENT", "IV. CONTRACT SCHEDULE STATUS", "V. SUMMARY OF UNWORKABLE DAYS", "VI. STATUS OF SUBMISSION",
            "VII. ISSUES / CONCERNS", "VIII. RECOMMENDATIONS").forEach { assertTrue(it, html.contains(it)) }
    }

    @Test
    fun weekly_drawsOneWeatherClockPerDay() {
        val report = WeeklyReport(
            projectId = 1, reportingWeek = "Week 30: July 20 - July 26, 2026", daysElapsed = 10, remainingDays = 20,
            targetAccomplishmentPct = 50.0, actualAccomplishmentPct = 48.0, manpowerJson = "[]", equipmentJson = "[]",
            activitiesJson = "[]", issuesJson = "[]", accomplishmentItemsJson = "[]", documentsIssuedReceivedJson = "[]",
            attachedPhotoUrlsJson = "[]", submittedByStaff = "Engr. Staff"
        )
        val html = BlueBookForms.weeklyReport(
            project, report, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            listOf(day("2026-07-20", hours(5))), listOf("data:image/jpeg;base64,BBBB" to "Footing, Block 3"), ContractLogs.from(project, docs),
            Signatories("Glenn C. Aprovechado", "Principal Engineer C", "Ana Cruz", "District Manager", "", "Regional Manager"),
            banner = "data:image/png;base64,AAAA"
        )
        assertEquals(7, Regex("<svg").findAll(html).count())
        // 24 hour slices per clock; days with no log are drawn empty.
        assertEquals(7 * 24, Regex("<path").findAll(html).count())
        assertTrue(html.contains("July 20, 2026"))
        assertTrue(html.contains("GLENN C. APROVECHADO"))
        assertTrue(html.contains("Principal Engineer C"))
        assertTrue(html.contains("<img class='banner' src='data:image/png;base64,AAAA'>"))
        assertFalse(html.contains("FORM NO."))
        assertTrue(html.contains("<img src='data:image/jpeg;base64,BBBB'><div>Footing, Block 3</div>"))
    }
}
