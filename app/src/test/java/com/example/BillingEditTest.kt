package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AppThemeMode
import com.example.data.model.ProjectPayment
import com.example.data.model.netAmount
import com.example.data.model.netBillings
import com.example.ui.detail.sections.PaymentCard
import com.example.ui.detail.sections.editedBillings
import com.example.ui.theme.NHATheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A billing's ⋮ menu edits all of it; a changed amount keeps the running balances right.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class BillingEditTest {
    @get:Rule val rule = createComposeRule()

    private fun billing(id: Long, gross: Double, balance: Double) = ProjectPayment(
        id = id, projectId = 7, name = "Billing $id", dvNo = "10$id", date = "2026-10-0$id", periodCovered = "Oct 2026",
        grossAmount = gross, percentage = gross / 100, balanceAmount = balance, balancePercentage = balance / 100
    )
    // Contract 10,000: billed 1,000, 2,000, 3,000 in that order.
    private val first = billing(1, 1000.0, 9000.0)
    private val second = billing(2, 2000.0, 7000.0)
    private val third = billing(3, 3000.0, 4000.0)
    private val all = listOf(first, second, third)

    @Test
    fun menu_offersEdit() {
        var editing = false
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                PaymentCard(first, {}, {}, {}, onEdit = { editing = true })
            }
        }
        rule.onNodeWithContentDescription("More actions").performClick()
        rule.onNodeWithText("Edit").performClick()
        assertEquals(true, editing)
    }

    @Test
    fun sameAmount_savesOnlyThatBilling() {
        val renamed = second.copy(name = "2nd partial billing", dvNo = "DV-2026-10-0002")
        assertEquals(listOf(renamed), editedBillings(all, second, renamed, 10_000.0))
    }

    @Test
    fun newAmount_movesItsOwnAndLaterBalances() {
        val raised = second.copy(grossAmount = 2500.0)
        val saved = editedBillings(all, second, raised, 10_000.0)
        assertEquals(listOf(2L, 3L), saved.map { it.id })
        assertEquals(25.0, saved[0].percentage, 1e-9)
        assertEquals(listOf(6500.0, 3500.0), saved.map { it.balanceAmount })
        assertEquals(listOf(65.0, 35.0), saved.map { it.balancePercentage })
    }

    @Test
    fun retention_leavesTheNetAndShowsIt() {
        val held = first.copy(billingType = "M1 - Housing unit", units = 4, unitPrice = 250.0, retentionPct = 10.0)
        assertEquals(900.0, held.netAmount, 1e-9)
        assertEquals(1000.0, first.netAmount, 1e-9)
        rule.setContent { NHATheme(themeMode = AppThemeMode.LIGHT) { PaymentCard(held, {}, {}, {}) } }
        rule.onNodeWithText("M1 - Housing unit · 4 housing units × ₱250.00").assertExists()
        rule.onNodeWithText("Retention (10%)").assertExists()
    }
    // Shares and balances on the net: a billing with retention lowers its share, and its balance and every later one rise by what is held.
    @Test
    fun netBillings_putShareAndBalanceOnTheNet() {
        val held = listOf(first, second.copy(retentionPct = 10.0), third)
        val net = netBillings(held.reversed()).associateBy { it.id }
        assertEquals(listOf(10.0, 18.0, 30.0), listOf(1L, 2L, 3L).map { net.getValue(it).percentage })
        assertEquals(listOf(9000.0, 7200.0, 4200.0), listOf(1L, 2L, 3L).map { net.getValue(it).balanceAmount })
        assertEquals(listOf(90.0, 72.0, 42.0), listOf(1L, 2L, 3L).map { net.getValue(it).balancePercentage })
        assertEquals("the gross stays", 2000.0, net.getValue(2L).grossAmount, 1e-9)
    }
}
