package com.example

import com.example.ui.components.dateToUtcMillis
import com.example.ui.components.utcMillisToDate
import com.example.util.FormValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormValidationTest {
    @Test fun parseAmount() {
        assertEquals(15000000.5, FormValidation.parseAmount("15,000,000.50")!!, 0.0)
        assertEquals(1200.0, FormValidation.parseAmount(" 1 200 ")!!, 0.0)
        assertNull(FormValidation.parseAmount("-5"))
        assertNull(FormValidation.parseAmount("abc"))
    }

    @Test fun amountError() {
        assertEquals("Required", FormValidation.amountError("", true))
        assertNull(FormValidation.amountError("", false))
        assertEquals("Enter an amount", FormValidation.amountError("abc", false))
    }

    @Test fun percentError() {
        assertNull(FormValidation.percentError("100"))
        assertEquals("0 to 100", FormValidation.percentError("100.5"))
        assertNull(FormValidation.percentError(""))
    }

    @Test fun requiredError() {
        assertEquals("Required", FormValidation.requiredError("  "))
        assertNull(FormValidation.requiredError("x"))
    }

    @Test fun dateRoundTripsThroughUtcMillis() {
        assertEquals("2026-03-01", utcMillisToDate(dateToUtcMillis("2026-03-01")!!))
        assertNull(dateToUtcMillis("-"))
        assertNull(dateToUtcMillis("1850-01-01"))
    }
}
