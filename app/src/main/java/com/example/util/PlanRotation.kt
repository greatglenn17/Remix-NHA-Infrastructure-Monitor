package com.example.util

/**
 * A plan shown turned clockwise by 0, 90, 180 or 270 degrees. Lots, roads and the print frame stay in
 * fractions (0..1) of the unturned page, so a turn never changes them; these map points to the turned view and back.
 */
object PlanRotation {
    fun next(degrees: Int) = (degrees + 90).mod(360)

    fun toView(x: Float, y: Float, degrees: Int): Pair<Float, Float> = when (degrees.mod(360)) {
        90 -> 1f - y to x
        180 -> 1f - x to 1f - y
        270 -> y to 1f - x
        else -> x to y
    }

    fun toPage(x: Float, y: Float, degrees: Int) = toView(x, y, 360 - degrees.mod(360))

    /** A frame (minX, minY, maxX, maxY) turned the same way; a turned box is still a box. */
    fun frameToView(f: List<Float>, degrees: Int) = box(toView(f[0], f[1], degrees), toView(f[2], f[3], degrees))

    fun frameToPage(f: List<Float>, degrees: Int) = box(toPage(f[0], f[1], degrees), toPage(f[2], f[3], degrees))

    private fun box(a: Pair<Float, Float>, b: Pair<Float, Float>) =
        listOf(minOf(a.first, b.first), minOf(a.second, b.second), maxOf(a.first, b.first), maxOf(a.second, b.second))
}
