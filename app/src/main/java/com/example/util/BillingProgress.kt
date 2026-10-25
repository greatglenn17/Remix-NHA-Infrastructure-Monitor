package com.example.util

/** How much of a project's contract has been billed, as shown on the dashboard cards. */
object BillingProgress {

    /** Billed share of the contract in 0..1, or null when there is no contract cost to measure against. */
    fun billedFraction(totalBilled: Double, contractCost: Double): Float? =
        if (contractCost > 0) (totalBilled / contractCost).coerceIn(0.0, 1.0).toFloat() else null
}
