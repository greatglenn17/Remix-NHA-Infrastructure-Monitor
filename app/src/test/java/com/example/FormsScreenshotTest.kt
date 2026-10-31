package com.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.data.model.SdpLot
import com.example.data.model.WeeklyReport
import com.example.ui.components.DiscardChangesDialog
import com.example.ui.detail.tabs.AddPaymentDialog
import com.example.ui.detail.tabs.RecordInspectionSnapshotDialog
import com.example.ui.dialogs.AddCustomDocumentDialog
import com.example.ui.dialogs.AddIssueDialog
import com.example.ui.dialogs.AddMonthlyReportDialog
import com.example.ui.dialogs.AddWeeklyReportDialog
import com.example.ui.dialogs.CreateProjectDialog
import com.example.ui.login.LoginScreen
import com.example.ui.theme.NHATheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Forms are dialog windows, so capture all windows. Baselines: ./gradlew recordRoborazziDebug
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val project = Project(
        id = 7, name = "Northville 8 Housing Units, Block 12 to Block 18 Concrete Roads and Drainage Works Phase 2",
        location = "Barangay Santa Rosa I, Marilao, Bulacan", implementingOffice = "NHA Bulacan District Office",
        contractor = "JRC Builders",
        scopeOfWork = """[{"name":"Site development","amount":6000000,"weightPct":60},{"name":"Drainage","amount":4000000,"weightPct":40}]""",
        contractCostOriginal = 10_000_000.0, contractCostRevised = 10_000_000.0, contractDurationDays = 300,
        dateStarted = "2026-01-05", completionDateOriginal = "2026-11-01", completionDateRevised = "-"
    )

    // One actual of 120 so the review step lists an error.
    private val badReport = WeeklyReport(
        id = 2, projectId = 7, reportingWeek = "Week 41: October 5 - October 11, 2026", daysElapsed = 280, remainingDays = 50,
        targetAccomplishmentPct = 62.0, actualAccomplishmentPct = 44.0,
        manpowerJson = "[]", equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]",
        accomplishmentItemsJson = """[{"itemDescription":"Site development","targetPct":60.0,"actualPct":120.0},{"itemDescription":"Drainage","targetPct":40.0,"actualPct":30.0}]""",
        documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = "Engr. R. Santos"
    )

    private val lot = SdpLot(id = 1, projectId = 7, sdpPlanId = 1, blockNumber = "12", lotNumber = "5", polygonNormalizedJson = "[]")

    private val today = java.time.LocalDate.of(2026, 10, 27)
    private var current: @Composable () -> Unit = {}
    private var tick by mutableStateOf(0)

    private fun settle() = repeat(6) { rule.mainClock.advanceTimeBy(500); org.robolectric.shadows.ShadowLooper.idleMainLooper() }

    private fun shoot(name: String, steps: () -> Unit = {}, content: @Composable () -> Unit) {
        current = content; tick++
        settle(); steps(); settle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    private fun all(width: String, mode: AppThemeMode) {
        val t = "${width}_${mode.name.lowercase()}"
        rule.mainClock.autoAdvance = false
        rule.setContent { NHATheme(themeMode = mode) { tick; current() } }
        shoot("form_weekly_week_$t") { AddWeeklyReportDialog(project, 7, null, {}, { _, _, _, _, _, _, _, _, _, _, _, _ -> }, today) }
        shoot("form_weekly_review_$t", steps = {
            rule.onNodeWithText("Review and save").performSemanticsAction(SemanticsActions.OnClick)
        }) { AddWeeklyReportDialog(project, 7, badReport, {}, { _, _, _, _, _, _, _, _, _, _, _, _ -> }, today) }
        shoot("form_monthly_$t") { AddMonthlyReportDialog(project, emptyList(), project.scopeOfWork, 10_000_000.0, null, {}, { _, _, _, _, _, _, _, _, _ -> }) }
        shoot("form_create_project_$t") { CreateProjectDialog({}, "Engr. R. Santos", { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }, today) }
        shoot("form_lot_inspection_$t") { RecordInspectionSnapshotDialog(lot, null, "JRC Builders", {}) { _, _, _, _, _, _, _ -> } }
        shoot("form_add_billing_$t") { AddPaymentDialog(10_000_000.0, 4_000_000.0, {}, { _, _, _, _, _, _ -> }, today) }
        shoot("form_variation_order_$t") { AddCustomDocumentDialog("Variation Order", {}) { _, _ -> } }
        shoot("form_work_suspension_$t") { AddCustomDocumentDialog("Work Suspension Order", {}) { _, _ -> } }
        shoot("form_issue_$t") { AddIssueDialog(null, {}, { _, _, _, _, _, _, _ -> }, today) }
        shoot("login_$t") { LoginScreen(onLoginSuccess = { _, _, _, _, _, _, _ -> }) }
    }

    @Config(qualifiers = "w411dp-h891dp") @Test fun phone_light() {
        all("411", AppThemeMode.LIGHT)
        shoot("form_discard_411_light") { DiscardChangesDialog({}, {}) }
    }
    @Config(qualifiers = "w411dp-h891dp") @Test fun phone_dark() = all("411", AppThemeMode.DARK)
    @Config(qualifiers = "w743dp-h1185dp") @Test fun tablet_portrait_light() = all("743", AppThemeMode.LIGHT)
    @Config(qualifiers = "w743dp-h1185dp") @Test fun tablet_portrait_dark() = all("743", AppThemeMode.DARK)
    @Config(qualifiers = "w1185dp-h743dp") @Test fun tablet_landscape_light() = all("1185", AppThemeMode.LIGHT)
    @Config(qualifiers = "w1185dp-h743dp") @Test fun tablet_landscape_dark() = all("1185", AppThemeMode.DARK)
}
