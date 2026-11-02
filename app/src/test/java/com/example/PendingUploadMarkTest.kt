package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.example.data.model.AppThemeMode
import com.example.data.model.ProjectPayment
import com.example.data.model.UserRole
import com.example.data.model.WeeklyReport
import com.example.ui.components.LocalPendingUploads
import com.example.ui.detail.sections.PaymentCard
import com.example.ui.detail.tabs.WeeklyReportsTab
import com.example.ui.theme.NHATheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Records this phone saved and the cloud has not confirmed yet say "Waiting to upload". Baselines: ./gradlew recordRoborazziDebug
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class PendingUploadMarkTest {
    @get:Rule val rule = createComposeRule()

    private fun report(id: Long, day: Int) = WeeklyReport(
        id = id, projectId = 7, reportingWeek = "Week $id", daysElapsed = day, remainingDays = 330 - day,
        targetAccomplishmentPct = 50.0, actualAccomplishmentPct = 48.0,
        manpowerJson = "[]", equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]",
        accomplishmentItemsJson = "[]", documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]",
        submittedByStaff = "Engr. R. Santos"
    )

    @Test
    fun onlyTheQueuedRecordsAreMarked() {
        val w41 = report(41, 280)
        val paid = ProjectPayment(projectId = 7, name = "1st billing", dvNo = "101", date = "2026-10-01", periodCovered = "Sep 2026", grossAmount = 1000.0, percentage = 10.0, balanceAmount = 9000.0, balancePercentage = 90.0)
        val queued = ProjectPayment(projectId = 7, name = "2nd billing", dvNo = "102", date = "2026-10-20", periodCovered = "Oct 2026", grossAmount = 1000.0, percentage = 10.0, balanceAmount = 8000.0, balancePercentage = 80.0)
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                CompositionLocalProvider(LocalPendingUploads provides setOf(w41.uuid, queued.uuid)) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column {
                            PaymentCard(paid, {}, {}, {})
                            PaymentCard(queued, {}, {}, {})
                            WeeklyReportsTab(listOf(w41, report(40, 273)), UserRole.ENGINEER_ADMIN, {}, {}, {})
                        }
                    }
                }
            }
        }
        rule.onAllNodesWithText("Waiting to upload").assertCountEquals(2)
        captureScreenRoboImage("src/test/screenshots/pending_upload_mark_411_light.png")
    }

    @Test
    fun nothingIsMarkedWithoutTheQueue() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                WeeklyReportsTab(listOf(report(41, 280)), UserRole.ENGINEER_ADMIN, {}, {}, {})
            }
        }
        rule.onAllNodesWithText("Waiting to upload").assertCountEquals(0)
    }
}
