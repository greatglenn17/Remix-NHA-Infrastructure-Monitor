package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.data.model.ProjectStatus
import com.example.data.model.UserRole
import com.example.data.model.WeeklyReport
import com.example.ui.detail.sections.SummarySection
import com.example.ui.detail.sections.needsThisWeeksReport
import com.example.ui.theme.NHATheme
import com.example.util.SdpSummary
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Summary asks for this week's report, shows the whole profile and, for housing, units by stage.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class SummaryReportPromptTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 27) // a Tuesday; the week is Oct 26 - Nov 1
    private val project = Project(
        name = "P", location = "L", implementingOffice = "NHA Bulacan", contractor = "JRC Builders",
        scopeOfWork = "[]", contractCostOriginal = 1000.0, contractCostRevised = 0.0,
        contractDurationDays = 300, dateStarted = "2026-01-05", completionDateOriginal = "2026-12-31",
        completionDateRevised = "", actualAccomplishment = 40.0
    )

    private fun report(week: String) = WeeklyReport(
        projectId = 1, reportingWeek = week, daysElapsed = 0, remainingDays = 0,
        targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0,
        manpowerJson = "[]", equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]",
        accomplishmentItemsJson = "[]", documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]",
        submittedByStaff = "me"
    )

    @Test fun needsReport_onlyWhenThisWeekHasNone() {
        assertTrue(needsThisWeeksReport(project, emptyList(), today))
        assertTrue(needsThisWeeksReport(project, listOf(report("Oct 19, 2026 - Oct 25, 2026")), today))
        // Older labels don't parse, so they never count as this week.
        assertTrue(needsThisWeeksReport(project, listOf(report("Week 41: October 5 - October 11, 2026")), today))
        assertFalse(needsThisWeeksReport(project, listOf(report("Oct 26, 2026 - Nov 1, 2026")), today))
        // A range picked by hand that overlaps the week counts too.
        assertFalse(needsThisWeeksReport(project, listOf(report("Oct 28, 2026 - Nov 3, 2026")), today))
    }

    @Test fun noPrompt_forFinishedSuspendedOrNotStarted() {
        assertFalse(needsThisWeeksReport(project.copy(actualAccomplishment = 100.0), emptyList(), today))
        assertFalse(needsThisWeeksReport(project.copy(status = ProjectStatus.SUSPENDED.label), emptyList(), today))
        assertFalse(needsThisWeeksReport(project.copy(dateStarted = "2026-11-02"), emptyList(), today))
    }

    private fun summary(onAdd: (() -> Unit)?) = rule.setContent {
        NHATheme(themeMode = AppThemeMode.LIGHT) {
            SummarySection(
                project, emptyList(), emptyList(), null, openIssues = 0, canEditProject = false, onEditProject = {},
                currentUserRole = UserRole.FIELD_ENGINEER, onAddOrder = {}, onUpdateOrderStatus = { _, _, _ -> }, onDeleteOrder = {},
                today = today, weeklyReports = emptyList(), onAddWeeklyReport = onAdd
            )
        }
    }

    @Test fun prompt_opensTheWeeklyForm() {
        var opened = false
        summary { opened = true }
        rule.onNodeWithText("No report yet for this week").assertExists()
        rule.onNodeWithText("Oct 26, 2026 - Nov 1, 2026").assertExists()
        rule.onNodeWithTag("summary_add_weekly_report").performClick()
        assertTrue(opened)
    }

    @Test fun noPrompt_forThoseWhoCannotSubmit() {
        summary(null)
        rule.onNodeWithText("No report yet for this week").assertDoesNotExist()
    }

    @Test fun construction_hiddenWithoutAPlan() {
        summary(null)
        rule.onNodeWithTag("summary_construction").assertDoesNotExist()
    }

    @Test fun construction_countsEachBand() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                SummarySection(
                    project, emptyList(), emptyList(), null, openIssues = 0, canEditProject = false, onEditProject = {},
                    currentUserRole = UserRole.ENGINEER_ADMIN, onAddOrder = {}, onUpdateOrderStatus = { _, _, _ -> }, onDeleteOrder = {},
                    today = today, construction = SdpSummary.Summary(listOf(18, 22, 40, 28, 12), SdpSummary.Totals(120, 6000.0))
                )
            }
        }
        rule.onNodeWithText("120 units").assertExists()
        rule.onNodeWithText("100% / RFO").assertExists()
        rule.onNodeWithText("40").assertExists()
    }

    @Test fun profileDetails_allShown() {
        summary(null)
        rule.onNodeWithText("JRC Builders").assertExists()
        rule.onNodeWithText("NHA Bulacan").assertExists()
    }
}
