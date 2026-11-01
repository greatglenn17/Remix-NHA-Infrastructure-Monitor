package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.model.AppThemeMode
import com.example.ui.detail.SectionChips
import com.example.ui.detail.sections.ChipRow
import com.example.ui.theme.NHATheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The section views sit on one scrolling line; the open one must be on screen.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class ChipRowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lastView_isScrolledIntoSight() {
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ChipRow(SectionChips.documents(true), "Audit log") {} } }
        rule.onNodeWithTag("section_chip_Audit log").assertIsDisplayed()
    }

    @Test fun tappingAView_selectsIt() {
        var picked = ""
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { ChipRow(SectionChips.progress(true), "Weekly") { picked = it } } }
        rule.onNodeWithTag("section_chip_Monthly").performClick()
        assertEquals("Monthly", picked)
    }
}
