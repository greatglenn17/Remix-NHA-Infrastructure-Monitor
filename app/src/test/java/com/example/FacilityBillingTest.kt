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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.ADVANCE_PAYMENT
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.data.model.advanceAmount
import com.example.data.model.advanceLeft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    private fun billing(gross: Double, recoup: Double = 0.0, type: String = "", uuid: String = java.util.UUID.randomUUID().toString(), projectId: Long = 1) =
        ProjectPayment(projectId = projectId, name = "", date = "2026-11-10", periodCovered = "", grossAmount = gross, percentage = 0.0, balanceAmount = 0.0,
            balancePercentage = 0.0, billingType = type, recoupmentAmount = recoup, uuid = uuid)

    @Test fun recoupmentStopsOnceTheAdvanceIsPaidBack() {
        assertEquals(1_500_000.0, advanceAmount(10_000_000.0), 0.0)
        val advance = billing(1_500_000.0, type = ADVANCE_PAYMENT, uuid = "a")
        val b1 = billing(2_000_000.0, 300_000.0, uuid = "b1")
        val b2 = billing(7_000_000.0, 1_100_000.0, uuid = "b2")
        assertEquals(null, advanceLeft(emptyList(), listOf(b1)))
        assertEquals(100_000.0, advanceLeft(listOf(advance), listOf(b1, b2))!!, 0.0)
        assertEquals("an edited billing's own recoupment is not counted", 1_200_000.0, advanceLeft(listOf(advance), listOf(b1, b2), b2)!!, 0.0)
        assertEquals(300_000.0, facilityDeductions(2_000_000.0)[0], 0.0)
        assertEquals(100_000.0, facilityDeductions(2_000_000.0, 100_000.0)[0], 0.0)
        assertEquals(0.0, facilityDeductions(2_000_000.0, 0.0)[0], 0.0)
    }

    @Test fun theFirstBillingIsTheAdvancePaidInFull() {
        val saved = mutableListOf<ProjectPayment>()
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                AddPaymentDialog(10_000_000.0, 0.0, {}, { saved += it }, isFacility = true, canAdvance = true, advanceAmount = 1_500_000.0)
            }
        }
        rule.onAllNodes(hasSetTextAction() and hasText("1500000.00")).onFirst().assertExists()
        rule.onNodeWithTag("billing_deduction_0").assertDoesNotExist()
        rule.onNodeWithText("Save Payment").performClick()
        rule.waitForIdle()
        val p = saved.single()
        assertEquals(ADVANCE_PAYMENT, p.billingType)
        assertEquals(ADVANCE_PAYMENT, p.name)
        assertEquals(listOf(1_500_000.0, 0.0, 0.0, 0.0, 0.0), listOf(p.grossAmount, p.retentionPct, p.recoupmentAmount, p.vatAmount, p.withholdingAmount))
        assertEquals(1_500_000.0, p.paidAmount, 0.0)
    }

    @Test fun theLastRecoupmentTakesOnlyWhatIsLeft() {
        rule.setContent {
            NHATheme(themeMode = AppThemeMode.LIGHT) { AddPaymentDialog(10_000_000.0, 0.0, {}, {}, isFacility = true, advanceLeft = 100_000.0) }
        }
        rule.onAllNodes(hasSetTextAction() and hasText("Gross Amount (PHP)")).onFirst().performTextReplacement("2000000")
        rule.onNodeWithTag("billing_deduction_0").assertTextContains("100000.00")
        rule.onNodeWithTag("billing_advance_note").assertTextContains("This billing finishes recouping the advance payment.")
    }

    @Test fun theAdvanceStaysOutOfTheBillings() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<android.content.Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val id = db.projectDao().insertProject(Project(
                name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
                contractCostOriginal = 10_000_000.0, contractCostRevised = 0.0, contractDurationDays = 1,
                dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31"
            ))
            db.projectDao().insertProjectPayment(billing(1_500_000.0, type = ADVANCE_PAYMENT, uuid = "a", projectId = id))
            db.projectDao().insertProjectPayment(billing(2_000_000.0, 300_000.0, uuid = "b1", projectId = id))
            assertEquals(listOf("b1"), db.projectDao().getProjectPaymentsForProject(id).first().map { it.uuid })
            assertEquals(listOf("b1"), db.projectDao().getAllPayments().first().map { it.uuid })
            assertEquals(listOf("a"), db.projectDao().getAdvancePayments(id).first().map { it.uuid })
        } finally {
            db.close()
        }
    }
}
