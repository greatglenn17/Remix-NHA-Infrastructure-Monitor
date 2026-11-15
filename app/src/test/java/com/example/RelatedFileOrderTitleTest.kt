package com.example

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.AppThemeMode
import com.example.ui.detail.tabs.AddRelatedDocumentDialog
import com.example.ui.theme.NHATheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelatedFileOrderTitleTest {
    @get:Rule val rule = createComposeRule()

    // A related file named like an order would be read as an order by every phone and the website.
    @Test
    fun aTitleThatNamesAnOrderIsRefused() {
        var saved: String? = null
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddRelatedDocumentDialog(onDismiss = {}, onAddDocument = { title, _, _, _ -> saved = title })
            }
        }
        rule.onNodeWithText("Save Document").assertIsEnabled()
        rule.onNodeWithTag("dropdown_doc_type").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Other / Specific Custom Document...").performClick()
        rule.onNodeWithTag("input_related_doc_title").performTextInput("Request Letter for time extension")
        rule.onNodeWithText("Save Document").assertIsNotEnabled()
        rule.onNodeWithText("Add it as an order instead.", substring = true).assertExists()
        rule.onNodeWithTag("input_related_doc_title").performTextReplacement("Extension of time request")
        rule.onNodeWithText("Add it as an order instead.", substring = true).assertDoesNotExist()
        rule.onNodeWithText("Save Document").assertIsEnabled().performClick()
        assertEquals("Extension of time request", saved)
    }
}
