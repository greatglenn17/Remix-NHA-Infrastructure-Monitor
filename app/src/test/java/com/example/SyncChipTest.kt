package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.data.model.AppThemeMode
import com.example.data.model.DefaultUserAccount
import com.example.ui.components.SyncChip
import com.example.ui.components.TopAppBarHeader
import com.example.ui.components.SyncDialog
import com.example.ui.components.label
import com.example.ui.components.lastSyncedText
import com.example.ui.theme.NHATheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertTrue
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The header chip says whether this phone's changes reached the cloud. Baselines: ./gradlew recordRoborazziDebug
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SyncChipTest {

    @Test
    fun chipFollowsTheUploadQueue() {
        assertEquals(SyncChip.PhoneOnly, SyncChip.of(cloudSignedIn = false, pending = 3, failed = 0))
        assertEquals(SyncChip.Synced, SyncChip.of(true, 0, 0))
        assertEquals(SyncChip.Uploading(2), SyncChip.of(true, 2, 0))
        // One failure means the phone is likely offline: everything not uploaded counts as waiting.
        assertEquals(SyncChip.Waiting(3), SyncChip.of(true, 2, 1))
        assertEquals("1 CHANGE WAITING", SyncChip.Waiting(1).label)
        assertEquals("3 CHANGES WAITING", SyncChip.Waiting(3).label)
        assertEquals("UPLOADING 2", SyncChip.Uploading(2).label)
    }

    private val states = listOf(SyncChip.Synced, SyncChip.Uploading(2), SyncChip.Waiting(3), SyncChip.PhoneOnly)

    @get:Rule val rule = createComposeRule()

    private fun shoot(mode: AppThemeMode) {
        // The Uploading icon spins forever, so the clock is stepped by hand instead of waiting for idle.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            NHATheme(themeMode = mode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column { states.forEach { TopAppBarHeader(currentUser = DefaultUserAccount, onSwitchUser = {}, sync = it) } }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onRoot().captureRoboImage("src/test/screenshots/header_sync_chip_411_${mode.name.lowercase()}.png")
    }

    @Config(qualifiers = "w411dp-h891dp") @Test fun header_light() = shoot(AppThemeMode.LIGHT)
    @Config(qualifiers = "w411dp-h891dp") @Test fun header_dark() = shoot(AppThemeMode.DARK)

    @Test
    fun lastSyncedText_readable() {
        val at = Instant.parse("2026-11-01T02:42:00Z").toEpochMilli()
        assertEquals("Last synced Nov 1, 2026, 10:42 AM", lastSyncedText(at, ZoneId.of("Asia/Manila")))
        assertEquals("Not fully synced on this phone yet", lastSyncedText(null))
    }

    @Test
    fun tappingTheChip_offersRetryWhileChangesWait() {
        var retried = false
        rule.mainClock.autoAdvance = false
        rule.setContent { NHATheme { TopAppBarHeader(currentUser = DefaultUserAccount, onSwitchUser = {}, sync = SyncChip.Waiting(3), onRetrySync = { retried = true }) } }
        rule.onNodeWithText("3 CHANGES WAITING").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Not fully synced on this phone yet").assertExists()
        rule.onNodeWithText("Retry now").performClick()
        assertTrue(retried)
    }

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun waiting_dialog_light() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                SyncDialog(SyncChip.Waiting(3), "Last synced Oct 31, 2026, 4:10 PM", onRetry = {}, onDismiss = {})
            }
        }
        rule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/sync_dialog_waiting_light.png")
    }
}
