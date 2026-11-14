package com.example

import com.example.data.model.Project
import com.example.data.model.ProjectAbstract
import com.example.data.model.WeeklyReport
import com.example.util.AbstractExcel
import com.example.util.BlueBookForms
import com.example.util.ProjectAbstracts
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The uploaded abstracts (Actual Accomplishment tab): the figures of web/logic.test.mjs "abstracts: ..." and of the
// made-up workbook in AbstractExcelTest.
@RunWith(RobolectricTestRunner::class)
class ProjectAbstractsTest {
    private val none3 = listOf<Double?>(null, null, null)
    private val none2 = listOf<Double?>(null, null)
    private fun row(kind: String, no: String, name: String, amount: Double? = null, toDate: List<Double?> = none3) =
        AbstractExcel.Row(kind, no, name, null, "", null, amount, none3, none3, toDate, none2)

    private val item = AbstractExcel.Row(
        "item", "2.1.1", "Mobilization/Demobilization", 1.0, "l.s.", 400000.0, 400000.0,
        prev = listOf(0.0, 0.0, 0.0), period = listOf(0.25, 100000.0, 25.0), toDate = listOf(0.25, 100000.0, 25.0), balance = listOf(0.75, 300000.0)
    )
    private val rows = listOf(
        row("part", "1", "LAND ACQUISITION COST"), row("subtotal", "", "Subtotal (Land Acquisition Cost)", 1000000.0, listOf(null, 500000.0, 50.0)),
        row("part", "2", "LAND DEVELOPMENT"), row("group", "2.1", "GENERAL REQUIREMENTS"), item,
        row("subtotal", "", "Subtotal (General Requrements)", 400000.0, listOf(null, 100000.0, 25.0)),
        row("item", "2.2.1", "Fill & <Compaction>", 1600000.0, listOf(null, 404000.0, 25.25)),
        row("subtotal", "", "Subtotal (Land Development)", 2000000.0, listOf(null, 504000.0, 25.21)),
        row("part", "3", "HOUSING CONSTRUCTION"), row("subtotal", "", "Subtotal (Housing Construction)", 7000000.0, listOf(null, 70000.0, 1.0)),
        row("total", "", "GRAND TOTAL", 10000000.0, listOf(null, 1074000.0, 10.73)),
    )

    private fun abstract(asOf: String, overall: Double = 10.73, updatedAt: Long = 1) =
        ProjectAbstract(projectId = 1, asOf = asOf, overallPct = overall, rowsJson = ProjectAbstracts.rowsJson(rows), updatedAt = updatedAt)

    private fun weekly(week: String, actual: Double) = WeeklyReport(
        projectId = 1, reportingWeek = week, daysElapsed = 0, remainingDays = 0, targetAccomplishmentPct = 0.0, actualAccomplishmentPct = actual,
        manpowerJson = "[]", equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]", accomplishmentItemsJson = "[]",
        documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = ""
    )

    @Test fun rowsAreKeptAsTheWebsiteWritesThem() {
        assertEquals(rows, ProjectAbstracts.rows(ProjectAbstracts.rowsJson(rows)))
        // The keys in the record's order, whole numbers without a decimal point, an empty cell as null.
        assertEquals(
            """[{"kind":"subtotal","no":"","name":"Subtotal (Land Development)","qty":null,"unit":"","unitCost":null,"amount":2000000,""" +
                """"prev":[null,null,null],"period":[null,null,null],"toDate":[null,504000,25.21],"balance":[null,null]}]""",
            ProjectAbstracts.rowsJson(rows.subList(7, 8))
        )
        // A row the website saved.
        assertEquals(
            listOf(item),
            ProjectAbstracts.rows(
                """[{"kind":"item","no":"2.1.1","name":"Mobilization/Demobilization","qty":1,"unit":"l.s.","unitCost":400000,"amount":400000,""" +
                    """"prev":[0,0,0],"period":[0.25,100000,25],"toDate":[0.25,100000,25],"balance":[0.75,300000]}]"""
            )
        )
        assertEquals(emptyList<AbstractExcel.Row>(), ProjectAbstracts.rows("not json"))
        assertEquals(emptyList<AbstractExcel.Row>(), ProjectAbstracts.rows(""))
    }

    @Test fun theLatestIsByAsOfDateThenByLastChange() {
        val march = abstract("2025-03-14")
        val april = abstract("2025-04-11", 15.0)
        val aprilAgain = abstract("2025-04-11", 16.0, updatedAt = 2) // the same date uploaded on a second device
        assertEquals(april, ProjectAbstracts.latest(listOf(april, march)))
        assertEquals(aprilAgain, ProjectAbstracts.latest(listOf(aprilAgain, march, april)))
        assertNull(ProjectAbstracts.latest(emptyList()))
    }

    @Test fun aWeeklyReportTakesTheAbstractDatedInItsWeek() {
        val friday = abstract("2025-03-14")
        val monday = abstract("2025-03-10", 9.0)
        assertEquals(friday, ProjectAbstracts.inWeek(listOf(monday, friday), "Mar 8, 2025 - Mar 14, 2025"))
        assertEquals(monday, ProjectAbstracts.inWeek(listOf(monday), "Week 11: March 8 - March 14, 2025")) // an older label
        assertNull(ProjectAbstracts.inWeek(listOf(monday, friday), "Mar 15, 2025 - Mar 21, 2025"))
        assertNull(ProjectAbstracts.inWeek(listOf(monday, friday), "Mar 1, 2025 - Mar 7, 2025"))
        assertNull(ProjectAbstracts.inWeek(listOf(friday), "some week"))
        // Its parts fill the three lines; a line it has no part for is 0.
        assertEquals(listOf(50.0, 25.21, 1.0), ProjectAbstracts.lineActuals(friday))
        assertEquals(listOf(0.0, 25.21, 0.0), ProjectAbstracts.lineActuals(friday.copy(rowsJson = ProjectAbstracts.rowsJson(rows.subList(2, 8)))))
    }

    @Test fun theProjectsActualIsTheLatestAbstractsThenTheLatestWeeklyReports() {
        val reports = listOf(weekly("Mar 1, 2025 - Mar 7, 2025", 9.0), weekly("Mar 8, 2025 - Mar 14, 2025", 10.74))
        assertEquals(15.0, ProjectAbstracts.projectActualAfter(listOf(abstract("2025-03-14"), abstract("2025-04-11", 15.0)), reports, 3.0), 0.0)
        assertEquals(10.74, ProjectAbstracts.projectActualAfter(emptyList(), reports, 3.0), 0.0) // the last abstract was deleted
        assertEquals(3.0, ProjectAbstracts.projectActualAfter(emptyList(), emptyList(), 3.0), 0.0)
    }

    @Test fun aReadWorkbookBecomesTheRecord() {
        val parts = AbstractExcel.parts(rows)
        val found = AbstractExcel.Abstract(LocalDate.of(2025, 3, 14), parts, 10.73, false, rows)
        val record = ProjectAbstracts.record(7, found, "abstract.xlsx", "Engr. Sample")!!
        assertEquals(ProjectAbstract(projectId = 7, asOf = "2025-03-14", overallPct = 10.73, rowsJson = ProjectAbstracts.rowsJson(rows), fileName = "abstract.xlsx", uploadedBy = "Engr. Sample", uuid = record.uuid, updatedAt = record.updatedAt), record)
        assertEquals("Mar 14, 2025: overall 10.73%", ProjectAbstracts.summary(record))
        // Without a grand total row: the parts' % by their contract amounts, 50 x 1 + 25.21 x 2 + 1 x 7 over 10.
        assertEquals(10.74, ProjectAbstracts.record(7, found.copy(overallPct = null), "", "")!!.overallPct, 0.0)
        assertEquals(0.0, ProjectAbstracts.record(7, found.copy(overallPct = null, parts = parts.map { it.copy(amount = 0.0) }), "", "")!!.overallPct, 0.0)
        assertNull(ProjectAbstracts.record(7, found.copy(asOf = null), "", "")) // nothing to file it under
    }

    @Test fun theFullTableHasEveryColumnOfTheSheet() {
        val project = Project(
            name = "P", location = "", implementingOffice = "", contractor = "", scopeOfWork = "", contractCostOriginal = 10_000_000.0,
            contractCostRevised = 10_000_000.0, contractDurationDays = 360, dateStarted = "2025-01-06", completionDateOriginal = "2025-12-31",
            completionDateRevised = "", boardResolution = "", noticeToProceed = ""
        )
        val html = BlueBookForms.actualAbstract(project, abstract("2025-03-14"))
        fun has(vararg cells: String) = assertTrue(cells.joinToString(), Regex(cells.joinToString("<td[^>]*") { ">" + Regex.escape(it) + "</td>" }).containsMatchIn(html))
        assertTrue(html.contains("As of March 14, 2025"))
        assertTrue(html.contains("ACTUAL ACCOMPLISHMENT</td><td class='pc'>:</td><td>10.73%"))
        has("2.1.1", "Mobilization/Demobilization", "1", "l.s.", "400,000.00", "400,000.00", "0", "0.00", "0.00%", "0.25", "100,000.00", "25.00%", "0.25", "100,000.00", "25.00%", "0.75", "300,000.00")
        // A cell the sheet left empty stays blank, and the workbook's text is escaped.
        has("2.2.1", "Fill &amp; &lt;Compaction&gt;", "", "", "", "1,600,000.00", "", "", "", "", "", "", "", "404,000.00", "25.25%", "", "")
        has("", "GRAND TOTAL", "", "", "", "10,000,000.00", "", "", "", "", "", "", "", "1,074,000.00", "10.73%", "", "")
        assertTrue(html.contains("<td class='c'>2.1</td><td><b>GENERAL REQUIREMENTS</b></td>"))
        assertTrue(html.contains("size: A4 landscape"))
    }
}
