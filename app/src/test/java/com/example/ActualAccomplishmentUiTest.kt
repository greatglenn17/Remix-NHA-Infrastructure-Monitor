package com.example

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.text.TextLayoutResult
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.data.model.ProjectAbstract
import com.example.data.model.WeeklyReport
import com.example.ui.detail.ProjectSection
import com.example.ui.detail.ProjectSectionTabs
import com.example.ui.detail.sections.ActualAccomplishmentSection
import com.example.ui.dialogs.AddWeeklyReportDialog
import com.example.ui.dialogs.WeeklyDrafts
import com.example.ui.theme.NHATheme
import com.example.util.AbstractExcel
import com.example.util.BillingAbstract
import com.example.util.BlueBookForms
import com.example.util.ProjectAbstracts
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The Actual Accomplishment tab, and the weekly report form's Accomplishment Status with and without an abstract and a bar chart.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActualAccomplishmentUiTest {
    @get:Rule val rule = createComposeRule()

    private val project = Project(
        id = 1, name = "P", location = "L", implementingOffice = "O", contractor = "C",
        scopeOfWork = """[{"name":"Land Acquisition","amount":1000000,"weightPct":10},{"name":"Land Development","amount":2000000,"weightPct":20},
            {"name":"Construction of Housing Units","amount":7000000,"weightPct":70}]""",
        contractCostOriginal = 10_000_000.0, contractCostRevised = 0.0, contractDurationDays = 360,
        dateStarted = "2025-01-06", completionDateOriginal = "2025-12-31", completionDateRevised = "", targetAccomplishment = 12.0
    )

    // As the website saves it: Land Development 25.21%, Housing Construction 1%, no Land Acquisition part.
    private val abstract = ProjectAbstract(
        id = 1, projectId = 1, asOf = "2025-03-14", overallPct = 10.73, fileName = "abstract.xlsx", uploadedBy = "Engr. Sample",
        rowsJson = """[{"kind":"part","no":"2","name":"LAND DEVELOPMENT","qty":null,"unit":"","unitCost":null,"amount":null,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,null,null],"balance":[null,null]},
            {"kind":"item","no":"2.1.1","name":"Mobilization/Demobilization","qty":1,"unit":"l.s.","unitCost":400000,"amount":400000,"prev":[0,0,0],"period":[0.25,100000,25],"toDate":[0.25,100000,25],"balance":[0.75,300000]},
            {"kind":"subtotal","no":"","name":"Subtotal (Land Development)","qty":null,"unit":"","unitCost":null,"amount":2000000,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,504000,25.21],"balance":[null,null]},
            {"kind":"part","no":"3","name":"HOUSING CONSTRUCTION","qty":null,"unit":"","unitCost":null,"amount":null,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,null,null],"balance":[null,null]},
            {"kind":"subtotal","no":"","name":"Subtotal (Housing Construction)","qty":null,"unit":"","unitCost":null,"amount":7000000,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,70000,1],"balance":[null,null]},
            {"kind":"total","no":"","name":"GRAND TOTAL","qty":null,"unit":"","unitCost":null,"amount":10000000,"prev":[null,null,null],"period":[null,null,null],"toDate":[null,1074000,10.73],"balance":[null,null]}]"""
    )

    private val typed = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    @Config(qualifiers = "w360dp-h640dp")
    @Test fun allFiveSectionTabs_fitA360dpPhone() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ProjectSectionTabs(ProjectSection.ACTUAL) {} } }
        val cut = ProjectSection.entries.mapNotNull { s ->
            val layout = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(s.label, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(layout)
            // The label's own width against the width it was given.
            val text = layout.single()
            "${s.label}: ${text.multiParagraph.maxIntrinsicWidth}px in ${text.size.width}px".takeIf { text.multiParagraph.maxIntrinsicWidth > text.size.width }
        }
        assertEquals(emptyList<String>(), cut)
    }

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun tab_showsTheLatestAbstractWithItsPayItems_andPartsFold() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                ActualAccomplishmentSection(project, emptyList(), listOf(abstract.copy(id = 2, asOf = "2025-02-14", overallPct = 5.0, uuid = "older"), abstract), "Engr. Sample", {}, {}, {}, { _, _ -> })
            }
        }
        rule.onNodeWithTag("standing_Overall").assert(hasText("10.73%")) // the latest "As of" date opens first
        // Target, actual and advance/slippage by line; without a bar chart only the overall has a target, the project's.
        rule.onNodeWithTag("standing_Land Development").assert(hasText("-")).assert(hasText("25.21%"))
        rule.onNodeWithTag("standing_Overall").assert(hasText("12.00%")).assert(hasText("10.73%")).assert(hasText("-1.27%"))
        rule.onNodeWithText("abstract.xlsx").assertExists()
        rule.onNodeWithText("2  LAND DEVELOPMENT").assertExists()
        rule.onNodeWithText("Amount 2,000,000.00 · To date 25.21%").assertExists()
        // The pay items are a table of the sheet's columns, under their headings; an empty cell is blank.
        listOf("ITEM DESCRIPTION", "THIS PERIOD", "TO DATE").forEach { rule.onAllNodesWithText(it).onFirst().assertExists() } // each open part has its own headings
        rule.onAllNodesWithText("BALANCE").assertCountEquals(0) // the table ends at TO DATE, as on the website and the printed abstracts
        rule.onNodeWithTag("abstract_row_1").assert(hasText("2.1.1")).assert(hasText("Mobilization/Demobilization")).assert(hasText("l.s."))
            .assert(hasText("400,000.00")).assert(hasText("0.25")).assert(hasText("100,000.00")).assert(hasText("25.00%"))
        rule.onNodeWithTag("abstract_row_1").assert(!hasText("0.75")).assert(!hasText("300,000.00")) // its balance (0.75, 300,000.00) is not shown
        rule.onNodeWithTag("abstract_row_2").assert(hasText("Subtotal (Land Development)")).assert(hasText("2,000,000.00")).assert(hasText("504,000.00")).assert(hasText("25.21%"))
        rule.onNodeWithTag("abstract_part_0").performClick()
        rule.onNodeWithTag("abstract_row_1").assertDoesNotExist()
        rule.onNodeWithTag("abstract_row_2").assertDoesNotExist()
        rule.onNodeWithTag("abstract_row_5").assert(hasText("GRAND TOTAL")).assert(hasText("1,074,000.00")) // the grand total stays in sight
        rule.onNodeWithTag("upload_abstract").assertExists()
        rule.onNodeWithTag("delete_abstract").assertExists()
        // An earlier date can be picked.
        rule.onNodeWithTag("section_chip_Feb 14, 2025").performClick()
        rule.onNodeWithText("5.00%").assertExists()
    }

    @Test fun tab_withoutAnAbstract_saysSo_andViewersCannotUpload() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ActualAccomplishmentSection(project, emptyList(), emptyList(), "", null, null, {}, { _, _ -> }) } }
        rule.onNodeWithText("No abstract uploaded yet. Upload the Abstract of Physical Accomplishment of the actual work (the \"As of\" one), saved as an Excel workbook (.xlsx).").assertExists()
        rule.onNodeWithTag("upload_abstract").assertDoesNotExist()
    }

    // "Print billing abstract": this billing's figures are typed, land development in lots and not started from the abstract's own this-period amount.
    @Test fun tab_printsTheBillingAbstractFromTheTypedFigures() {
        val withPeriod = abstract.copy(rowsJson = abstract.rowsJson.replace(""""period":[null,null,null],"toDate":[null,504000,25.21]""", """"period":[null,104000,5.2],"toDate":[null,504000,25.21]"""))
        var printed: Pair<ProjectAbstract, BillingAbstract.Period>? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                ActualAccomplishmentSection(project, emptyList(), listOf(withPeriod), "", null, null, {}, { a, period -> printed = a to period }) // a viewer can print too
            }
        }
        rule.onNodeWithTag("actual_accomplishment").performScrollToNode(hasTestTag("print_billing_abstract")) // under the Weekly | Monthly chips it is below the small screen
        rule.onNodeWithTag("print_billing_abstract").performClick()
        rule.onNodeWithText("This billing").assertExists()
        rule.onNodeWithText("Previous is what the billings saved up to Mar 14, 2025 came to. Nothing typed here is saved.").assertExists()
        rule.onNodeWithTag("billing_lots").assertDoesNotExist() // the abstract has no land acquisition
        rule.onNodeWithTag("billing_development").assert(hasText("Land development (lots)")).assert(!hasText("104000.00"))
        rule.onNodeWithTag("billing_development").performTextReplacement("5.2")
        rule.onNodeWithTag("billing_covered").performTextReplacement("March 1 - 14, 2025")
        rule.onNodeWithTag("billing_m1").performTextReplacement("2")
        rule.onNodeWithTag("billing_m2").performTextReplacement("two") // unreadable: 0
        rule.onNodeWithTag("confirm_print_billing_abstract").performClick()
        assertEquals(withPeriod to BillingAbstract.Period("March 1 - 14, 2025", 0.0, 5.2, 2.0, 0.0), printed)
        rule.onNodeWithText("This billing").assertDoesNotExist()
    }

    // The made-up project of ProjectAbstractsTest (NEXT_ROWS of web/logic.test.mjs): Land Acquisition is a part that is its own pay line.
    private fun line(kind: String, no: String, name: String, qty: Double, unit: String, unitCost: Double, toDate: List<Double?>) = AbstractExcel.Row(
        kind, no, name, qty, unit, unitCost, Math.round(qty * unitCost * 100) / 100.0, listOf(0.0, 0.0, 0.0), listOf(null, 0.0, 0.0), toDate, listOf(0.0, 0.0), listOf(0.0, 0.0, 0.0)
    )
    private fun sum(kind: String, name: String, amount: Double, done: Double, pct: Double) =
        AbstractExcel.Row(kind, "", name, null, "", null, amount, listOf(null, 0.0, 0.0), listOf(null, 0.0, 0.0), listOf(null, done, pct), listOf(null, null))
    private fun heading(kind: String, no: String, name: String) =
        AbstractExcel.Row(kind, no, name, null, "", null, null, listOf(null, null, null), listOf(null, null, null), listOf(null, null, null), listOf(null, null))
    private val nextRows = listOf(
        line("part", "1", "LAND ACQUISITION COST", 520.0, "LOT", 65250.5, listOf(420.0, 27405210.0, 80.77)),
        sum("subtotal", "Subtotal (Land Acquisition Cost)", 33930260.0, 27405210.0, 80.77),
        heading("part", "2", "LAND DEVELOPMENT"), heading("group", "2.1", "GENERAL REQUIREMENTS"),
        line("item", "2.1.1", "Mobilization/Demobilization", 1.0, "LOT", 2150000.75, listOf(0.6, 1290000.45, 60.0)),
        line("item", "2.1.2", "Permits & Licences", 1.0, "P.S.", 350000.0, listOf(1.0, 350000.0, 100.0)),
        sum("subtotal", "Subtotal (General Requirements)", 2500000.75, 1640000.45, 65.6),
        heading("group", "2.2", "DRAINAGE WORKS"),
        line("item", "2.2.1", "Catch basin", 96.0, "unit", 18500.0, listOf(36.0, 666000.0, 37.5)),
        sum("subtotal", "Subtotal (Drainage Works)", 1776000.0, 666000.0, 37.5),
        sum("subtotal", "Subtotal (Land Development)", 4276000.75, 2306000.45, 53.93),
        sum("total", "GRAND TOTAL", 38206260.75, 29711210.45, 77.77),
    )
    private val first = ProjectAbstract(id = 1, projectId = 1, asOf = "2026-11-13", overallPct = 77.77, rowsJson = ProjectAbstracts.rowsJson(nextRows), fileName = "a.xlsx", uploadedBy = "x", equivUnits = 520.0)
    /** The abstract as of Nov 20, 2026 the form makes from [first] with these quantities. */
    private fun next(typed: Map<Int, Double>) = ProjectAbstracts.nextRecord(first, ProjectAbstracts.nextRows(nextRows, typed, 520.0), "2026-11-20", "Engr. Sample")
    /** What a record holds, leaving out its identity and time. */
    private fun kept(a: ProjectAbstract) = a.run { listOf(projectId, asOf, overallPct, ProjectAbstracts.rows(rowsJson), fileName, uploadedBy, equivUnits) }

    private fun openNewAbstract(abstracts: List<ProjectAbstract>, today: String, onSave: (ProjectAbstract) -> Unit = {}) {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) { ActualAccomplishmentSection(project, emptyList(), abstracts, "Engr. Sample", onSave, {}, {}, { _, _ -> }, LocalDate.parse(today)) }
        }
        rule.onNodeWithTag("new_abstract").performClick()
    }
    /** A node of the form's list, scrolled to. */
    private fun inForm(tag: String) = rule.onNodeWithTag("new_abstract_rows").performScrollToNode(hasTestTag(tag)).let { rule.onNodeWithTag(tag) }

    // "New abstract" on a small phone: only this period's quantities are typed; the to date and the overall follow, and Save files the record.
    @Config(qualifiers = "w360dp-h640dp")
    @Test fun newAbstract_typedQuantitiesGiveTheToDateAndTheOverall_andSaveFilesIt() {
        // On a busy machine one scroll step of this form took longer than the 60 s idle limit; with a longer one the test passes in about 80 s.
        androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
        val filed = mutableStateListOf(first)
        openNewAbstract(filed, "2026-11-20") { filed += it }
        rule.onNodeWithText("Type only this period's quantity of each item. Previous is the to date of the abstract before it; to date, the amounts and the percentages are computed.").assertExists()
        inForm("new_abstract_note").assert(hasText("Overall to date 77.77%, from 77.77% as of Nov 13, 2026."))
        inForm("next_done_0").assert(hasText("To date 420"))
        rule.onNodeWithText("520 LOT · Previous 420").assertExists()
        rule.onNodeWithText("Subtotal (Land Acquisition Cost)").assertDoesNotExist() // only the pay lines and their headings

        inForm("next_period_0").performTextReplacement("twenty")
        rule.onNodeWithTag("save_new_abstract").performClick()
        rule.onNodeWithTag("new_abstract_problems").assert(hasText("1 LAND ACQUISITION COST: this period must be a number."))
        assertEquals(listOf(first), filed.toList())

        inForm("next_period_0").performTextReplacement("20")
        inForm("next_done_0").assert(hasText("To date 440"))
        inForm("next_period_4").performTextReplacement("0.25")
        inForm("next_period_8").performTextReplacement("10.5")
        inForm("next_done_8").assert(hasText("To date 46.5"))
        inForm("new_abstract_note").assert(hasText("Overall to date 83.10%, from 77.77% as of Nov 13, 2026."))
        rule.onNodeWithTag("save_new_abstract").performClick()

        assertEquals(kept(next(mapOf(0 to 20.0, 4 to 0.25, 8 to 10.5))), kept(filed.last()))
        assertEquals(83.1, filed.last().overallPct, 0.0)
        // The form closes on the abstract it made, which has no file.
        rule.onNodeWithTag("save_new_abstract").assertDoesNotExist()
        rule.onNodeWithTag("abstract_note").assert(hasText("Saved the abstract as of Nov 20, 2026: overall 83.10%."))
        rule.onNodeWithText("Made in the app").assertExists()
        rule.onNodeWithText("Made by").assertExists()
        rule.onNodeWithText("Uploaded by").assertDoesNotExist()
    }

    @Test fun newAbstract_datedBeforeTheLatestAbstract_saysSo_andCannotBeSaved() {
        openNewAbstract(listOf(first, next(emptyMap()).copy(id = 2)), "2026-11-19")
        rule.onNodeWithTag("new_abstract_error").assert(hasText("The date must not be before the latest abstract's, Nov 20, 2026."))
        rule.onNodeWithTag("save_new_abstract").assertIsNotEnabled()
        rule.onNodeWithTag("next_period_0").assertDoesNotExist()
    }

    // A turn of the phone recreates the screen: the form stays open with the quantities typed so far.
    @Test fun newAbstract_keepsWhatWasTyped_whenTheScreenIsRecreated() {
        val restore = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        restore.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) { ActualAccomplishmentSection(project, emptyList(), listOf(first), "Engr. Sample", {}, {}, {}, { _, _ -> }, LocalDate.parse("2026-11-20")) }
        }
        rule.onNodeWithTag("new_abstract").performClick()
        inForm("next_period_0").performTextReplacement("20")
        inForm("next_period_8").performTextReplacement("10.5")
        restore.emulateSavedInstanceStateRestore()
        inForm("next_period_0").assert(hasText("20"))
        inForm("next_done_0").assert(hasText("To date 440"))
        inForm("next_done_8").assert(hasText("To date 46.5"))
    }

    // Made again for a date that already has an abstract: the boxes start from that one's quantities, and saving asks first.
    @Test fun newAbstract_ofADateAlreadyFiled_startsFromItsQuantities_andReplacesItAfterAsking() {
        var saved: ProjectAbstract? = null
        openNewAbstract(listOf(first, next(mapOf(0 to 20.0, 8 to 10.5)).copy(id = 2)), "2026-11-20") { saved = it }
        inForm("next_period_0").assert(hasText("20"))
        inForm("next_period_8").assert(hasText("10.5"))
        inForm("next_done_4").assert(hasText("To date 0.6")) // nothing was typed for it
        inForm("new_abstract_note").assert(hasText("Overall to date 81.69%, from 77.77% as of Nov 13, 2026. Saving replaces the abstract already filed for this date."))
        rule.onNodeWithTag("save_new_abstract").performClick()
        rule.onNodeWithText("An abstract as of Nov 20, 2026 is already filed. Replace it with this one?").assertExists()
        assertEquals(null, saved)
        rule.onNodeWithTag("confirm_replace_abstract").performClick()
        assertEquals(kept(next(mapOf(0 to 20.0, 8 to 10.5))), kept(saved!!))
        rule.onNodeWithTag("abstract_note").assert(hasText("Replaced the abstract as of Nov 20, 2026: overall 81.69%."))
    }

    // Land Acquisition is a part that is its own pay item: its figures are the first line under its heading, in the tab and on the sheet.
    @Config(qualifiers = "w411dp-h891dp")
    @Test fun aPartThatIsItsOwnPayItem_showsItsFiguresUnderItsHeading() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ActualAccomplishmentSection(project, emptyList(), listOf(first), "", null, null, {}, { _, _ -> }) } }
        rule.onNodeWithText("1  LAND ACQUISITION COST").assertExists()
        rule.onNodeWithTag("abstract_row_0").assert(hasText("LAND ACQUISITION COST")).assert(hasText("520")).assert(hasText("LOT")).assert(hasText("27,405,210.00")).assert(hasText("80.77%"))
        rule.onNodeWithTag("abstract_part_0").performClick()
        rule.onNodeWithTag("abstract_row_0").assertDoesNotExist()
        rule.onNodeWithText("1  LAND ACQUISITION COST").assertExists() // its heading stays
        // The sheet ("Print abstract") has every column on the part's own row.
        val row = "<td class='c'>1</td><td><b>LAND ACQUISITION COST</b></td><td class='n'>520</td><td class='c'>LOT</td><td class='n'>65,250.50</td><td class='n'>33,930,260.00</td>"
        assertTrue(BlueBookForms.actualAbstract(project, emptyList(), first, com.example.util.Signatories("", "", "", "", "", "")).contains(row))
    }

    // "Print abstract" hands over the abstract shown, for its detailed sheet; a viewer can print it too.
    @Config(qualifiers = "w411dp-h891dp")
    @Test fun printAbstract_handsOverTheAbstractShown() {
        var printed: ProjectAbstract? = null
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ActualAccomplishmentSection(project, emptyList(), listOf(first), "", null, null, { printed = it }, { _, _ -> }) } }
        rule.onNodeWithTag("print_abstract").assert(hasText("Print abstract")).performClick()
        assertEquals(first, printed)
    }

    // 320dp wide: on a wider screen Robolectric never comes to rest under a dialog of the platform's width that has text
    // boxes (it measures the dialog at two widths, over and over), with the table in sight or not.
    @Config(qualifiers = "w320dp-h891dp")
    @Test fun billingDialog_opensOverThePayItemsTable() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ActualAccomplishmentSection(project, emptyList(), listOf(first), "", null, null, {}, { _, _ -> }) } }
        rule.onNodeWithTag("abstract_row_0").assertIsDisplayed()
        rule.onNodeWithTag("print_billing_abstract").performClick()
        rule.onNodeWithText("This billing").assertExists()
    }

    // At a phone's density each column is rounded to whole pixels on its own; a group's one wide cell must still end where they end.
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    @Test fun table_groupRowIsAsWideAsAnItemRow() {
        val grouped = abstract.copy(rowsJson = abstract.rowsJson.replace("{\"kind\":\"item\"", "{\"kind\":\"group\",\"no\":\"2.1\",\"name\":\"Site Works\",\"qty\":null,\"unit\":\"\",\"unitCost\":null,\"amount\":null,\"prev\":[null,null,null],\"period\":[null,null,null],\"toDate\":[null,null,null],\"balance\":[null,null]}," + "{\"kind\":\"item\""))
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ActualAccomplishmentSection(project, emptyList(), listOf(grouped), "", null, null, {}, { _, _ -> }) } }
        rule.onNodeWithTag("abstract_row_1").assert(hasText("Site Works"))
        assertEquals(rule.onNodeWithTag("abstract_row_2").fetchSemanticsNode().size.width, rule.onNodeWithTag("abstract_row_1").fetchSemanticsNode().size.width)
    }

    // "Monthly": the abstract of the month as of a date, computed from the filed ones; it prints, and nothing else is offered.
    @Config(qualifiers = "w411dp-h891dp")
    @Test fun monthly_showsTheMonthsAbstract_andPrintsIt() {
        val october = first.copy(
            id = 2, asOf = "2026-10-30", overallPct = 74.36, uuid = "october",
            rowsJson = ProjectAbstracts.rowsJson(nextRows.mapIndexed { i, r -> if (i == 0) r.copy(toDate = listOf(400.0, 26100200.0, 76.92)) else r })
        )
        var printed: ProjectAbstract? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                ActualAccomplishmentSection(project, emptyList(), listOf(first, october), "Engr. Sample", {}, {}, { printed = it }, { _, _ -> }, LocalDate.parse("2026-11-15"))
            }
        }
        rule.onNodeWithTag("monthly_note").assertDoesNotExist() // the tab opens on Weekly
        rule.onNodeWithTag("section_chip_Monthly").performClick()
        rule.onNodeWithTag("monthly_note").assert(hasText("To date is the abstract as of Nov 13, 2026. Previous is the abstract as of Oct 30, 2026."))
        rule.onNodeWithText("Nov 15, 2026").assertExists() // the date starts at today
        rule.onNodeWithTag("standing_Overall").assert(hasText("77.77%"))
        // Land Acquisition: previous is October's to date, this period what was done since.
        rule.onNodeWithTag("abstract_row_0").assert(hasText("400")).assert(hasText("26,100,200.00")).assert(hasText("76.92%"))
            .assert(hasText("20")).assert(hasText("1,305,010.00")).assert(hasText("3.85%")).assert(hasText("27,405,210.00"))
        listOf("upload_abstract", "new_abstract", "delete_abstract", "print_billing_abstract").forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
        rule.onNodeWithText("Uploaded by").assertDoesNotExist()
        rule.onNodeWithTag("print_abstract").performClick()
        assertEquals(kept(ProjectAbstracts.monthly(listOf(first, october), "2026-11-15").abstract!!), kept(printed!!))
        rule.onNodeWithTag("section_chip_Weekly").performClick()
        rule.onNodeWithTag("upload_abstract").assertExists()
        rule.onNodeWithTag("monthly_note").assertDoesNotExist()
    }

    @Test fun monthly_ofADateBeforeTheFirstAbstract_saysSo() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) { ActualAccomplishmentSection(project, emptyList(), listOf(first), "", null, null, {}, { _, _ -> }, LocalDate.parse("2026-11-12")) }
        }
        rule.onNodeWithTag("section_chip_Monthly").performClick()
        rule.onNodeWithTag("monthly_error").assert(hasText("No abstract is filed on or before Nov 12, 2026."))
        rule.onNodeWithTag("print_abstract").assertDoesNotExist()
    }

    private fun report(week: String, acquisition: Double, development: Double, housing: Double) = WeeklyReport(
        projectId = 1, reportingWeek = week, daysElapsed = 0, remainingDays = 0, targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0,
        manpowerJson = "[]", equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]", documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = "",
        accomplishmentItemsJson = listOf("Land Acquisition" to acquisition, "Land Development" to development, "Housing Construction" to housing)
            .joinToString(",", "[", "]") { (name, actual) -> """{"itemDescription":"$name","targetPct":0,"actualPct":$actual,"weightPct":10}""" }
    )
    private fun actuals(accItemsJson: String) = org.json.JSONArray(accItemsJson).let { a -> (0 until a.length()).map { a.getJSONObject(it).getDouble("actualPct") } }

    /** Opens the form in the abstract's week and saves it as it is: the overall actual and the lines' as stored. */
    private fun savedAsOpened(existing: WeeklyReport?): Pair<Double, List<Double>> {
        var saved: Pair<Double, List<Double>>? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = project, projectId = 1, existingReport = existing, onDismiss = {},
                    onSubmit = { _, _, actual, _, _, _, _, _, _, _, accItemsJson, _ -> saved = actual to actuals(accItemsJson) },
                    today = LocalDate.of(2025, 3, 12), abstracts = listOf(abstract))
            }
        }
        rule.onNodeWithTag("submit_weekly_report_button").performClick()
        return saved!!
    }

    // Saving stores the abstract's three figures: 25.21 x 20% + 1 x 70% overall.
    @Test fun weeklyForm_savesTheAbstractsFigures() {
        val (overall, lines) = savedAsOpened(null)
        assertEquals(listOf(0.0, 25.21, 1.0), lines)
        assertEquals(5.742, overall, 1e-9)
    }

    // Also in a saved report that had other figures, though nothing was typed.
    @Test fun weeklyForm_savesTheAbstractsFigures_whenEditingASavedReport() {
        val (overall, lines) = savedAsOpened(report("Mar 8, 2025 - Mar 14, 2025", 10.0, 20.0, 30.0))
        assertEquals(listOf(0.0, 25.21, 1.0), lines)
        assertEquals(5.742, overall, 1e-9)
    }

    // A draft keeps what was typed (or carried) under the abstract's figures, so the boxes return to it in a week without an abstract.
    @Test fun weeklyForm_draftKeepsWhatWasTypedUnderTheAbstract() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WeeklyDrafts.clear(context, 1)
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = project, projectId = 1, onDismiss = {}, onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    today = LocalDate.of(2025, 3, 12), reports = listOf(report("Mar 1, 2025 - Mar 7, 2025", 50.0, 20.0, 0.5)), abstracts = listOf(abstract))
            }
        }
        rule.onNodeWithTag("weekly_actual_Land Development").assertDoesNotExist() // the abstract's 25.21 stands over the 20.00 carried from the last report
        rule.onNodeWithTag("weekly_target_Land Development").performTextReplacement("30")
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(listOf(50.0, 20.0, 0.5), actuals(WeeklyDrafts.load(context, 1)!!.report.accomplishmentItemsJson))
        WeeklyDrafts.clear(context, 1)
    }

    // Without a bar chart the targets are typed; the actuals are the latest abstract's on or before the week's last day, with no field.
    @Test fun weeklyForm_withAnAbstractAndNoChart_hasOnlyTheTargetFields() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                // Wed Mar 19, 2025 is in the week of Sat Mar 15 to Fri Mar 21, the week after the abstract's.
                AddWeeklyReportDialog(project = project, projectId = 1, onDismiss = {}, onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    today = LocalDate.of(2025, 3, 19), abstracts = listOf(abstract))
            }
        }
        rule.onNodeWithText("Actual % is from the abstract as of Mar 14, 2025 (Actual Accomplishment tab).").assertExists()
        rule.onNodeWithTag("weekly_actual_Land Development").assertDoesNotExist()
        rule.onNodeWithText("Actual %").assertDoesNotExist()
        rule.onNodeWithTag("weekly_target_Land Development").assert(hasText("0.00")).assert(typed)
        rule.onNodeWithText("Actual: 5.74%").assertExists() // 25.21 x 20% + 1 x 70%
    }

    // The approved bar chart of ProjectAbstractsTest: roads are Land Development, the housing units Housing Construction.
    private val charted = project.copy(barChartJson = """[{"effective":"","items":[{"name":"Roads","amount":60,"bars":[[1,100]]},{"name":"Housing units","amount":40,"bars":[[51,150]]}]}]""")

    // With a bar chart the targets have no field; a week before the first abstract has none to take, so its actuals are typed.
    @Test fun weeklyForm_withAChartAndNoAbstract_hasOnlyTheActualFields() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                // Wed Mar 5, 2025 is in the week of Sat Mar 1 to Fri Mar 7.
                AddWeeklyReportDialog(project = charted, projectId = 1, onDismiss = {}, onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    today = LocalDate.of(2025, 3, 5), abstracts = listOf(abstract))
            }
        }
        rule.onNodeWithText("Actual % is from the abstract", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("weekly_target_Land Development").assertDoesNotExist()
        rule.onNodeWithText("Target %").assertDoesNotExist()
        rule.onNodeWithTag("weekly_actual_Land Development").assert(hasText("0.00")).assert(typed)
    }

    // Both automatic: the three lines are left out, the notes and the grand total stay, and Save stores the chart's targets and the abstract's actuals.
    @Test fun weeklyForm_withAChartAndAnAbstract_hasNoFields_andSavesBoth() {
        var saved: List<Pair<Double, Double>>? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = charted, projectId = 1, onDismiss = {},
                    onSubmit = { _, _, _, _, _, _, _, _, _, _, accItemsJson, _ ->
                        saved = org.json.JSONArray(accItemsJson).let { a -> (0 until a.length()).map { a.getJSONObject(it).run { getDouble("targetPct") to getDouble("actualPct") } } }
                    },
                    today = LocalDate.of(2025, 3, 12), abstracts = listOf(abstract))
            }
        }
        rule.onNodeWithText("Target % is from the approved bar chart as of Mar 14, 2025.").assertExists()
        rule.onNodeWithText("Actual % is from the abstract as of Mar 14, 2025 (Actual Accomplishment tab).").assertExists()
        listOf("Land Acquisition", "Land Development", "Housing Construction").forEach {
            rule.onNodeWithTag("weekly_target_$it").assertDoesNotExist()
            rule.onNodeWithTag("weekly_actual_$it").assertDoesNotExist()
        }
        rule.onNodeWithText("Wt:", substring = true).assertDoesNotExist()
        rule.onNodeWithText("GRAND TOTAL:").assertExists()
        rule.onNodeWithText("Actual: 15.53%").assertExists() // 25.21 x 60% + 1 x 40%, by the chart's amounts
        rule.onNodeWithTag("submit_weekly_report_button").performClick()
        // Mar 14, 2025 is contract day 68: the roads are 68% planned, the housing units 18%.
        assertEquals(listOf(0.0 to 0.0, 68.0 to 25.21, 18.0 to 1.0), saved)
    }
}
