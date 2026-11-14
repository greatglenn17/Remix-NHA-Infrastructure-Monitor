package com.example

import com.example.data.model.Project
import com.example.data.model.ProjectAbstract
import com.example.data.model.ProjectPayment
import com.example.data.model.WeeklyReport
import com.example.data.model.billedUnits
import com.example.data.model.unitsText
import com.example.util.AbstractExcel
import com.example.util.BillingAbstract
import com.example.util.BlueBookForms
import com.example.util.ProjectAbstracts
import com.example.util.Signatories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The figures of web/logic.test.mjs "abstracts: ..." and web/sections.test.mjs "billing abstract: ...".
@RunWith(RobolectricTestRunner::class)
class BillingAbstractTest {
    private val project = Project(
        name = "P", location = "", implementingOffice = "", contractor = "",
        scopeOfWork = """[{"name":"Land Acquisition","amount":1000000,"weightPct":10},{"name":"Land Development","amount":2000000,"weightPct":20},
            {"name":"Construction of Housing Units","amount":7000000,"weightPct":70}]""",
        contractCostOriginal = 10_000_000.0, contractCostRevised = 10_000_000.0, contractDurationDays = 360,
        dateStarted = "2025-01-06", completionDateOriginal = "2025-12-31", completionDateRevised = "",
        boardResolution = "", noticeToProceed = "", numberOfUnits = "100 units", targetAccomplishment = 12.0, actualAccomplishment = 10.74
    )

    private fun weekly(week: String, overall: Double, a: Double, b: Double, c: Double) = WeeklyReport(
        projectId = 1, reportingWeek = week, daysElapsed = 0, remainingDays = 0,
        targetAccomplishmentPct = 0.0, actualAccomplishmentPct = overall, manpowerJson = "[]", equipmentJson = "[]",
        activitiesJson = "[]", issuesJson = "[]", documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = "",
        accomplishmentItemsJson = listOf("Land Acquisition" to a, "Land Development" to b, "Housing Construction" to c)
            .joinToString(",", "[", "]") { (name, actual) -> """{"itemDescription":"$name","targetPct":0,"actualPct":$actual,"weightPct":10}""" }
    )

    // Newest first, as the lists come; the latest is the one of the latest week, whenever it was typed.
    private val reports = listOf(weekly("Mar 1, 2025 - Mar 7, 2025", 9.0, 50.0, 20.0, 0.0), weekly("Mar 8, 2025 - Mar 14, 2025", 10.74, 50.0, 25.21, 1.0))

    private fun billing(id: Long, type: String, gross: Double, date: String, units: Int = 0, unitPrice: Double = 0.0, period: String = "") = ProjectPayment(
        id = id, projectId = 1, name = "Billing $id", date = date, periodCovered = period, grossAmount = gross, percentage = 0.0,
        balanceAmount = 0.0, balancePercentage = 0.0, billingType = type, units = units, unitPrice = unitPrice
    )

    private val payments = listOf(
        billing(1, "Land acquisition", 400_000.0, "2025-02-01", units = 40, unitPrice = 10_000.0),
        billing(2, "", 1_500_000.0, "2025-02-02"), // an advance payment: no type, so not in the abstract and not counted
        billing(3, "Land development", 300_000.0, "2025-03-01", unitPrice = 20_000.0, period = "February 1 - 28, 2025")
    )

    // The rows of web/logic.test.mjs ABSTRACT_ROWS: each part's contract amount, % to date and this-period amount on its own subtotal.
    private fun row(kind: String, no: String, name: String, amount: Double? = null, pct: Double? = null, period: Double? = null) = AbstractExcel.Row(
        kind, no, name, null, "", null, amount, listOf(null, null, null), listOf(null, period, null), listOf(null, null, pct), listOf(null, null)
    )
    private val rows = listOf(
        row("part", "1", "LAND ACQUISITION COST"), row("subtotal", "", "Subtotal (Land Acquisition Cost)", 1_000_000.0, 50.0),
        row("part", "2", "LAND DEVELOPMENT"), row("group", "2.2", "ROADWORKS"), row("item", "2.2.1", "Road Excavation", 1_600_000.0, 25.25),
        row("subtotal", "", "Subtotal (Roadworks)", 1_600_000.0, 25.25, 80_000.0), row("subtotal", "", "Subtotal (Land Development)", 2_000_000.0, 25.21, 104_000.0),
        row("part", "3", "HOUSING CONSTRUCTION"), row("subtotal", "", "Subtotal (Housing Construction)", 7_000_000.0, 1.0),
        row("total", "", "GRAND TOTAL", 10_000_000.0, 10.73)
    )
    private val uploaded = ProjectAbstract(projectId = 1, asOf = "2025-03-14", overallPct = 10.73, rowsJson = ProjectAbstracts.rowsJson(rows))

    private fun ahead(new: ProjectPayment, old: ProjectPayment? = null, weekly: List<WeeklyReport> = reports) =
        BillingAbstract.aheadErrors(project, emptyList(), payments, weekly, new, old)

    @Test fun whatIsBilledNeverPassesTheActual() {
        val form = billing(0, "Land development", 204_200.0, "2025-03-15", unitPrice = 20_000.0)
        assertEquals(emptyList<String>(), ahead(form)) // 504,200 of 2,000,000 is 25.21%, the actual: equal is allowed
        assertEquals(
            listOf("Land Development: billed to date 25.22% would pass the actual 25.21% of Mar 8, 2025 - Mar 14, 2025."),
            ahead(form.copy(grossAmount = 204_400.0))
        )
        assertEquals(
            listOf("Housing Construction: billed to date 1.14% would pass the actual 1.00% of Mar 8, 2025 - Mar 14, 2025."),
            ahead(form.copy(billingType = "M1 - Housing unit", units = 1, unitPrice = 80_000.0, grossAmount = 80_000.0))
        )
        assertEquals(emptyList<String>(), ahead(form.copy(billingType = "", grossAmount = 9_000_000.0)))
        assertEquals(
            listOf("Overall: billed to date 8.00% would pass the actual 7.50% of Mar 8, 2025 - Mar 14, 2025."),
            ahead(form.copy(billingType = "Land acquisition", units = 1, unitPrice = 100_000.0, grossAmount = 100_000.0), null, listOf(reports[1].copy(actualAccomplishmentPct = 7.5)))
        )
        assertEquals(listOf("Land Development: billed to date 25.21% would pass the actual 0.00%."), ahead(form, null, emptyList()))
        // An edit is checked only when it raises the billing, so one already saved can still be corrected.
        assertEquals(emptyList<String>(), ahead(payments[2], payments[2], emptyList()))
        assertEquals(
            listOf("Land Development: billed to date 25.22% would pass the actual 25.21% of Mar 8, 2025 - Mar 14, 2025."),
            ahead(payments[2].copy(grossAmount = 504_400.0), payments[2])
        )
        // An old billing saved as lots x price: its gross at 2 decimals parses a hair above the saved one, which is not a raise.
        assertEquals(emptyList<String>(), ahead(payments[2].copy(grossAmount = 35_219_729.88), payments[2].copy(grossAmount = 81 * 434_811.48), emptyList()))
    }

    // A project with an uploaded abstract (Actual Accomplishment tab) is checked against the latest one, not the weekly reports.
    @Test fun withAnUploadedAbstractTheActualIsTheAbstracts() {
        val abstract = uploaded
        fun ahead(new: ProjectPayment, abstracts: List<ProjectAbstract> = listOf(abstract), weekly: List<WeeklyReport> = emptyList()) =
            BillingAbstract.aheadErrors(project, emptyList(), payments, weekly, new, null, abstracts)
        val form = billing(0, "Land development", 204_200.0, "2025-03-15", unitPrice = 20_000.0)
        val passes = listOf("Land Development: billed to date 25.22% would pass the actual 25.21% of the abstract as of Mar 14, 2025.")
        assertEquals(emptyList<String>(), ahead(form))
        assertEquals(passes, ahead(form.copy(grossAmount = 204_400.0)))
        assertEquals(
            listOf("Overall: billed to date 8.00% would pass the actual 7.50% of the abstract as of Mar 14, 2025."),
            ahead(form.copy(billingType = "Land acquisition", units = 1, unitPrice = 100_000.0, grossAmount = 100_000.0), listOf(abstract.copy(overallPct = 7.5)))
        )
        // With weekly reports as well, the abstract wins; and of two abstracts, the later one.
        assertEquals(passes, ahead(form.copy(grossAmount = 204_400.0), weekly = reports))
        val later = abstract.copy(asOf = "2025-04-11", overallPct = 0.0, rowsJson = "[]")
        assertEquals(
            listOf(
                "Land Development: billed to date 25.21% would pass the actual 0.00% of the abstract as of Apr 11, 2025.",
                "Overall: billed to date 9.04% would pass the actual 0.00% of the abstract as of Apr 11, 2025."
            ),
            ahead(form, listOf(abstract, later), reports)
        )
        assertEquals("Upload the newer abstract first, or lower this billing.", BillingAbstract.aheadHint(listOf(abstract)))
        assertEquals("Save the newer weekly report first, or lower this billing.", BillingAbstract.aheadHint(emptyList()))
    }

    @Test fun latestWeekPartAmountsUnitsAndCarriedActuals() {
        assertEquals("Mar 8, 2025 - Mar 14, 2025", BillingAbstract.latestWeekly(reports)?.reportingWeek)
        assertNull(BillingAbstract.latestWeekly(emptyList()))
        assertEquals(mapOf("Land Acquisition" to 50.0, "Land Development" to 25.21, "Housing Construction" to 1.0), BillingAbstract.carriedActuals(reports))
        assertEquals(emptyMap<String, Double>(), BillingAbstract.carriedActuals(emptyList()))
        assertEquals(listOf(1_000_000.0, 2_000_000.0, 7_000_000.0), BillingAbstract.partAmounts(project, emptyList(), java.time.LocalDate.of(2025, 3, 14)))
        assertEquals(listOf(0.0, 0.0, 0.0), BillingAbstract.partAmounts(project.copy(scopeOfWork = ""), emptyList(), null))
        assertEquals(100, BillingAbstract.units(project))
        assertEquals(42, BillingAbstract.units(project.copy(numberOfUnits = ""), 42))
        assertEquals(1, BillingAbstract.units(project.copy(numberOfUnits = "")))
        // Land development is billed by amount: its lots are the gross over the amount per lot.
        assertEquals(15.0, payments[2].billedUnits, 0.0)
        assertEquals(40.0, payments[0].billedUnits, 0.0)
        assertEquals("15.5", unitsText(payments[2].copy(grossAmount = 310_000.0).billedUnits))
        assertEquals("40", unitsText(40.0))
    }

    @Test fun theBillingAbstractIsMadeFromTheUploadedAbstract() {
        // Previous: the saved billings dated on or before the As-of date (March 14); the one of April 1 is not in it, the one of the day is.
        val saved = payments + billing(4, "Land development", 1.0, "2025-04-01") + billing(5, "M1 - Housing unit", 56_000.0, " 2025-03-14 ") +
            billing(6, "Land development", 9.0, "")
        // Typed for this billing: 5 lots, the abstract's 104,000 of land development, 2 units at Milestone 1 and 1 at Milestone 2, of 100 units.
        val period = BillingAbstract.Period("March 1 - 14, 2025", lots = 5.0, development = ProjectAbstracts.periodAmounts(uploaded)[1], m1 = 2.0, m2 = 1.0)
        val abstract = BillingAbstract.of(project, emptyList(), saved, uploaded, period, 100)
        assertEquals(12.0, abstract.targetPct, 0.0) // no bar chart: the project's stored target
        assertEquals(listOf("Land Acquisition Cost" to null, "Land Development" to null, "Housing Construction" to ("3" to "HOUSING CONSTRUCTION")), abstract.parts.map { it.name to it.heading })
        assertEquals(
            listOf(
                BillingAbstract.Line("1", "LAND ACQUISITION COST", "LOT", "Land acquisition", 1_000_000.0, 400_000.0, 50_000.0),
                BillingAbstract.Line("2", "LAND DEVELOPMENT", "LOT", "Land development", 2_000_000.0, 300_000.0, 104_000.0),
                BillingAbstract.Line("3.1", "MILESTONE 1 (80%)", "UNIT", "M1 - Housing unit", 5_600_000.0, 56_000.0, 112_000.0),
                BillingAbstract.Line("3.2", "MILESTONE 2 (20%)", "UNIT", "M2 - Housing unit", 1_400_000.0, 0.0, 14_000.0),
            ),
            abstract.parts.flatMap { it.lines }
        )
        // A negative figure is 0; a quantity's amount is at 2 decimals (1 of 3 units of 1,000,000 is 333,333.33).
        assertEquals(
            listOf(333_333.33, 0.0, 0.0, 0.0),
            BillingAbstract.of(project, emptyList(), emptyList(), uploaded, BillingAbstract.Period(lots = 1.0, development = -5.0, m1 = -2.0), 3).parts.flatMap { it.lines }.map { it.thisPeriod }
        )
        // The target is the bar chart's plan on the As-of date when the project has one.
        val charted = project.copy(dateStarted = "2025-01-01", barChartJson = """[{"effective":"","items":[{"name":"Roads","amount":60,"bars":[[1,100]]},{"name":"Housing units","amount":40,"bars":[[51,150]]}]}]""")
        assertEquals(53.0, BillingAbstract.of(charted, emptyList(), emptyList(), uploaded, period, 100).targetPct, 0.0)
        // An abstract without housing has no housing lines.
        val land = uploaded.copy(rowsJson = ProjectAbstracts.rowsJson(rows.subList(2, 7)))
        assertEquals(listOf("Land Development"), BillingAbstract.of(project, emptyList(), payments, land, period, 100).parts.map { it.name })
    }

    @Test fun printsEachLineBeforeInThisBillingAndToDate() {
        // The uploaded abstract gives the part amounts; the billing of March 15 is after its As-of date, so it is not in "previous".
        val abstract = uploaded.copy(equivUnits = 100.0, rowsJson = ProjectAbstracts.rowsJson(rows.map { if (it.kind == "subtotal") it.copy(equiv = listOf(1.0, 2.0, 3.0)) else it }))
        val saved = payments.dropLast(1) + billing(9, "Land development", 99.0, "2025-03-15")
        fun sheet(period: BillingAbstract.Period) = BlueBookForms.billingAbstract(
            project, emptyList(), saved, abstract, period, 100, Signatories("Juan", "Engineer", "", "District Manager", "", "Regional Manager")
        )
        val html = sheet(BillingAbstract.Period(" February 1 - 28, 2025 ", development = 310_000.0))
        fun has(vararg cells: String, page: String = html) =
            assertTrue(cells.joinToString(), Regex(cells.joinToString("<td[^>]*") { ">" + Regex.escape(it) + "</td>" }).containsMatchIn(page))
        assertTrue(html.contains("<div class='period'>Period covered: February 1 - 28, 2025</div>"))
        assertTrue(sheet(BillingAbstract.Period(" ")).contains("<div class='period'>As of March 14, 2025</div>")) // no period typed
        assertTrue(!html.contains("equivalent", ignoreCase = true)) // the billing format has no equivalent-units columns
        // Land acquisition: 40 of 100 lots billed before, nothing in this billing.
        has("1", "LAND ACQUISITION COST", "100", "LOT", "10,000.00", "1,000,000.00", "40", "400,000.00", "40.00%", "0", "0.00", "0.00%", "40", "400,000.00", "40.00%", "60", "600,000.00")
        // Land development is typed as an amount: 310,000 at 20,000 a lot is 15.5 lots.
        has("2", "LAND DEVELOPMENT", "100", "LOT", "20,000.00", "2,000,000.00", "0", "0.00", "0.00%", "15.5", "310,000.00", "15.50%", "15.5", "310,000.00", "15.50%", "84.5", "1,690,000.00")
        has("3.1", "MILESTONE 1 (80%)", "100", "UNIT", "56,000.00", "5,600,000.00")
        // Typed quantities become amounts: 2 units at Milestone 2 are 28,000.
        has("3.2", "MILESTONE 2 (20%)", "100", "UNIT", "14,000.00", "1,400,000.00", "0", "0.00", "0.00%", "2", "28,000.00", "2.00%", page = sheet(BillingAbstract.Period(m2 = 2.0)))
        has("Subtotal (Housing Construction)", "", "", "70,000.00", "7,000,000.00")
        // The advance payment has no type, so it is not in the abstract: 710,000 of 10,000,000 billed to date.
        has("GRAND TOTAL", "", "", "100,000.00", "10,000,000.00", "", "400,000.00", "4.00%", "", "310,000.00", "3.10%", "", "710,000.00", "7.10%", "", "9,290,000.00")
        assertTrue(html.contains("TARGET ACCOMPLISHMENT</td><td class='pc'>:</td><td>12.00%"))
        assertTrue(html.contains("ACTUAL ACCOMPLISHMENT</td><td class='pc'>:</td><td>7.10%"))
        assertTrue(html.contains("ADVANCE/SLIPPAGE</td><td class='pc'>:</td><td>-4.90%"))
        assertTrue(html.contains("Approved by:"))
    }
}
