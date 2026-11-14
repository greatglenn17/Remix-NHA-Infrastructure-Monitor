package com.example

import com.example.data.model.PendingDocument
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
    private fun row(kind: String, no: String, name: String, amount: Double? = null, toDate: List<Double?> = none3, period: List<Double?> = none3) =
        AbstractExcel.Row(kind, no, name, null, "", null, amount, none3, period, toDate, none2)

    private val item = AbstractExcel.Row(
        "item", "2.1.1", "Mobilization/Demobilization", 1.0, "l.s.", 400000.0, 400000.0,
        prev = listOf(0.0, 0.0, 0.0), period = listOf(0.25, 100000.0, 25.0), toDate = listOf(0.25, 100000.0, 25.0), balance = listOf(0.75, 300000.0),
        equiv = listOf(0.0, 342.5, 1027.5)
    )
    private val rows = listOf(
        row("part", "1", "LAND ACQUISITION COST"), row("subtotal", "", "Subtotal (Land Acquisition Cost)", 1000000.0, listOf(null, 500000.0, 50.0)),
        row("part", "2", "LAND DEVELOPMENT"), row("group", "2.1", "GENERAL REQUIREMENTS"), item,
        row("subtotal", "", "Subtotal (General Requrements)", 400000.0, listOf(null, 100000.0, 25.0), period = listOf(null, 80000.0, 5.0)),
        row("item", "2.2.1", "Fill & <Compaction>", 1600000.0, listOf(null, 404000.0, 25.25)),
        row("subtotal", "", "Subtotal (Land Development)", 2000000.0, listOf(null, 504000.0, 25.21), period = listOf(null, 104000.0, 5.2)),
        row("part", "3", "HOUSING CONSTRUCTION"), row("subtotal", "", "Subtotal (Housing Construction)", 7000000.0, listOf(null, 70000.0, 1.0)),
        row("total", "", "GRAND TOTAL", 10000000.0, listOf(null, 1074000.0, 10.73)),
    )

    private fun abstract(asOf: String, overall: Double = 10.73, updatedAt: Long = 1) =
        ProjectAbstract(projectId = 1, asOf = asOf, overallPct = overall, rowsJson = ProjectAbstracts.rowsJson(rows), updatedAt = updatedAt)

    private val project = Project(
        name = "P", location = "", implementingOffice = "", contractor = "", scopeOfWork = "", contractCostOriginal = 10_000_000.0,
        contractCostRevised = 10_000_000.0, contractDurationDays = 360, dateStarted = "2025-01-01", completionDateOriginal = "2025-12-31",
        completionDateRevised = "", boardResolution = "", noticeToProceed = "", targetAccomplishment = 12.0
    )

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
                """"prev":[null,null,null],"period":[null,104000,5.2],"toDate":[null,504000,25.21],"balance":[null,null],"equiv":[null,null,null]}]""",
            ProjectAbstracts.rowsJson(rows.subList(7, 8))
        )
        // A row the website saved; "equiv" is the last key.
        val saved = """[{"kind":"item","no":"2.1.1","name":"Mobilization/Demobilization","qty":1,"unit":"l.s.","unitCost":400000,"amount":400000,""" +
            """"prev":[0,0,0],"period":[0.25,100000,25],"toDate":[0.25,100000,25],"balance":[0.75,300000],"equiv":[0,342.5,1027.5]}]"""
        assertEquals(listOf(item), ProjectAbstracts.rows(saved))
        // One saved before the equivalent-units columns: none.
        assertEquals(listOf(item.copy(equiv = none3)), ProjectAbstracts.rows(saved.replace(""","equiv":[0,342.5,1027.5]""", "")))
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
        // The date is compared as it is saved, as the website does.
        assertNull(ProjectAbstracts.inWeek(listOf(friday.copy(asOf = " 2025-03-14 ")), "Mar 8, 2025 - Mar 14, 2025"))
        // Its parts fill the three lines; a line it has no part for is 0.
        assertEquals(listOf(50.0, 25.21, 1.0), ProjectAbstracts.lineActuals(friday))
        assertEquals(listOf(0.0, 25.21, 0.0), ProjectAbstracts.lineActuals(friday.copy(rowsJson = ProjectAbstracts.rowsJson(rows.subList(2, 8)))))
        // Two parts of one line are averaged by their amounts, at 2 decimals as the website's "24.74": (25.21 x 2 + 20.0149 x 0.2) / 2.2 = 24.7377...
        val two = rows.subList(2, 8) + listOf(row("part", "4", "SITE GRADING"), row("subtotal", "", "Subtotal (Site Grading)", 200000.0, listOf(null, null, 20.0149)))
        assertEquals(listOf(0.0, 24.74, 0.0), ProjectAbstracts.lineActuals(friday.copy(rowsJson = ProjectAbstracts.rowsJson(two))))
        // Two parts averaged to 25.215 (stored just under it) read 25.21 as on the website; an overrun part reads 100.
        val tie = listOf(row("part", "2", "LAND DEVELOPMENT"), row("subtotal", "", "Subtotal", 300000.0, listOf(null, null, 25.21)),
            row("part", "4", "SITE GRADING"), row("subtotal", "", "Subtotal", 100000.0, listOf(null, null, 25.23)))
        assertEquals(listOf(0.0, 25.21, 0.0), ProjectAbstracts.lineActuals(friday.copy(rowsJson = ProjectAbstracts.rowsJson(tie))))
        assertEquals(listOf(0.0, 100.0, 0.0), ProjectAbstracts.lineActuals(friday.copy(rowsJson = ProjectAbstracts.rowsJson(listOf(tie[0], row("subtotal", "", "Subtotal", 1000.0, listOf(null, null, 100.5)))))))
    }

    // The same figures as web/logic.test.mjs "uploaded abstracts: target, actual and advance/slippage of the three lines and overall".
    @Test fun targetActualAndAdvanceOrSlippageOfTheThreeLinesAndOverall() {
        val uploaded = abstract("2025-03-14")
        // The approved bar chart on March 14, 2025 (contract day 73): roads 73% planned, housing 23%, overall 53%; it has no land acquisition.
        val charted = project.copy(barChartJson = """[{"effective":"","items":[{"name":"Roads","amount":60,"bars":[[1,100]]},{"name":"Housing units","amount":40,"bars":[[51,150]]}]}]""")
        assertEquals(
            listOf(
                ProjectAbstracts.Standing("Land Acquisition", 0.0, 50.0, 50.0),
                ProjectAbstracts.Standing("Land Development", 73.0, 25.21, -47.79),
                ProjectAbstracts.Standing("Housing Construction", 23.0, 1.0, -22.0),
                ProjectAbstracts.Standing("Overall", 53.0, 10.73, -42.27),
            ),
            ProjectAbstracts.standing(charted, emptyList<PendingDocument>(), uploaded)
        )
        // Without a chart a line has no target; the overall target is the project's stored one.
        assertEquals(
            listOf(
                ProjectAbstracts.Standing("Land Acquisition", null, 50.0, null),
                ProjectAbstracts.Standing("Land Development", null, 25.21, null),
                ProjectAbstracts.Standing("Housing Construction", null, 1.0, null),
                ProjectAbstracts.Standing("Overall", 12.0, 10.73, -1.27),
            ),
            ProjectAbstracts.standing(project, emptyList(), uploaded)
        )
        assertEquals(listOf("+4.66%", "-7.58%", "0.00%", "-"), listOf(4.66, -7.58, 0.0, null).map(ProjectAbstracts::diffText))
    }

    // The same figures as web/logic.test.mjs: the billing abstract's part amounts and where its land development starts.
    @Test fun theLinesContractAndThisPeriodAmountsAreTheAbstractsOwn() {
        val uploaded = abstract("2025-03-14")
        assertEquals(listOf(1000000.0, 2000000.0, 7000000.0), ProjectAbstracts.lineAmounts(uploaded))
        assertEquals(listOf(0.0, 104000.0, 0.0), ProjectAbstracts.periodAmounts(uploaded)) // a part's own subtotal, not its groups'
        assertEquals(listOf(0.0, 0.0, 0.0), ProjectAbstracts.lineAmounts(uploaded.copy(rowsJson = "")))
        assertEquals(listOf(0.0, 0.0, 0.0), ProjectAbstracts.periodAmounts(uploaded.copy(rowsJson = "")))
    }

    @Test fun theProjectsActualIsTheLatestAbstractsThenTheLatestWeeklyReports() {
        val reports = listOf(weekly("Mar 1, 2025 - Mar 7, 2025", 9.0), weekly("Mar 8, 2025 - Mar 14, 2025", 10.74))
        assertEquals(15.0, ProjectAbstracts.projectActualAfter(listOf(abstract("2025-03-14"), abstract("2025-04-11", 15.0)), reports, 3.0), 0.0)
        assertEquals(10.74, ProjectAbstracts.projectActualAfter(emptyList(), reports, 3.0), 0.0) // the last abstract was deleted
        assertEquals(3.0, ProjectAbstracts.projectActualAfter(emptyList(), emptyList(), 3.0), 0.0)
    }

    @Test fun aReadWorkbookBecomesTheRecord() {
        val parts = AbstractExcel.parts(rows)
        val found = AbstractExcel.Abstract(LocalDate.of(2025, 3, 14), parts, 10.73, false, rows, 100.0)
        val record = ProjectAbstracts.record(7, found, "abstract.xlsx", "Engr. Sample")!!
        assertEquals(ProjectAbstract(projectId = 7, asOf = "2025-03-14", overallPct = 10.73, rowsJson = ProjectAbstracts.rowsJson(rows), fileName = "abstract.xlsx", uploadedBy = "Engr. Sample", equivUnits = 100.0, uuid = record.uuid, updatedAt = record.updatedAt), record)
        assertEquals(0.0, ProjectAbstracts.record(7, found.copy(equivUnits = 0.0), "", "")!!.equivUnits, 0.0) // a sheet without the equivalent-units columns
        assertEquals("Mar 14, 2025: overall 10.73%", ProjectAbstracts.summary(record))
        // Without a grand total row: the parts' % by their contract amounts, 50 x 1 + 25.21 x 2 + 1 x 7 over 10.
        assertEquals(10.74, ProjectAbstracts.record(7, found.copy(overallPct = null), "", "")!!.overallPct, 0.0)
        assertEquals(0.0, ProjectAbstracts.record(7, found.copy(overallPct = null, parts = parts.map { it.copy(amount = 0.0) }), "", "")!!.overallPct, 0.0)
        assertNull(ProjectAbstracts.record(7, found.copy(asOf = null), "", "")) // nothing to file it under
    }

    @Test fun theFullTableHasEveryColumnOfTheSheet() {
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
        assertTrue(!html.contains("EQUIVALENT")) // a record without equivalent units: the table as before

        // With the record's equivalent units: three more columns after BALANCE, 2 decimals at most, blank when empty.
        val wide = BlueBookForms.actualAbstract(project, abstract("2025-03-14").copy(equivUnits = 1370.0))
        fun wideHas(vararg cells: String) = assertTrue(cells.joinToString(), Regex(cells.joinToString("<td[^>]*") { ">" + Regex.escape(it) + "</td>" }).containsMatchIn(wide))
        assertTrue(wide.contains("<th colspan='2' rowspan='2'>BALANCE</th><th colspan='3' rowspan='2'>EQUIVALENT TO 1,370 UNITS</th></tr>"))
        assertTrue(wide.contains("<th>QTY</th><th>AMOUNT</th><th>PREVIOUS</th><th>TO DATE</th><th>BALANCE</th></tr>"))
        wideHas("0.75", "300,000.00", "0", "342.5", "1,027.5")
        wideHas("GRAND TOTAL", "", "", "", "10,000,000.00", "", "", "", "", "", "", "", "1,074,000.00", "10.73%", "", "", "", "", "")
        assertTrue(BlueBookForms.actualAbstract(project, abstract("2025-03-14").copy(equivUnits = 370.5)).contains("EQUIVALENT TO 370.5 UNITS"))
    }
}
