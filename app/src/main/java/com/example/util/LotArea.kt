package com.example.util

import kotlin.math.abs

/** Ground area of a lot outline drawn on a plan sheet. */
object LotArea {

    private const val METRES_PER_POINT = 0.0254 / 72 // a PDF point is 1/72 inch on paper

    /**
     * [polygon] is normalized to the page (x, y in 0..1); the page is [pageWidthPt] x [pageHeightPt]
     * PDF points and the plan is drawn at 1:[scale]. Returns square metres, to two decimals.
     */
    fun squareMetres(polygon: List<Pair<Double, Double>>, pageWidthPt: Double, pageHeightPt: Double, scale: Int): Double {
        if (polygon.size < 3 || scale <= 0) return 0.0
        var twice = 0.0
        for (i in polygon.indices) {
            val (x1, y1) = polygon[i]; val (x2, y2) = polygon[(i + 1) % polygon.size]
            twice += x1 * pageWidthPt * y2 * pageHeightPt - x2 * pageWidthPt * y1 * pageHeightPt
        }
        val metres = METRES_PER_POINT * scale
        return Math.round(abs(twice) / 2 * metres * metres * 100) / 100.0
    }
}
