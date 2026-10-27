package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AppThemeMode
import com.example.ui.components.LongForm
import com.example.ui.theme.NHATheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormKitTest {
    @get:Rule val rule = createComposeRule()
    private var dismissed = 0

    private fun show(dirty: Boolean, saveEnabled: Boolean = true) {
        dismissed = 0
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                LongForm(
                    title = "T", dirty = dirty, saveEnabled = saveEnabled, onSave = {},
                    onDismiss = { dismissed++ }, saveTag = "save"
                ) { Text("body") }
            }
        }
    }

    @Test fun clean_closeDismissesAtOnce() {
        show(false)
        rule.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, dismissed)
        rule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test fun dirty_closeAsks() {
        show(true)
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithText("Discard changes?").assertExists()
        assertEquals(0, dismissed)
    }

    @Test fun dirty_keepEditing_staysOpen() {
        show(true)
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertEquals(0, dismissed)
    }

    @Test fun dirty_discard_dismisses() {
        show(true)
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithText("Discard").performClick()
        assertEquals(1, dismissed)
    }

    @Test fun save_disabled_whenNotEnabled() {
        show(false, saveEnabled = false)
        rule.onNodeWithTag("save").assertIsNotEnabled()
    }
}
