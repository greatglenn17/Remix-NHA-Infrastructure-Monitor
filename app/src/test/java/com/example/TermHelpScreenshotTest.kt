package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.model.AppThemeMode
import com.example.ui.dashboard.TermHelpDialog
import com.example.ui.theme.NHATheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TermHelpScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun term_help_phone_light() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { TermHelpDialog(onDismiss = {}) } }
        rule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/term_help_phone_light.png")
    }
}
