package com.example

import com.example.util.BlockNumbering
import com.example.util.BlockNumbering.Pattern
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BlockNumberingTest {

    /** Two rows of five lots, 10 wide and 20 deep: top row first, left to right. */
    private val block = (0 until 2).flatMap { r -> (0 until 5).map { c -> c * 10.0 + 5 to r * 20.0 + 10 } }

    @Test
    fun oddAlongOneRowEvenAlongTheOther() {
        assertEquals(listOf(1, 3, 5, 7, 9, 2, 4, 6, 8, 10), BlockNumbering.number(block, 1, Pattern.ODD_EVEN))
        assertEquals(listOf(9, 7, 5, 3, 1, 10, 8, 6, 4, 2), BlockNumbering.number(block, 1, Pattern.ODD_EVEN, otherEnd = true))
        assertEquals(listOf(2, 4, 6, 8, 10, 1, 3, 5, 7, 9), BlockNumbering.number(block, 1, Pattern.ODD_EVEN, otherRow = true))
    }

    @Test
    fun consecutiveGoesOutAlongOneRowAndBackAlongTheOther() {
        assertEquals(listOf(1, 2, 3, 4, 5, 10, 9, 8, 7, 6), BlockNumbering.number(block, 1, Pattern.CONSECUTIVE))
        assertEquals(listOf(21, 22, 23, 24, 25, 30, 29, 28, 27, 26), BlockNumbering.number(block, 21, Pattern.CONSECUTIVE))
    }

    @Test
    fun singleUprightRowAndATiltedBlock() {
        val strip = (0 until 4).map { 50.0 to it * 10.0 }
        assertEquals(listOf(1, 2, 3, 4), BlockNumbering.number(strip, 1, Pattern.CONSECUTIVE))
        assertEquals(listOf(1, 3, 5, 7), BlockNumbering.number(strip, 1, Pattern.ODD_EVEN))
        // The same two-row block turned 30 degrees still splits into its two rows.
        val a = Math.toRadians(30.0)
        val tilted = block.map { (x, y) -> x * cos(a) - y * sin(a) to x * sin(a) + y * cos(a) }
        assertEquals(listOf(1, 3, 5, 7, 9, 2, 4, 6, 8, 10), BlockNumbering.number(tilted, 1, Pattern.ODD_EVEN))
    }
}
