package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.BlueBookPrint
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Email opens the phone's email app, not "No apps can perform this action". Needs Gmail on the device.
@RunWith(AndroidJUnit4::class)
class EmailAppTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emailOpensGmail() {
        rule.runOnUiThread { BlueBookPrint.email(rule.activity, "Weekly Report - Test", "<html><body>Report</body></html>") }
        val deadline = System.currentTimeMillis() + 30_000
        var top = ""
        while (System.currentTimeMillis() < deadline) {
            top = shell("dumpsys activity activities").lines().firstOrNull { "topResumedActivity" in it }.orEmpty()
            if ("com.google.android.gm" in top) break
            Thread.sleep(500)
        }
        assertTrue("Gmail not opened, top is: $top", "com.google.android.gm" in top)
    }

    private fun shell(cmd: String): String =
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).let { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        }
}
