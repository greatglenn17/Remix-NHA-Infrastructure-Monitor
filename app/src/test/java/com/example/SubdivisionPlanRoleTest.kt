package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.data.model.SdpLot
import com.example.data.model.SdpPlan
import com.example.data.model.UserRole
import com.example.ui.detail.tabs.SubdivisionPlanTab
import com.example.ui.theme.NHATheme
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// A viewer sees the plan and its numbers but none of the controls that change lots.
@RunWith(RobolectricTestRunner::class)
class SubdivisionPlanRoleTest {
    @get:Rule val rule = createComposeRule()

    private val project = Project(
        id = 1, name = "Test", location = "", implementingOffice = "", contractor = "", scopeOfWork = "[]",
        contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 0,
        dateStarted = "2026-01-01", completionDateOriginal = "2026-12-31", completionDateRevised = "-"
    )
    private val plan = SdpPlan(id = 1, projectId = 1, planName = "Plan", pdfFileUrl = "", uploadedDate = "2026-10-29", uploadedBy = "x", scaleDenominator = 1000)
    private val lot = SdpLot(id = 1, projectId = 1, sdpPlanId = 1, blockNumber = "1", lotNumber = "1", polygonNormalizedJson = "[]")

    private fun show(role: UserRole, onDeletePlan: (SdpPlan) -> Unit = {}) = rule.setContent {
        NHATheme(themeMode = AppThemeMode.LIGHT) {
            SubdivisionPlanTab(
                project = project, sdpPlans = listOf(plan), activeSdpPlan = plan, activeLots = listOf(lot), activeRoads = emptyList(),
                lotProgressMap = emptyMap(), currentUserRole = role,
                onUploadSdpPlan = { _, _, _ -> }, onSelectActiveVersion = {}, onAddLot = { _, _, _, _, _, _, _ -> },
                onUpdateLot = {}, onDeactivateLot = {}, onAddRoad = { _, _, _ -> }, onUpdateRoad = {}, onDeactivateRoad = {},
                onUpdateLotProgress = { _, _, _, _, _, _, _, _, _, _ -> }, onUpdateLotBillingStatus = { _, _, _, _, _ -> },
                onRecordInspection = { _, _, _, _, _, _, _, _ -> }, onGetInspectionsForLot = { emptyFlow() },
                onDeletePlan = onDeletePlan
            )
        }
    }

    @Test
    fun viewerHasNoEditControls() {
        show(UserRole.VIEWER)
        for (label in listOf("Upload Revised", "EDIT SDP", "TAP TO ADD", "Scale 1:1000 (change)")) {
            rule.onAllNodesWithText(label).assertCountEquals(0)
        }
        rule.onNodeWithText("Scale 1:1000").assertExists()
        rule.onAllNodesWithContentDescription("Plan options").assertCountEquals(0)
    }

    @Test
    fun engineerAdminHasThem() {
        show(UserRole.ENGINEER_ADMIN)
        // TAP TO ADD also needs the plan's PDF on the phone, which this test plan has none of.
        for (label in listOf("Upload Revised", "EDIT SDP", "Scale 1:1000 (change)")) {
            rule.onNodeWithText(label).assertExists()
        }
    }

    // Delete asks first, says what goes and that it cannot be undone, then deletes the plan on screen.
    @Test
    fun engineerAdminDeletesThePlanAfterConfirming() {
        var deleted: SdpPlan? = null
        show(UserRole.ENGINEER_ADMIN) { deleted = it }
        rule.onNodeWithContentDescription("Plan options").performClick()
        rule.onNodeWithText("Rotate plan").assertExists()
        rule.onNodeWithText("Delete plan").performClick()
        rule.onNodeWithText("Delete SDP v1?").assertExists()
        rule.onNodeWithText("its 1 lots, with their progress", substring = true).assertExists()
        rule.onNodeWithText("cannot be undone", substring = true).assertExists()
        assertNull(deleted)
        rule.onNodeWithText("Delete").performClick()
        assertEquals(plan, deleted)
    }
}
