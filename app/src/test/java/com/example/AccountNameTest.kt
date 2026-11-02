package com.example

import com.example.data.auth.isEmailName
import com.example.data.auth.personName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The account shows "First M. Last" from sign-up, not a name made from the email.
class AccountNameTest {
    @Test fun personName_joinsTheParts() {
        assertEquals("Juan C. Dela Cruz", personName(" Juan ", "c", "Dela Cruz "))
        assertEquals("Juan C. Dela Cruz", personName("Juan", "C.", "Dela Cruz"))
        assertEquals("Juan Dela Cruz", personName("Juan", " ", "Dela Cruz"))
    }

    @Test fun emailNames_areSpotted() {
        val email = "great.glenn17@gmail.com"
        assertTrue(isEmailName("Great Glenn17", email))
        assertTrue(isEmailName("great.glenn17", email))
        assertTrue(isEmailName("", email))
        assertTrue(isEmailName(null, email))
        assertFalse(isEmailName("Glenn C. Aprovechado", email))
    }
}
