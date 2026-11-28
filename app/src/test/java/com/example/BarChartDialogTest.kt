package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.AppThemeMode
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.ui.detail.sections.BarChartDialog
import com.example.ui.detail.sections.BarChartSection
import com.example.ui.theme.NHATheme
import com.example.util.BarChart
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The bar chart form: a revised chart's items take a new amount and name, and can be added and removed; the original's cannot.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class BarChartDialogTest {
    @get:Rule val rule = createComposeRule()

    private val items = listOf(
        BarChart.Item("Land development", 3_000_000.0, listOf(BarChart.Bar(1, 240))),
        BarChart.Item("Housing units", 2_000_000.5, listOf(BarChart.Bar(121, 296), BarChart.Bar(481, 554))) // ends between the 5-day cells, as imported
    )
    private val chart = BarChart.toJson(listOf(BarChart.Version("Original", "2026-01-05", items), BarChart.Version("Revised No. 1", "2026-06-01", items)))
    private val project = Project(
        id = 1, name = "P", location = "L", implementingOffice = "O", contractor = "C",
        scopeOfWork = """[{"name":"Land development","amount":3000000,"weightPct":60},{"name":"Housing units","amount":2000000.5,"weightPct":40}]""",
        contractCostOriginal = 5_000_000.5, contractCostRevised = 0.0, contractDurationDays = 540,
        dateStarted = "2026-01-05", completionDateOriginal = "2027-06-28", completionDateRevised = "", targetAccomplishment = 0.0,
        barChartJson = chart
    )

    private fun type(tag: String, text: String) = rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
    private fun errors(text: String) = rule.onAllNodesWithText(text, substring = true)

    @Test fun chartColouredOnTheWebsite_keepsItsColoursAndColouredDays_whenThePhoneSavesIt() {
        val colors = """[{"name":"Suspension No. 1","color":"#FFD966","work":false}]"""
        val coloured = BarChart.toJson(listOf(BarChart.Version("Original", "2026-01-05", items),
            BarChart.Version("Revised No. 1", "2026-06-01", listOf(items[0].copy(marks = "[[241,270,0]]"), items[1]), colors)))
        var saved: String? = null
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { BarChartDialog(project.copy(barChartJson = coloured), emptyList(), {}, { saved = it }) } }
        // A cost changed and put back is still a save of the form's own drafts.
        type("item_1_amount", "1")
        type("item_1_amount", "2,000,000.50")
        rule.onNodeWithTag("save_bar_chart").assertIsEnabled().performClick()
        assertEquals(coloured, saved)
    }

    @Test fun withAVariationOrder_theLatestRevisedChartMustTotalTheRevisedContract() {
        val vo = PendingDocument(projectId = 1, documentName = "Variation Order No. 1 (Additive) - PHP 500,000.00", status = "Approved", remarks = "")
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { BarChartDialog(project, listOf(vo), {}, {}) } }
        // The revised chart still totals the original contract, which the Variation Order has raised.
        errors("the chart totals").assertCountEquals(1)
        rule.onNodeWithTag("save_bar_chart").assertIsNotEnabled()
        type("item_1_amount", "2,500,000.50")
        errors("the chart totals").assertCountEquals(0)
        rule.onNodeWithTag("save_bar_chart").assertIsEnabled()
    }

    @Test fun revisedChart_itemsTakeAnAmountAndName_andCanBeAddedAndRemoved() {
        var saved: String? = null
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { BarChartDialog(project, emptyList(), {}, { saved = it }) } }
        // It opens on the revised chart, as saved: nothing to fix, and the amounts read back as they were.
        rule.onNodeWithTag("save_bar_chart").assertIsEnabled()
        type("item_1_amount", "1,500,000.50")
        errors("the chart totals").assertCountEquals(1)
        rule.onNodeWithTag("save_bar_chart").assertIsNotEnabled()
        rule.onNodeWithTag("add_chart_item").performScrollTo().performClick()
        errors("name every item").assertCountEquals(1)
        type("item_2_name", "Housing units")
        errors("two items are named Housing units").assertCountEquals(1)
        type("item_2_name", " Drainage ")
        type("item_2_amount", "500000")
        listOf("sm" to "2", "sd" to "6", "em" to "2", "ed" to "26").forEach { (box, value) -> type("bar_ Drainage _0_$box", value) }
        rule.onNodeWithTag("save_bar_chart").assertIsEnabled().performClick()
        val (original, revised) = BarChart.parse(saved!!)
        assertEquals(items, original.items)
        assertEquals(items[0], revised.items[0])
        assertEquals(listOf(BarChart.Item("Housing units", 1_500_000.5, items[1].bars), BarChart.Item("Drainage", 500_000.0, listOf(BarChart.Bar(36, 56)))), revised.items.drop(1))
        // Removing an item leaves the chart short of the contract; removing them all leaves no chart to save.
        rule.onNodeWithTag("item_2_remove").performScrollTo().performClick()
        errors("the chart totals").assertCountEquals(1)
        repeat(2) { rule.onNodeWithTag("item_0_remove").performScrollTo().performClick() }
        errors("add an item, or import the Excel file").assertCountEquals(1)
        rule.onNodeWithTag("save_bar_chart").assertIsNotEnabled()
    }

    @Test fun originalChart_keepsTheScopeOfWorksItems() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { BarChartDialog(project, emptyList(), {}, {}) } }
        rule.onNodeWithText("Original").performClick()
        rule.onAllNodesWithTag("item_0_amount").assertCountEquals(0)
        rule.onAllNodesWithTag("add_chart_item").assertCountEquals(0)
    }

    // The Bar chart tab: the chart in force opens first, a chip opens another chart on file, and Edit opens the form.
    @Test fun tab_showsTheChartInForceAndItsVersions_andEditOpensTheForm() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { BarChartSection(project, emptyList(), LocalDate.of(2026, 7, 1), onSave = {}) } }
        rule.onNodeWithText("In force").assertExists()
        rule.onNodeWithText("Revised No. 1 · from Jun 1, 2026", substring = true).assertExists()
        rule.onNodeWithTag("bar_row_Housing units").assertExists()
        rule.onNodeWithContentDescription("month 5 day 1 to month 10 day 26, month 17 day 1 to month 19 day 14").assertExists()
        rule.onNodeWithTag("section_chip_Original").performClick()
        rule.onNodeWithText("On file").assertExists()
        rule.onNodeWithTag("edit_bar_chart_button").performClick()
        rule.onNodeWithTag("save_bar_chart").assertExists()
    }

    @Test fun tab_withoutAChartOrTheRightToEdit_saysSo() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { BarChartSection(project.copy(barChartJson = ""), emptyList(), LocalDate.of(2026, 7, 1), onSave = null) } }
        rule.onNodeWithText("No bar chart yet", substring = true).assertExists()
        rule.onAllNodesWithTag("edit_bar_chart_button").assertCountEquals(0)
    }

    // A chart coloured on the website: the tab draws its colours and names them, as the website's sheet does.
    @Test fun tab_showsTheWebsitesColoursAndTheirNames() {
        val colors = """[{"name":"Suspension No. 1","color":"#FFD966","work":false}]"""
        val coloured = BarChart.toJson(listOf(BarChart.Version("Original", "2026-01-05", listOf(items[0].copy(marks = "[[241,270,0]]"), items[1]), colors)))
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { BarChartSection(project.copy(barChartJson = coloured), emptyList(), LocalDate.of(2026, 7, 1), onSave = null) } }
        rule.onNodeWithText("Suspension No. 1 (no work)").assertExists()
        rule.onNodeWithContentDescription("month 1 day 1 to month 8 day 30, Suspension No. 1: month 9 day 1 to month 9 day 30").assertExists()
    }
}
