package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.ui.dialogs.AddWeeklyReportDialog
import com.example.ui.theme.NHATheme
import com.example.util.BarChart
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// With an approved bar chart the weekly report's target is the chart's plan as of the week's last day: it has no field, and Save stores it.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeeklyBarChartTargetTest {
    @get:Rule val rule = createComposeRule()

    private val project = Project(
        name = "P", location = "L", implementingOffice = "O", contractor = "C",
        scopeOfWork = JSONArray().put(JSONObject().put("name", "Drainage").put("amount", 1000.0).put("weightPct", 100.0)).toString(),
        contractCostOriginal = 1000.0, contractCostRevised = 0.0, contractDurationDays = 600,
        dateStarted = "2026-01-05", completionDateOriginal = "2027-08-27", completionDateRevised = "",
        barChartJson = BarChart.toJson(listOf(
            BarChart.Version("Original", "2026-01-05", listOf(BarChart.Item("Drainage", 1000.0, listOf(BarChart.Bar(1, 600)))))
        ))
    )

    // Each line's target as Save stores it, by the line's name.
    private var saved = emptyMap<String, Double>()
    private fun targets(accItemsJson: String) = JSONArray(accItemsJson).let { a -> (0 until a.length()).associate { a.getJSONObject(it).run { getString("itemDescription") to getDouble("targetPct") } } }
    private fun twoDecimals(value: Double) = "%.2f".format(java.util.Locale.US, value).toDouble()

    @Test fun target_comesFromTheChart() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = project, projectId = 1, onDismiss = {}, onSubmit = { _, _, _, _, _, _, _, _, _, _, accItemsJson, _ -> saved = targets(accItemsJson) },
                    today = LocalDate.of(2026, 10, 27))
            }
        }
        // The week of Tue Oct 27 runs Sat Oct 24 to Fri Oct 30.
        val expected = BarChart.itemTargetsOn(project, emptyList(), LocalDate.of(2026, 10, 30))!!.getValue("Drainage")
        rule.onNodeWithTag("weekly_target_Land Development").assertDoesNotExist()
        rule.onNodeWithTag("weekly_actual_Land Development").assertExists() // no abstract: the actual is typed
        rule.onNodeWithText("Target % is from the approved bar chart", substring = true).assertExists()
        rule.onNodeWithTag("submit_weekly_report_button").performClick()
        assertEquals(twoDecimals(expected), saved.getValue("Land Development"), 0.0)
    }

    // The rows are the chart's items, even when the Scope of Works names them differently.
    @Test fun rows_areTheChartItems() {
        val imported = project.copy(barChartJson = BarChart.toJson(listOf(
            BarChart.Version("Original", "2026-01-05", listOf(
                BarChart.Item("Earthworks", 250.0, listOf(BarChart.Bar(1, 300))),
                BarChart.Item("Housing", 750.0, listOf(BarChart.Bar(1, 600)))
            ))
        )))
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = imported, projectId = 1, onDismiss = {}, onSubmit = { _, _, _, _, _, _, _, _, _, _, accItemsJson, _ -> saved = targets(accItemsJson) },
                    today = LocalDate.of(2026, 10, 27))
            }
        }
        val expected = BarChart.itemTargetsOn(imported, emptyList(), LocalDate.of(2026, 10, 30))!!
        rule.onNodeWithTag("weekly_actual_Drainage").assertDoesNotExist()
        rule.onNodeWithText("Wt: 25.00%").assertExists()
        rule.onNodeWithTag("submit_weekly_report_button").performClick()
        assertEquals(
            mapOf("Land Acquisition" to 0.0, "Land Development" to twoDecimals(expected.getValue("Earthworks")), "Housing Construction" to twoDecimals(expected.getValue("Housing"))),
            saved
        )
    }

    // A community facility has no land or housing lines. Before its first abstract its rows, and what Save stores, are the chart's own items.
    private val hall = project.copy(projectType = "Community Facility", barChartJson = BarChart.toJson(listOf(
        BarChart.Version("Original", "2026-01-05", listOf(
            BarChart.Item("Earthworks", 250.0, listOf(BarChart.Bar(1, 300))),
            BarChart.Item("Roofing", 600.0, listOf(BarChart.Bar(1, 600))),
            BarChart.Item("Construction of Guard House", 150.0, listOf(BarChart.Bar(301, 600)))
        ))
    )))
    private var actuals = emptyMap<String, Double>()
    private fun facilityForm(abstracts: List<com.example.data.model.ProjectAbstract>) = rule.setContent {
        NHATheme(themeMode = AppThemeMode.LIGHT) {
            AddWeeklyReportDialog(project = hall, projectId = 1, onDismiss = {}, abstracts = abstracts, today = LocalDate.of(2026, 10, 27),
                onSubmit = { _, _, _, _, _, _, _, _, _, _, accItemsJson, _ ->
                    saved = targets(accItemsJson)
                    actuals = JSONArray(accItemsJson).let { a -> (0 until a.length()).associate { a.getJSONObject(it).run { getString("itemDescription") to getDouble("actualPct") } } }
                })
        }
    }

    @Test fun aCommunityFacility_withoutAnAbstract_listsTheChartItems() {
        facilityForm(emptyList())
        val expected = BarChart.itemTargetsOn(hall, emptyList(), LocalDate.of(2026, 10, 30))!!
        rule.onNodeWithTag("weekly_actual_Land Development").assertDoesNotExist()
        rule.onNodeWithTag("weekly_actual_Earthworks").assertExists()
        rule.onNodeWithTag("submit_weekly_report_button").performClick()
        assertEquals(expected.mapValues { twoDecimals(it.value) }, saved)
    }

    // With the week's abstract its rows are the abstract's works: the actual is each work's own subtotal, the target the
    // chart's for the chart items that belong to the work (the guard house is a row under ROOFING WORKS here), by their amounts.
    @Test fun aCommunityFacility_listsTheAbstractsWorks() {
        val none3 = listOf<Double?>(null, null, null)
        fun row(kind: String, name: String, amount: Double? = null, pct: Double? = null) =
            com.example.util.AbstractExcel.Row(kind, "", name, null, "", null, amount, none3, none3, listOf(null, null, pct), listOf(null, null))
        val abstract = com.example.data.model.ProjectAbstract(projectId = 1, asOf = "2026-10-30", overallPct = 17.5,
            rowsJson = com.example.util.ProjectAbstracts.rowsJson(listOf(
                row("part", "EARTHWORKS"), row("subtotal", "Sub-Total (Earthworks)", 250.0, 40.0),
                row("part", "ROOFING WORKS"), row("item", "Construction of Guard House and Gate", 150.0), row("subtotal", "Sub-Total (Roofing Works)", 750.0, 10.0),
                row("part", "EXTRA WORKS"), row("subtotal", "Sub-Total (Extra Works)", 0.0, 0.0), row("total", "GRAND TOTAL", 1000.0, 17.5))))
        val works = com.example.util.ProjectAbstracts.works(abstract)
        assertEquals(listOf("EARTHWORKS" to 250.0, "ROOFING WORKS" to 750.0, "EXTRA WORKS" to 0.0), works.map { it.name to it.amount })
        assertEquals(listOf(0, 1, 1, -1), listOf("Earthworks", "Roofing", "Construction of Guard House", "Landscaping").map { com.example.util.ProjectAbstracts.workOf(it, works) })
        assertEquals(listOf(1.0, 0.0), listOf(com.example.util.ProjectAbstracts.likeness("Flag-pole", "FLAG POLE"), com.example.util.ProjectAbstracts.likeness("A", "A")))

        facilityForm(listOf(abstract))
        val expected = BarChart.itemTargetsOn(hall, emptyList(), LocalDate.of(2026, 10, 30))!!
        // The chart gives every target and the abstract every actual: no fields, and the abstract's note.
        listOf("Earthworks", "EARTHWORKS", "ROOFING WORKS").forEach { rule.onNodeWithTag("weekly_actual_$it").assertDoesNotExist() }
        rule.onNodeWithTag("abstract_note").assertExists()
        rule.onNodeWithTag("submit_weekly_report_button").performClick()
        val roofing = (600 * expected.getValue("Roofing") + 150 * expected.getValue("Construction of Guard House")) / 750
        assertEquals(mapOf("EARTHWORKS" to twoDecimals(expected.getValue("Earthworks")), "ROOFING WORKS" to twoDecimals(roofing), "EXTRA WORKS" to 0.0), saved)
        assertEquals(mapOf("EARTHWORKS" to 40.0, "ROOFING WORKS" to 10.0, "EXTRA WORKS" to 0.0), actuals)
    }
}
