package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.model.AppThemeMode
import com.example.data.model.SdpLot
import com.example.data.model.WeeklyReport
import com.example.data.model.isUnassigned
import com.example.data.model.label
import com.example.ui.detail.tabs.PhotoGalleryTab
import com.example.ui.detail.tabs.UpdateLotProgressDialog
import com.example.ui.detail.tabs.isLotMatchingFilter
import com.example.ui.theme.NHATheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnassignedLotsAndPhotosTest {
    @get:Rule val rule = createComposeRule()

    private fun lot(block: String, lotNo: String) =
        SdpLot(id = 1, projectId = 1, sdpPlanId = 1, blockNumber = block, lotNumber = lotNo, polygonNormalizedJson = "[]")

    @Test
    fun foundLotsReadUnassignedUntilNamed() {
        assertTrue(lot("", "").isUnassigned)
        assertEquals("Unassigned lot", lot("", "").label)
        assertFalse(lot("3", "12").isUnassigned)
        assertEquals("Block 3 Lot 12", lot("3", "12").label)
        // Search finds them by "unassigned", and they don't match every "block"/"lot" query.
        assertTrue(isLotMatchingFilter(lot("", ""), "unassigned", "ALL", "ALL", "ALL", emptyMap()))
        assertFalse(isLotMatchingFilter(lot("", ""), "block", "ALL", "ALL", "ALL", emptyMap()))
        assertTrue(isLotMatchingFilter(lot("3", "12"), "b3-l12", "ALL", "ALL", "ALL", emptyMap()))
    }

    @Test
    fun unassignedLotSavesProgressWithoutAName_butNotWithHalfOne() {
        var renamedTo: Pair<String, String>? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                UpdateLotProgressDialog(
                    lot = lot("", ""), currentProgress = null, defaultContractor = "", canManageBilling = true,
                    onDismiss = {}, onSubmitProgress = { _, _, _, _, _, _, _, _, _, _ -> }, onToggleBillingRequested = { _, _ -> },
                    onRename = { b, l -> renamedTo = b to l }
                )
            }
        }
        rule.onNodeWithText("Unassigned lot Construction & Billing").assertExists()
        rule.onNodeWithText("SAVE PROGRESS").assertIsEnabled()
        rule.onNodeWithTag("lot_block_name").performTextInput("7")
        rule.onNodeWithText("SAVE PROGRESS").assertIsNotEnabled()
        rule.onNodeWithTag("lot_lot_name").performTextInput("4")
        rule.onNodeWithText("SAVE PROGRESS").assertIsEnabled().performClick()
        assertEquals("7" to "4", renamedTo)
    }

    private val report = WeeklyReport(
        id = 5, projectId = 1, reportingWeek = "Week 3", daysElapsed = 0, remainingDays = 0,
        targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0, manpowerJson = "[]", equipmentJson = "[]",
        activitiesJson = "[]", issuesJson = "[]", accomplishmentItemsJson = "[]",
        documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[\"a.jpg\"]", submittedByStaff = "S"
    )

    @Test
    fun galleryPhotoIsDeletedOnlyAfterConfirming() {
        var deleted: Pair<Long, String>? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                PhotoGalleryTab(listOf(report), onDeletePhoto = { r, url -> deleted = r.id to url })
            }
        }
        rule.onNodeWithText("Week 3  ·  1 photo").assertExists()
        rule.onNodeWithTag("photo_viewer_image").assertDoesNotExist()
        rule.onAllNodesWithTag("photo_delete_button").assertCountEquals(0)
        rule.onNodeWithText("PROJECT UPDATE PHOTO GALLERY (1 Photos)").assertExists()
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Project Photo")).performClick()
        rule.onNodeWithTag("photo_delete_button").performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(null, deleted)
        rule.onNodeWithTag("photo_delete_button").performClick()
        rule.onNodeWithTag("photo_delete_confirm").performClick()
        assertEquals(5L to "a.jpg", deleted)
    }

    @Test
    fun noDeleteButtonForUsersWhoCantEditReports() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) { PhotoGalleryTab(listOf(report)) }
        }
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Project Photo")).performClick()
        rule.onNodeWithTag("photo_viewer_image").assertExists()
        rule.onAllNodesWithTag("photo_delete_button").assertCountEquals(0)
    }
}
