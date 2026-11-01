package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AppThemeMode
import com.example.data.model.UserRole
import com.example.data.model.WeeklyReport
import com.example.ui.detail.tabs.WeeklyReportsTab
import com.example.ui.detail.tabs.previousReport
import com.example.ui.theme.NHATheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Weekly report cards keep Print, Edit and Delete in one menu. Baselines: ./gradlew recordRoborazziDebug
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class WeeklyReportCardTest {
    @get:Rule val rule = createComposeRule()

    private fun report(id: Long, day: Int, actual: Double) = WeeklyReport(
        id = id, projectId = 7, reportingWeek = "Week $id", daysElapsed = day, remainingDays = 330 - day,
        targetAccomplishmentPct = 50.0, actualAccomplishmentPct = actual,
        manpowerJson = "[]", equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]",
        accomplishmentItemsJson = "[]", documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]",
        submittedByStaff = "Engr. R. Santos"
    )

    @Test
    fun previousReport_goesByWeekNotListOrder() {
        // Week 40 was added late, so it sits first in the newest-added-first list.
        val w40 = report(40, 273, 43.0)
        val w41 = report(41, 280, 44.0)
        val w39 = report(39, 266, 41.0)
        val list = listOf(w40, w41, w39)
        assertEquals(w40, previousReport(list, w41))
        assertEquals(w39, previousReport(list, w40))
        assertNull(previousReport(list, w39))
    }

    @Test
    fun menu_deletesOnlyAfterConfirming() {
        var deleted: WeeklyReport? = null
        var printed = false
        val w41 = report(41, 280, 44.0)
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    WeeklyReportsTab(listOf(w41, report(40, 273, 43.0)), UserRole.ENGINEER_ADMIN, {}, {}, { deleted = it }, { printed = true })
                }
            }
        }
        rule.onNodeWithText("+1.00% from previous report", substring = true).assertExists()
        rule.onAllNodesWithContentDescription("Report actions")[0].performClick()
        rule.onNodeWithText("Edit").assertExists()
        captureScreenRoboImage("src/test/screenshots/weekly_card_menu_411_light.png")
        rule.onNodeWithText("Print").performClick()
        assertEquals(true, printed)

        rule.onAllNodesWithContentDescription("Report actions")[0].performClick()
        rule.onNodeWithText("Delete").performClick()
        assertNull(deleted)
        rule.onNodeWithText("Are you sure you want to delete this report?").assertExists()
        rule.onAllNodesWithText("Delete").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        assertEquals(w41, deleted)
    }

    @Test
    fun menu_fieldEngineerCannotDelete() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                WeeklyReportsTab(listOf(report(41, 280, 44.0)), UserRole.FIELD_ENGINEER, {}, {}, {}, {})
            }
        }
        rule.onNodeWithContentDescription("Report actions").performClick()
        rule.onNodeWithText("Print").assertExists()
        rule.onNodeWithText("Delete").assertDoesNotExist()
    }
}
