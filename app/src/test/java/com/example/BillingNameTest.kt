package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AppThemeMode
import com.example.data.model.ProjectPayment
import com.example.ui.detail.sections.PaymentCard
import com.example.ui.detail.sections.renamedBilling
import com.example.ui.theme.NHATheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A billing's ⋮ menu renames it; the amounts, DV and scan stay as they are.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class BillingNameTest {
    @get:Rule val rule = createComposeRule()

    private val payment = ProjectPayment(
        id = 3, projectId = 7, name = "1st billing", dvNo = "101", date = "2026-10-01", periodCovered = "Sep 2026",
        grossAmount = 1000.0, percentage = 10.0, balanceAmount = 9000.0, balancePercentage = 90.0
    )

    @Test
    fun menu_offersEditName() {
        var editing = false
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                PaymentCard(payment, {}, {}, {}, onEditName = { editing = true })
            }
        }
        rule.onNodeWithContentDescription("More actions").performClick()
        rule.onNodeWithText("Edit name").performClick()
        assertEquals(true, editing)
    }

    @Test
    fun rename_trimsAndSkipsBlankOrUnchanged() {
        assertEquals("1st partial billing", renamedBilling("1st billing", "  1st partial billing "))
        assertNull(renamedBilling("1st billing", "   "))
        assertNull(renamedBilling("1st billing", " 1st billing "))
    }
}
