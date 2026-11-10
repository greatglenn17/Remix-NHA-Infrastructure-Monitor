package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.data.model.WeeklyReport
import com.example.ui.dialogs.AddWeeklyReportDialog
import com.example.ui.dialogs.WeekRange
import com.example.ui.dialogs.WeeklyDraft
import com.example.ui.dialogs.WeeklyDrafts
import com.example.ui.theme.NHATheme
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// A new weekly report keeps a draft on the phone until it is saved or discarded.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeeklyDraftsTest {
    @get:Rule val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val today = LocalDate.of(2026, 10, 27)
    private val project = Project(
        name = "P", location = "L", implementingOffice = "O", contractor = "C",
        scopeOfWork = JSONArray().put(JSONObject().put("name", "Drainage").put("amount", 1000.0).put("weightPct", 100.0)).toString(),
        contractCostOriginal = 1000.0, contractCostRevised = 0.0, contractDurationDays = 300,
        dateStarted = "2026-01-05", completionDateOriginal = "2026-12-31", completionDateRevised = ""
    )
    private val manpower = JSONArray().put(JSONObject().put("designation", "Foreman").put("count", 3).put("remarks", "")).toString()

    private fun draft(week: String = "Oct 19, 2026 - Oct 25, 2026") = WeeklyDraft(
        savedAt = 1_793_000_000_000,
        report = WeeklyReport(
            projectId = 1, reportingWeek = week, daysElapsed = 0, remainingDays = 0,
            targetAccomplishmentPct = 30.0, actualAccomplishmentPct = 25.0,
            manpowerJson = manpower, equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]",
            accomplishmentItemsJson = JSONArray().put(JSONObject().put("itemDescription", "Drainage").put("targetPct", 30.0).put("actualPct", 25.0).put("weightPct", 100.0)).toString(),
            documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = JSONArray().put("nhafile://photo/1.jpg").toString(),
            submittedByStaff = ""
        ),
        weather = mapOf("Monday" to List(24) { if (it < 6) "RAINY" else "FAIR" })
    )

    // A report gets a fresh id and timestamps each time it is built; the draft keeps only these.
    private fun WeeklyDraft.content() = listOf(
        savedAt, weather, report.reportingWeek, report.targetAccomplishmentPct, report.actualAccomplishmentPct,
        report.manpowerJson, report.equipmentJson, report.activitiesJson, report.issuesJson,
        report.accomplishmentItemsJson, report.attachedPhotoUrlsJson, report.photoDetailsJson
    )

    private var submitted: List<Any>? = null
    private fun open(existing: WeeklyReport? = null) = rule.setContent {
        NHATheme(themeMode = AppThemeMode.LIGHT) {
            AddWeeklyReportDialog(project = project, projectId = 1, existingReport = existing, onDismiss = {},
                onSubmit = { week, target, actual, _, photos, weather, _, manpowerJson, _, _, _, _ ->
                    submitted = listOf(week, target, actual, photos, weather.getValue("Monday"), manpowerJson)
                }, today = today)
        }
    }

    @Test fun draft_roundTrips() {
        WeeklyDrafts.save(context, 1, draft())
        assertEquals(draft().content(), WeeklyDrafts.load(context, 1)?.content())
        assertNull(WeeklyDrafts.load(context, 2))
        WeeklyDrafts.clear(context, 1)
        assertNull(WeeklyDrafts.load(context, 1))
    }

    @Test fun typing_keepsADraft() {
        open()
        assertNull(WeeklyDrafts.load(context, 1))
        rule.onAllNodesWithText("0.00")[1].performTextReplacement("12.5")
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        val saved = WeeklyDrafts.load(context, 1)!!
        assertEquals(WeekRange.containing(today), saved.report.reportingWeek)
        assertTrue(saved.report.accomplishmentItemsJson, saved.report.accomplishmentItemsJson.contains("12.5"))
    }

    @Test fun continue_restoresTheDraft_andSavingRemovesIt() {
        WeeklyDrafts.save(context, 1, draft())
        open()
        rule.onNodeWithText("Continue your draft?").assertExists()
        rule.onNodeWithTag("continue_weekly_draft").performClick()
        rule.onNodeWithText("Oct 19, 2026 - Oct 25, 2026").assertExists()
        rule.onNodeWithTag("submit_weekly_report_button").performClick()
        assertEquals(
            listOf("Oct 19, 2026 - Oct 25, 2026", 30.0, 25.0, JSONArray().put("nhafile://photo/1.jpg").toString(),
                List(24) { if (it < 6) "RAINY" else "FAIR" }.joinToString(","), manpower),
            submitted
        )
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertNull(WeeklyDrafts.load(context, 1))
    }

    @Test fun startOver_dropsTheDraft() {
        WeeklyDrafts.save(context, 1, draft())
        open()
        rule.onNodeWithTag("discard_weekly_draft").performClick()
        rule.onNodeWithText(WeekRange.containing(today)).assertExists()
        assertNull(WeeklyDrafts.load(context, 1))
    }

    @Test fun editingASavedReport_neverOffersTheDraft() {
        WeeklyDrafts.save(context, 1, draft())
        open(existing = draft("Oct 12, 2026 - Oct 18, 2026").report)
        rule.onNodeWithText("Continue your draft?").assertDoesNotExist()
        rule.onNodeWithText("Oct 12, 2026 - Oct 18, 2026").assertExists()
        // The new-report draft is left for when a new report is started.
        assertEquals(draft().content(), WeeklyDrafts.load(context, 1)?.content())
    }
}
