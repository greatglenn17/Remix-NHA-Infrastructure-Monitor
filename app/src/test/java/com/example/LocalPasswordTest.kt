package com.example

import com.example.data.auth.LocalPassword
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPasswordTest {

    @Test
    fun saltedHash_verifiesOnlyTheRightPassword() {
        val stored = LocalPassword.hash("secret123")
        assertTrue(stored.startsWith("pbkdf2:"))
        assertTrue(LocalPassword.verify("secret123", stored))
        assertTrue(LocalPassword.verify(" secret123 ", stored))
        assertFalse(LocalPassword.verify("secret124", stored))
        assertFalse(LocalPassword.needsUpgrade(stored))
        // A fresh salt each time, so equal passwords do not give equal copies.
        assertNotEquals(stored, LocalPassword.hash("secret123"))
    }

    @Test
    fun olderCopies_stillVerifyAndNeedUpgrade() {
        val sha256 = "fcf730b6d95236ecd3c9fc2d92d7b6b2bb061514961aec041d6c7a7192f592e4" // "secret123"
        assertTrue(LocalPassword.verify("secret123", sha256))
        assertFalse(LocalPassword.verify("other1", sha256))
        assertTrue(LocalPassword.verify("secret123", "secret123"))
        assertTrue(LocalPassword.needsUpgrade(sha256))
        assertFalse(LocalPassword.verify("secret123", null))
        assertFalse(LocalPassword.verify("secret123", "pbkdf2:x:zz:zz"))
    }
}
