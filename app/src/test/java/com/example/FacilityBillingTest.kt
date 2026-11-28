package com.example

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import com.example.data.model.AppThemeMode
import com.example.data.model.ProjectPayment
import com.example.data.model.facilityDeductions
import com.example.data.model.paidAmount
import com.example.ui.detail.tabs.AddPaymentDialog
import com.example.ui.theme.NHATheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// A community facility's billing: the gross fills retention, recoupment, VAT and withholding (logic.test.mjs checks the same figures).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FacilityBillingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun deductionsToTheCentavo() {
        assertEquals(listOf(150_000.0, 44_642.86, 17_857.14), facilityDeductions(1_000_000.0))
        val p = ProjectPayment(projectId = 1, name = "", date = "", periodCovered = "", grossAmount = 1_000_000.0, percentage = 0.0, balanceAmount = 0.0,
            balancePercentage = 0.0, retentionPct = 10.0, recoupmentAmount = 150_000.0, vatAmount = 44_642.86, withholdingAmount = 17_857.14)
        assertEquals(687_500.0, p.paidAmount, 1e-6)
    }

    @Test fun typingTheGrossFillsTheDeductionsAndEachStaysEditable() {
        val saved = mutableListOf<ProjectPayment>()
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) { AddPaymentDialog(10_000_000.0, 0.0, {}, { saved += it }, isFacility = true) }
        }
        rule.onAllNodes(hasSetTextAction() and hasText("Gross Amount (PHP)")).onFirst().performTextReplacement("1000000")
        rule.onNodeWithTag("billing_deduction_0").assertTextContains("150000.00")
        rule.onNodeWithTag("billing_deduction_1").assertTextContains("44642.86")
        rule.onNodeWithTag("billing_deduction_2").assertTextContains("17857.14")
        rule.onNodeWithText("₱687,500.00", substring = true).assertExists()
        // The official billing rounded the VAT differently: the typed figure is kept and the net follows it.
        rule.onNodeWithTag("billing_deduction_1").performTextReplacement("44642.00")
        rule.onNodeWithText("Save Payment").performClick()
        rule.waitForIdle()
        val p = saved.single()
        assertEquals(10.0, p.retentionPct, 0.0)
        assertEquals(150_000.0, p.recoupmentAmount, 0.0)
        assertEquals(44_642.0, p.vatAmount, 0.0)
        assertEquals(17_857.14, p.withholdingAmount, 0.0)
        assertEquals(687_500.86, p.paidAmount, 1e-6)
    }
}
