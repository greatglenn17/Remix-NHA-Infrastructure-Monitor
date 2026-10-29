package com.example.util

/** How much of a project's contract has been billed, as shown on the dashboard cards. */
object BillingProgress {

    /** Billed share of the contract in 0..1, or null when there is no contract cost to measure against. */
    fun billedFraction(totalBilled: Double, contractCost: Double): Float? =
        if (contractCost > 0) (totalBilled / contractCost).coerceIn(0.0, 1.0).toFloat() else null

    /** Text beside the Billed bar: the billed share, or why there is none. */
    fun label(totalBilled: Double, contractCost: Double): String = when {
        totalBilled <= 0 -> "No billings yet"
        contractCost <= 0 -> "No contract cost"
        else -> "%.2f%%".format(totalBilled / contractCost * 100)
    }
}
