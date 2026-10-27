package com.example

import com.example.util.BillingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingProgressTest {

    @Test
    fun partialBilling_isShareOfContract() {
        assertEquals(0.25f, BillingProgress.billedFraction(250_000.0, 1_000_000.0)!!, 0.0001f)
    }

    @Test
    fun noPayments_isZero() {
        assertEquals(0f, BillingProgress.billedFraction(0.0, 1_000_000.0)!!, 0.0001f)
    }

    @Test
    fun overbilling_isCappedAtFull() {
        assertEquals(1f, BillingProgress.billedFraction(1_200_000.0, 1_000_000.0)!!, 0.0001f)
    }

    @Test
    fun zeroOrNegativeContract_hasNoFraction() {
        assertNull(BillingProgress.billedFraction(100.0, 0.0))
        assertNull(BillingProgress.billedFraction(100.0, -5.0))
    }

    @Test
    fun label_saysWhyThereIsNoPercent() {
        assertEquals("No billings yet", BillingProgress.label(0.0, 1_000_000.0))
        assertEquals("No contract cost", BillingProgress.label(100.0, 0.0))
        assertEquals("25.0%", BillingProgress.label(250_000.0, 1_000_000.0))
    }
}
