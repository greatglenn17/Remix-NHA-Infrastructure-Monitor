package com.example

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import com.example.data.model.AppThemeMode
import com.example.data.model.DeveloperRequest
import com.example.data.model.ProjectPayment
import com.example.data.model.UserRole
import com.example.data.model.billingText
import com.example.data.model.pendingBillingRequests
import com.example.ui.detail.sections.MoneySection
import com.example.ui.theme.NHATheme
import com.example.util.ProjectAttention
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// A request for billing (web/logic.test.mjs "developer request edits"): it waits under Payments until a billing with its DV is saved from it.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BillingRequestsTest {
    @get:Rule val rule = createComposeRule()

    private val asked = DeveloperRequest(
        projectId = 1, requestType = ProjectAttention.BILLING_REQUEST, dateRequested = "2026-10-30", dateReceived = "2026-11-01",
        billingType = "Land acquisition", units = 12, uuid = "r1"
    )
    private fun billing(uuid: String) = ProjectPayment(
        projectId = 1, name = "B", date = "2026-11-02", periodCovered = "", grossAmount = 1.0, percentage = 0.0, balanceAmount = 0.0, balancePercentage = 0.0, uuid = uuid
    )

    @Test fun waitsUntilABillingIsSavedFromIt() {
        assertEquals("12 lots · Land acquisition", asked.billingText)
        assertEquals("1 housing unit · M2 - Housing unit", asked.copy(units = 1, billingType = "M2 - Housing unit").billingText)
        val requests = listOf(asked, asked.copy(uuid = "r2", paymentUuid = "p1"), asked.copy(uuid = "r3", paymentUuid = "gone"), asked.copy(uuid = "r4", requestType = "Time Extension"))
        // One whose billing was deleted waits again; other requests never do.
        assertEquals(listOf("r1", "r3"), pendingBillingRequests(requests, listOf(billing("p1"))).map { it.uuid })
    }

    @Test fun markBilledNeedsTheDvNoAndTheScannedDv() {
        val added = mutableListOf<ProjectPayment>()
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                MoneySection(
                    payments = emptyList(), contractCost = 10_000_000.0, actualPercent = 50.0, currentUserRole = UserRole.SUPER_ADMIN, isHousing = true,
                    onAddPayment = { added += it }, onUpdatePayment = {}, onDeletePayment = {}, billingRequests = listOf(asked)
                )
            }
        }
        rule.onNodeWithText("12 lots · Land acquisition").assertExists()
        rule.onNodeWithTag("mark_billed_r1").performClick()
        // The form starts from the request: its type and its 12 lots.
        rule.onNodeWithText("Mark the billing request billed").assertExists()
        rule.onAllNodes(hasSetTextAction() and hasText("12")).onFirst().assertExists()
        rule.onNodeWithTag("billing_dv_missing").assertExists()
        rule.onNodeWithText("Save Payment").assertIsNotEnabled()
        rule.onAllNodes(hasSetTextAction() and hasText("DV No. (Disbursement Voucher)")).onFirst().performTextReplacement("DV-1")
        rule.onNodeWithText("Attach the scanned DV form.").assertExists()
        rule.onNodeWithText("Save Payment").assertIsNotEnabled()
        assertEquals(0, added.size)
    }
}
