package com.example.util

object FormValidation {
    private val plainDecimal = Regex("""\d+(\.\d*)?|\.\d+""")

    fun parseAmount(text: String): Double? =
        text.replace(",", "").replace(" ", "").takeIf { plainDecimal.matches(it) }
            ?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

    fun amountError(text: String, required: Boolean): String? = when {
        text.isBlank() -> if (required) "Required" else null
        parseAmount(text) == null -> "Enter an amount"
        else -> null
    }

    fun percentError(text: String): String? {
        if (text.isBlank()) return null
        val v = text.trim().takeIf { plainDecimal.matches(it) }?.toDoubleOrNull()
        return if (v == null || !v.isFinite() || v < 0 || v > 100) "0 to 100" else null
    }

    fun requiredError(text: String): String? = if (text.isBlank()) "Required" else null
}
