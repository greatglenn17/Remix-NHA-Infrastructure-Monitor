package com.example.util

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Lot numbers for the lots of one block, from where the lots sit. The lots are split into rows
 * along the block's long direction; a plan numbers them either odd along one row and even along
 * the other (1 across from 2), or consecutively around the block (one row out, the next back).
 */
object BlockNumbering {

    enum class Pattern { ODD_EVEN, CONSECUTIVE }

    /**
     * [centres] are the lot centres in page pixels (same unit for x and y). Returns each lot's
     * number, in the order of [centres]. Without flips, the first row is the top (or left) one
     * and numbering starts at its left (or top) end.
     */
    fun number(
        centres: List<Pair<Double, Double>>,
        first: Int,
        pattern: Pattern,
        otherEnd: Boolean = false,
        otherRow: Boolean = false
    ): List<Int> {
        if (centres.size < 2) return centres.map { first }
        val mx = centres.map { it.first }.average(); val my = centres.map { it.second }.average()
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for ((x, y) in centres) { val dx = x - mx; val dy = y - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        val angle = 0.5 * atan2(2 * sxy, sxx - syy)
        var ux = cos(angle); var uy = sin(angle)
        // Point the long axis rightwards (downwards when the block stands upright).
        if (if (abs(ux) > 0.3) ux < 0 else uy < 0) { ux = -ux; uy = -uy }
        val along = centres.map { (x, y) -> (x - mx) * ux + (y - my) * uy }
        val across = centres.map { (x, y) -> (x - mx) * -uy + (y - my) * ux }

        // Neighbours in a row sit one lot width apart; the next row is a lot depth away.
        val width = centres.indices.map { i ->
            centres.indices.filter { it != i }.minOf { j -> hypot(centres[i].first - centres[j].first, centres[i].second - centres[j].second) }
        }.sorted()[centres.size / 2]
        val rows = mutableListOf(mutableListOf<Int>())
        val byAcross = centres.indices.sortedBy { across[it] }
        for ((k, i) in byAcross.withIndex()) {
            if (k > 0 && across[i] - across[byAcross[k - 1]] > width * 0.5) rows += mutableListOf<Int>()
            rows.last() += i
        }
        if (otherRow) rows.reverse()
        rows.forEach { row -> row.sortBy { if (otherEnd) -along[it] else along[it] } }

        val numbers = IntArray(centres.size)
        when (pattern) {
            Pattern.ODD_EVEN -> {
                val used = IntArray(2)
                rows.forEachIndexed { k, row ->
                    row.forEachIndexed { n, i -> numbers[i] = first + k % 2 + 2 * (used[k % 2] + n) }
                    used[k % 2] += row.size
                }
            }
            Pattern.CONSECUTIVE -> {
                var next = first
                rows.forEachIndexed { k, row -> (if (k % 2 == 0) row else row.reversed()).forEach { numbers[it] = next++ } }
            }
        }
        return numbers.toList()
    }
}
