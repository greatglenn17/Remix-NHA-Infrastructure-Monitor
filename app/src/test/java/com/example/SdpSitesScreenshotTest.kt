package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.data.model.SdpLot
import com.example.data.model.SdpPlan
import com.example.data.model.UserRole
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.detail.tabs.AddSdpPlanDialog
import com.example.ui.detail.tabs.SubdivisionPlanTab
import com.example.ui.theme.NHATheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SdpSitesScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val project = Project(
        id = 1, name = "Bulacan Heights", location = "", implementingOffice = "", contractor = "", scopeOfWork = "[]",
        contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 0,
        dateStarted = "2026-01-01", completionDateOriginal = "2026-12-31", completionDateRevised = "-"
    )
    private val phase1 = SdpPlan(id = 1, projectId = 1, planName = "Phase 1 SDP", pdfFileUrl = "", version = 1, isActive = true, uploadedDate = "2026-10-01", uploadedBy = "x", scaleDenominator = 1000, siteName = "Phase 1")
    private val phase2 = SdpPlan(id = 2, projectId = 1, planName = "Phase 2 SDP", pdfFileUrl = "", version = 1, isActive = true, uploadedDate = "2026-10-20", uploadedBy = "x", scaleDenominator = 1000, siteName = "Phase 2")
    private val lot = SdpLot(id = 1, projectId = 1, sdpPlanId = 2, blockNumber = "1", lotNumber = "1", polygonNormalizedJson = "[]")

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun sdp_sites_phone_light() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                SubdivisionPlanTab(
                    project = project, sdpPlans = listOf(phase2, phase1), activeSdpPlan = phase2, activeLots = listOf(lot), activeRoads = emptyList(),
                    lotProgressMap = emptyMap(), currentUserRole = UserRole.ENGINEER_ADMIN,
                    onUploadSdpPlan = { _, _, _ -> }, onSelectActiveVersion = {}, onAddLot = { _, _, _, _, _, _, _ -> },
                    onUpdateLot = {}, onDeactivateLot = {}, onAddRoad = { _, _, _ -> }, onUpdateRoad = {}, onDeactivateRoad = {},
                    onUpdateLotProgress = { _, _, _, _, _, _, _, _, _, _ -> }, onUpdateLotBillingStatus = { _, _, _, _, _ -> },
                    onRecordInspection = { _, _, _, _, _, _, _, _ -> }, onGetInspectionsForLot = { emptyFlow() }
                )
            }
        }
        rule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/sdp_sites_phone_light.png")
    }

    // Adding a second site to a project whose first plan has no site name yet. Default size: at
    // w411dp-h891dp this dialog never goes idle under Robolectric, with or without the site choice.
    @Test fun sdp_add_site_dialog_light() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddSdpPlanDialog(currentVersion = 2, onDismiss = {}, onSubmit = { _, _, _ -> }, currentSite = "", takenSites = listOf(""))
            }
        }
        rule.onNodeWithText("Another site under this contract").performClick()
        rule.onNodeWithText("Name of the new site").assertExists()
        captureScreenRoboImage("src/test/screenshots/sdp_add_site_dialog_light.png")
    }
}
