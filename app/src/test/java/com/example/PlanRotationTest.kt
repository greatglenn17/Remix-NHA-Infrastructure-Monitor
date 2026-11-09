package com.example

import com.example.util.PlanRotation
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanRotationTest {

    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, 1e-6f)
        assertEquals(expected.second, actual.second, 1e-6f)
    }

    @Test
    fun quarterTurnsCycle() {
        assertEquals(listOf(90, 180, 270, 0), generateSequence(PlanRotation.next(0), PlanRotation::next).take(4).toList())
    }

    // Turning clockwise moves the page's top-left corner to the top right, then bottom right, then bottom left.
    @Test
    fun pageCorner_movesClockwise() {
        assertPoint(0f to 0f, PlanRotation.toView(0f, 0f, 0))
        assertPoint(1f to 0f, PlanRotation.toView(0f, 0f, 90))
        assertPoint(1f to 1f, PlanRotation.toView(0f, 0f, 180))
        assertPoint(0f to 1f, PlanRotation.toView(0f, 0f, 270))
        // A point right of centre on the page goes below centre after a quarter turn.
        assertPoint(0.5f to 0.8f, PlanRotation.toView(0.8f, 0.5f, 90))
    }

    // A tap on the turned plan lands on the same page point the lot was saved with, so lots never change.
    @Test
    fun tapOnTurnedPlan_mapsBackToThePage() {
        for (deg in listOf(0, 90, 180, 270, 360, -90)) {
            for ((x, y) in listOf(0.1f to 0.7f, 0.95f to 0.05f, 0.5f to 0.5f)) {
                val (vx, vy) = PlanRotation.toView(x, y, deg)
                assertPoint(x to y, PlanRotation.toPage(vx, vy, deg))
            }
        }
    }

    @Test
    fun printFrame_staysABox() {
        val frame = listOf(0.1f, 0.2f, 0.4f, 0.9f)
        listOf(0.1f, 0.1f, 0.8f, 0.4f).zip(PlanRotation.frameToView(frame, 90)).forEach { (a, b) -> assertEquals(a, b, 1e-6f) }
        for (deg in listOf(0, 90, 180, 270)) {
            val back = PlanRotation.frameToPage(PlanRotation.frameToView(frame, deg), deg)
            frame.zip(back).forEach { (a, b) -> assertEquals(a, b, 1e-6f) }
        }
    }
}
