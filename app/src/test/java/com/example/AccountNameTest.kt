package com.example

import com.example.data.auth.isEmailName
import com.example.data.auth.personName
import com.example.data.auth.refreshedAccount
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
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

    // Every phone signed in to the same account shows the server's name, not the one it saved at its own sign-in.
    @Test fun openingTheApp_takesTheServersProfile() {
        val email = "great.glenn17@gmail.com"
        val phone = UserAccount(id = "u1", name = "Great Glenn17", title = "Engineer", role = UserRole.SUPER_ADMIN, office = "Bulacan District Office")
        val fresh = refreshedAccount(phone, email, null, "Glenn C. Aprovechado", "Division Manager", "Region III")
        assertEquals(phone.copy(name = "Glenn C. Aprovechado", title = "Division Manager", office = "Region III"), fresh)
        // Offline or nothing on the server: the phone keeps what it has.
        assertEquals(phone, refreshedAccount(phone, email, null, null, null, null))
        // An email-made name on the server never replaces a real one.
        val named = phone.copy(name = "Glenn C. Aprovechado")
        assertEquals(named, refreshedAccount(named, email, null, "great.glenn17", null, null))
        assertEquals(UserRole.VIEWER, refreshedAccount(phone, email, UserRole.VIEWER, null, null, null).role)
    }
}
