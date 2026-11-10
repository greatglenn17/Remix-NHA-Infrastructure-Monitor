package com.example

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.data.model.WeeklyReport
import com.example.ui.dialogs.AddWeeklyReportDialog
import com.example.ui.dialogs.WeeklyStep
import com.example.ui.dialogs.weeklyErrors
import com.example.ui.theme.NHATheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeeklyReportStepsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun weeklyErrors_flagsOutOfRange() {
        assertEquals(
            listOf(WeeklyStep.WEEK to "Drainage: actual must be 0 to 100%"),
            weeklyErrors(mapOf("Drainage" to ("50" to "120")), listOf("Drainage"))
        )
    }

    @Test fun weeklyErrors_ignoresRemovedScopeItems() {
        assertEquals(emptyList<Pair<WeeklyStep, String>>(), weeklyErrors(mapOf("Old" to ("50" to "120")), listOf("Drainage")))
    }

    @Test fun weeklyErrors_blankIsFine() {
        assertEquals(emptyList<Pair<WeeklyStep, String>>(), weeklyErrors(mapOf("Drainage" to ("" to "")), listOf("Drainage")))
    }

    @Test fun editUnchanged_submitsSameValues() {
        val scope = JSONArray()
            .put(JSONObject().put("name", "Drainage").put("amount", 1000.0).put("weightPct", 50.0))
            .put(JSONObject().put("name", "Roads").put("amount", 1000.0).put("weightPct", 50.0))
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C",
            scopeOfWork = scope.toString(), contractCostOriginal = 2000.0, contractCostRevised = 0.0,
            contractDurationDays = 100, dateStarted = "2026-01-05", completionDateOriginal = "2026-12-31",
            completionDateRevised = ""
        )
        val acc = JSONArray()
            .put(JSONObject().put("itemDescription", "Drainage").put("targetPct", 40.0).put("actualPct", 30.5).put("weightPct", 50.0))
            .put(JSONObject().put("itemDescription", "Roads").put("targetPct", 20.0).put("actualPct", 10.0).put("weightPct", 50.0))
            .toString()
        val report = WeeklyReport(
            projectId = 1, reportingWeek = "Oct 19, 2026 - Oct 25, 2026", daysElapsed = 0, remainingDays = 0,
            targetAccomplishmentPct = 30.0, actualAccomplishmentPct = 20.25,
            manpowerJson = JSONArray().put(JSONObject().put("designation", "Foreman").put("count", 2).put("remarks", "")).toString(),
            equipmentJson = "[]",
            activitiesJson = JSONArray().put(JSONObject().put("day", "Tuesday").put("description", "Backfill").put("remarks", "ok")).toString(),
            issuesJson = JSONArray().put(JSONObject().put("description", "Rain").put("actionTaken", "Pumped").put("remarks", "")).toString(),
            accomplishmentItemsJson = acc,
            documentsIssuedReceivedJson = "[]",
            attachedPhotoUrlsJson = JSONArray().put("content://photos/1.jpg").toString(),
            submittedByStaff = "me"
        )
        var got: List<Any>? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = project, projectId = 1, existingReport = report, onDismiss = {},
                    onSubmit = { week, target, actual, activities, photos, weather, dates, manpower, equipment, issues, accItems, _ ->
                        got = listOf(week, target, actual, activities, photos, weather.keys, dates.values.toList(), manpower, equipment, issues, accItems)
                    })
            }
        }
        rule.onNodeWithTag("submit_weekly_report_button").performClick()
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        // The two items of work were saved one by one; they come back as the one Land Development line, same totals.
        fun line(name: String, target: Double, actual: Double, weight: Double) =
            JSONObject().put("itemDescription", name).put("targetPct", target).put("actualPct", actual).put("weightPct", weight)
        val lines = JSONArray().put(line("Land Acquisition", 0.0, 0.0, 0.0)).put(line("Land Development", 30.0, 20.25, 100.0))
            .put(line("Housing Construction", 0.0, 0.0, 0.0)).toString()
        assertEquals(
            listOf(
                report.reportingWeek, report.targetAccomplishmentPct, report.actualAccomplishmentPct, report.activitiesJson,
                report.attachedPhotoUrlsJson, days.toSet(), (19..25).map { "2026-10-$it" },
                report.manpowerJson, report.equipmentJson, report.issuesJson, lines
            ),
            got
        )
    }

    // All five steps fit as tabs; an error on Review takes you back to the step it belongs to.
    @Test fun reviewError_goesToItsStep() {
        val scope = JSONArray().put(JSONObject().put("name", "Drainage").put("amount", 1000.0).put("weightPct", 100.0))
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C",
            scopeOfWork = scope.toString(), contractCostOriginal = 1000.0, contractCostRevised = 0.0,
            contractDurationDays = 100, dateStarted = "2026-01-05", completionDateOriginal = "2026-12-31",
            completionDateRevised = ""
        )
        val report = WeeklyReport(
            projectId = 1, reportingWeek = "Oct 19, 2026 - Oct 25, 2026", daysElapsed = 0, remainingDays = 0,
            targetAccomplishmentPct = 50.0, actualAccomplishmentPct = 120.0,
            manpowerJson = "[]", equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]",
            accomplishmentItemsJson = JSONArray().put(JSONObject().put("itemDescription", "Drainage").put("targetPct", 50.0).put("actualPct", 120.0).put("weightPct", 100.0)).toString(),
            documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = "me"
        )
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddWeeklyReportDialog(project = project, projectId = 1, existingReport = report, onDismiss = {},
                    onSubmit = { _, _, _, _, _, _, _, _, _, _, _, _ -> })
            }
        }
        rule.onNodeWithText("Step 1 of 5: Week and progress").assertExists()
        rule.onNodeWithText("Enter 0 to 100").assertExists()
        rule.onNodeWithText("Review").performClick()
        rule.onNodeWithText("Step 5 of 5: Review and save").assertExists()
        rule.onNodeWithText("Land Development: actual must be 0 to 100%").assertExists()
        rule.onNodeWithText("Go to Week").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Step 1 of 5: Week and progress").assertExists()
    }
}
