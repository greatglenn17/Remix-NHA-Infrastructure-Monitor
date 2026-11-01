package com.example.utils

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {
    private val decimalFormat = (NumberFormat.getNumberInstance(Locale.US) as DecimalFormat).apply {
        applyPattern("#,##0.00")
    }

    private val integerFormat = (NumberFormat.getNumberInstance(Locale.US) as DecimalFormat).apply {
        applyPattern("#,##0")
    }

    /**
     * Formats an amount for the screen: "₱1,250,000.00", the same peso sign the dashboard uses.
     */
    fun formatPhp(amount: Double): String {
        val sign = if (amount < 0) "-" else ""
        return "${sign}₱${decimalFormat.format(kotlin.math.abs(amount))}"
    }

    /**
     * Formats number to string with commas for thousands: "1,000"
     */
    fun formatNumber(number: Number): String {
        return integerFormat.format(number)
    }

    /**
     * Formats double to decimal string with commas: "1,000.00"
     */
    fun formatDecimal(number: Double): String {
        return decimalFormat.format(number)
    }

    /**
     * Formats a raw number string to comma separated string if valid
     */
    fun formatInputNumber(input: String): String {
        val clean = input.replace(",", "").trim()
        val num = clean.toLongOrNull()
        return if (num != null) integerFormat.format(num) else input
    }
}

fun Double.formatPhp(): String = CurrencyFormatter.formatPhp(this)
fun Float.formatPhp(): String = CurrencyFormatter.formatPhp(this.toDouble())
fun Int.formatWithCommas(): String = CurrencyFormatter.formatNumber(this)
fun Long.formatWithCommas(): String = CurrencyFormatter.formatNumber(this)
fun Double.formatWithCommas(): String = CurrencyFormatter.formatDecimal(this)
