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
        // The sheet ends at BALANCE: a record's equivalent-units columns are not shown.
        val wide = BlueBookForms.actualAbstract(project, abstract("2025-03-14").copy(equivUnits = 1370.0))
        assertTrue(!wide.contains("EQUIVALENT"))
        assertTrue(wide.contains("<th colspan='2' rowspan='2'>BALANCE</th></tr>"))
        assertTrue(wide.contains(">300,000.00</td></tr>")) // 2.1.1's row ends at its balance, though it carries equivalent units
    }

    // The made-up project of web/logic.test.mjs (NEXT_ROWS), figure for figure: Land Acquisition is a part that is its own pay line.
    private fun line(kind: String, no: String, name: String, qty: Double, unit: String, unitCost: Double, toDate: List<Double?>) = AbstractExcel.Row(
        kind, no, name, qty, unit, unitCost, Math.round(qty * unitCost * 100) / 100.0, listOf(0.0, 0.0, 0.0), listOf(null, 0.0, 0.0), toDate, listOf(0.0, 0.0), listOf(0.0, 0.0, 0.0)
    )
    private fun sum(kind: String, name: String, amount: Double, done: Double, pct: Double, shown: Boolean) = AbstractExcel.Row(
        kind, "", name, null, "", null, amount, listOf(null, 0.0, if (shown) 0.0 else null), listOf(null, 0.0, if (shown) 0.0 else null), listOf(null, done, pct), none2
    )
    private val nextRows = listOf(
        line("part", "1", "LAND ACQUISITION COST", 520.0, "LOT", 65250.5, listOf(420.0, 27405210.0, 80.77)),
        sum("subtotal", "Subtotal (Land Acquisition Cost)", 33930260.0, 27405210.0, 80.77, true),
        row("part", "2", "LAND DEVELOPMENT"), row("group", "2.1", "GENERAL REQUIREMENTS"),
        line("item", "2.1.1", "Mobilization/Demobilization", 1.0, "LOT", 2150000.75, listOf(0.6, 1290000.45, 60.0)),
        line("item", "2.1.2", "Permits & Licences", 1.0, "P.S.", 350000.0, listOf(1.0, 350000.0, 100.0)),
        sum("subtotal", "Subtotal (General Requirements)", 2500000.75, 1640000.45, 65.6, false),
        row("group", "2.2", "DRAINAGE WORKS"),
        line("item", "2.2.1", "Catch basin", 96.0, "unit", 18500.0, listOf(36.0, 666000.0, 37.5)),
        sum("subtotal", "Subtotal (Drainage Works)", 1776000.0, 666000.0, 37.5, false),
        sum("subtotal", "Subtotal (Land Development)", 4276000.75, 2306000.45, 53.93, true),
        sum("total", "GRAND TOTAL", 38206260.75, 29711210.45, 77.77, true),
    )
    private fun made(asOf: String, rows: List<AbstractExcel.Row>, id: Long = 0) =
        ProjectAbstract(id = id, projectId = 7, asOf = asOf, overallPct = 77.77, rowsJson = ProjectAbstracts.rowsJson(rows), fileName = "a.xlsx", uploadedBy = "x", equivUnits = 520.0)

    @Test fun next_abstract_previous_is_the_to_date_and_the_typed_quantity_gives_the_rest() {
        assertEquals(mapOf(1 to listOf(0), 6 to listOf(4, 5), 9 to listOf(8), 10 to listOf(4, 5, 8), 11 to listOf(0, 4, 5, 8)), ProjectAbstracts.cover(nextRows))
        val next = ProjectAbstracts.nextRows(nextRows, mapOf(0 to 20.0, 4 to 0.25, 8 to 10.5), 520.0)
        fun of(i: Int) = next[i].run { listOf(prev, period, toDate, balance, equiv) }
        assertEquals(listOf(listOf(420.0, 27405210.0, 80.77), listOf(20.0, 1305010.0, 3.85), listOf(440.0, 28710220.0, 84.62), listOf(80.0, 5220040.0), listOf(420.0, 440.0, 80.0)), of(0))
        assertEquals(listOf(listOf(null, 27405210.0, 80.77), listOf(null, 1305010.0, 3.85), listOf(null, 28710220.0, 84.62), none2, none3), of(1))
        assertEquals(listOf(listOf(0.6, 1290000.45, 60.0), listOf(0.25, 537500.19, 25.0), listOf(0.85, 1827500.64, 85.0), listOf(0.15, 322500.11), listOf(312.0, 442.0, 78.0)), of(4))
        assertEquals(listOf(listOf(1.0, 350000.0, 100.0), listOf(null, 0.0, 0.0), listOf(1.0, 350000.0, 100.0), listOf(0.0, 0.0), listOf(520.0, 520.0, 0.0)), of(5))
        assertEquals(listOf(listOf(null, 1640000.45, null), listOf(null, 537500.19, null), listOf(null, 2177500.64, 87.1), none2, none3), of(6))
        assertEquals(listOf(listOf(36.0, 666000.0, 37.5), listOf(10.5, 194250.0, 10.94), listOf(46.5, 860250.0, 48.44), listOf(49.5, 915750.0), listOf(195.0, 251.88, 268.12)), of(8))
        assertEquals(listOf(null, 731750.19, 17.11), next[10].period)
        assertEquals(listOf(null, 3037750.64, 71.04), next[10].toDate)
        assertEquals(listOf(listOf(null, 29711210.45, 77.77), listOf(null, 2036760.19, 5.33), listOf(null, 31747970.64, 83.1), none2, none3), of(11))
        // What is copied from the project's abstract stays as it is: the works, quantities, units, unit costs and amounts.
        assertEquals(nextRows.map { listOf(it.kind, it.no, it.name, it.qty, it.unit, it.unitCost, it.amount) }, next.map { listOf(it.kind, it.no, it.name, it.qty, it.unit, it.unitCost, it.amount) })
        assertEquals(none3, ProjectAbstracts.nextRows(nextRows, emptyMap())[0].equiv)
        assertEquals(listOf(520.0, 33930260.0, 100.0), ProjectAbstracts.nextRows(next, mapOf(0 to 80.0), 520.0)[0].toDate)

        val record = ProjectAbstracts.nextRecord(made("2026-11-13", nextRows), next, "2026-11-20", "Engr. Phone")
        assertEquals(listOf(7L, "2026-11-20", 83.1, "", "Engr. Phone", 520.0), record.run { listOf(projectId, asOf, overallPct, fileName, uploadedBy, equivUnits) })
        assertEquals(next, ProjectAbstracts.rows(record.rowsJson))

        assertEquals(emptyList<String>(), ProjectAbstracts.nextErrors(nextRows, mapOf(0 to 20.0, 4 to 0.25)))
        assertEquals(
            listOf("1 LAND ACQUISITION COST: to date 521 would pass the quantity 520.", "2.1.1 Mobilization/Demobilization: to date would go below zero.", "2.1.2 Permits & Licences: this period must be a number."),
            ProjectAbstracts.nextErrors(nextRows, mapOf(0 to 101.0, 4 to -0.7, 5 to Double.NaN, 8 to 60.0))
        )
        assertEquals(listOf("2.2.1 Catch basin: to date 96.5 would pass the quantity 96."), ProjectAbstracts.nextErrors(nextRows, mapOf(8 to 60.5)))
        assertEquals("12,550", ProjectAbstracts.quantityText(12550.0))
        assertEquals("0.0313", ProjectAbstracts.quantityText(0.03125))
        // A line already over its quantity is carried; only more work on it is refused.
        val over = nextRows.mapIndexed { i, r -> if (i == 8) r.copy(toDate = listOf(97.0, 1794500.0, 101.04)) else r }
        assertEquals(emptyList<String>(), ProjectAbstracts.nextErrors(over, emptyMap()))
        assertEquals(emptyList<String>(), ProjectAbstracts.nextErrors(over, mapOf(8 to -1.0)))
        assertEquals(listOf("2.2.1 Catch basin: to date 98 would pass the quantity 96."), ProjectAbstracts.nextErrors(over, mapOf(8 to 1.0)))
        // A line done a third at a time ends at 100.00: its to date % is its to date amount over its amount.
        val third = listOf(nextRows[8].copy(qty = 3.0, unitCost = 1000.0, amount = 3000.0, toDate = listOf(0.0, 0.0, 0.0)), nextRows[11].copy(amount = 3000.0, toDate = listOf(null, 0.0, 0.0)))
        val thrice = (1..3).fold(third) { rows, _ -> ProjectAbstracts.nextRows(rows, mapOf(0 to 1.0)) }
        assertEquals(listOf(listOf(3.0, 3000.0, 100.0), listOf(0.0, 0.0), listOf(null, 3000.0, 100.0)), listOf(thrice[0].toDate, thrice[0].balance, thrice[1].toDate))
    }

    @Test fun next_abstract_continues_the_latest_one_dated_before_it() {
        val first = made("2026-11-13", nextRows, 1)
        val second = made("2026-11-20", ProjectAbstracts.nextRows(nextRows, mapOf(0 to 20.0, 8 to 10.5), 520.0), 2).copy(overallPct = 81.69)
        assertEquals(ProjectAbstracts.NextSource(first, null, ""), ProjectAbstracts.nextSource(listOf(first), "2026-11-20"))
        assertEquals("Pick the \"As of\" date.", ProjectAbstracts.nextSource(listOf(first), "").error)
        assertEquals("The date must be after Nov 13, 2026, the first abstract's.", ProjectAbstracts.nextSource(listOf(first), "2026-11-13").error)
        assertEquals("The date must not be before the latest abstract's, Nov 20, 2026.", ProjectAbstracts.nextSource(listOf(first, second), "2026-11-19").error)
        assertEquals(ProjectAbstracts.NextSource(first, second, ""), ProjectAbstracts.nextSource(listOf(first, second), "2026-11-20"))
        assertEquals(mapOf(0 to 20.0, 8 to 10.5), ProjectAbstracts.nextTyped(nextRows, second))
        assertEquals(emptyMap<Int, Double>(), ProjectAbstracts.nextTyped(nextRows, null))
        assertEquals(emptyMap<Int, Double>(), ProjectAbstracts.nextTyped(nextRows.drop(1), second))
        assertEquals(second, ProjectAbstracts.nextSource(listOf(first, second), "2026-11-27").source)
        val off = made("2026-11-13", nextRows.mapIndexed { i, r -> if (i == 9) r.copy(amount = 1800000.0) else r })
        assertEquals(
            "\"Subtotal (Drainage Works)\" does not add up from its items in the abstract as of Nov 13, 2026, so the next one cannot be made from it. Upload the next abstract instead.",
            ProjectAbstracts.nextSource(listOf(off), "2026-11-20").error
        )
        assertEquals(
            "There are no pay items with a grand total in the abstract as of Nov 13, 2026, so the next one cannot be made from it. Upload the next abstract instead.",
            ProjectAbstracts.nextProblem(made("2026-11-13", nextRows.take(10)))
        )
        assertEquals(
            "Overall to date 83.10%, from 77.77% as of Nov 13, 2026. Saving replaces the abstract already filed for this date.",
            ProjectAbstracts.nextNote(first, second, ProjectAbstracts.nextRows(nextRows, mapOf(0 to 20.0, 4 to 0.25, 8 to 10.5), 520.0))
        )
    }
}
