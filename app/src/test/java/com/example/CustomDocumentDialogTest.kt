package com.example

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.model.AppThemeMode
import com.example.ui.dialogs.AddCustomDocumentDialog
import com.example.ui.theme.NHATheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomDocumentDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun suspensionWithBlankDays_saveDisabled() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddCustomDocumentDialog(initialType = "Work Suspension Order", onDismiss = {}, onSubmit = { _, _ -> })
            }
        }
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }
}
