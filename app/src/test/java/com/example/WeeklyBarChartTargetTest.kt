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
}
