package com.example.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The on-device copy of a password, used only to sign in while Firebase is unreachable.
 * Stored as "pbkdf2:<iterations>:<salt hex>:<hash hex>" with a random salt per account.
 * Older copies (plain SHA-256 or plain text) still verify; [needsUpgrade] says to re-store them.
 */
object LocalPassword {
    private const val ITERATIONS = 10_000
    // HmacSHA1 is the PBKDF2 variant available on every Android version this app supports (24+).
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"

    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "pbkdf2:$ITERATIONS:${salt.hex()}:${derive(password.trim(), salt, ITERATIONS).hex()}"
    }

    fun verify(password: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val clean = password.trim()
        val parts = stored.split(":")
        if (parts.size == 4 && parts[0] == "pbkdf2") {
            val iterations = parts[1].toIntOrNull() ?: return false
            val expected = parts[3].unhex() ?: return false
            val salt = parts[2].unhex() ?: return false
            return MessageDigest.isEqual(derive(clean, salt, iterations), expected)
        }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(clean.toByteArray(Charsets.UTF_8)).hex()
        return MessageDigest.isEqual(sha256.toByteArray(), stored.toByteArray()) || stored == clean
    }

    fun needsUpgrade(stored: String?): Boolean = stored != null && !stored.startsWith("pbkdf2:")

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance(ALGORITHM)
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, 256))
            .encoded

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    private fun String.unhex(): ByteArray? =
        if (length % 2 != 0) null else runCatching { chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
}
