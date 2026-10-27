package com.example.util

object FormValidation {
    fun parseAmount(text: String): Double? =
        text.replace(",", "").replace(" ", "").toDoubleOrNull()?.takeIf { it >= 0 }

    fun amountError(text: String, required: Boolean): String? = when {
        text.isBlank() -> if (required) "Required" else null
        parseAmount(text) == null -> "Enter an amount"
        else -> null
    }

    fun percentError(text: String): String? {
        if (text.isBlank()) return null
        val v = text.trim().toDoubleOrNull()
        return if (v == null || v < 0 || v > 100) "0 to 100" else null
    }

    fun requiredError(text: String): String? = if (text.isBlank()) "Required" else null
}
