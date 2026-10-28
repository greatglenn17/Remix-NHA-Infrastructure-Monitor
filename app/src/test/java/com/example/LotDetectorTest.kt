package com.example

import com.example.util.LotDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotDetectorTest {

    private class Canvas(val w: Int, val h: Int) {
        val dark = BooleanArray(w * h)
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int) {
            for (x in x0..x1) { dark[y0 * w + x] = true; dark[y1 * w + x] = true }
            for (y in y0..y1) { dark[y * w + x0] = true; dark[y * w + x1] = true }
        }
        /** A block of [cols] x [rows] lots of [lw] x [lh] pixels, outlines shared. */
        fun block(x0: Int, y0: Int, cols: Int, rows: Int, lw: Int, lh: Int) {
            for (c in 0 until cols) for (r in 0 until rows) {
                rect(x0 + c * lw, y0 + r * lh, x0 + (c + 1) * lw, y0 + (r + 1) * lh)
            }
        }
    }

    @Test
    fun findsLotsInBlocksAndAnOpenSpace() {
        val c = Canvas(800, 600)
        c.block(50, 50, cols = 6, rows = 2, lw = 30, lh = 50)   // block 1: 12 lots
        c.block(50, 250, cols = 6, rows = 2, lw = 30, lh = 50)  // block 2: 12 lots
        c.rect(60, 165, 200, 235)                                // open space between the blocks
        c.rect(650, 450, 780, 580)                               // title box, far from the site
        val areas = LotDetector.detect(c.dark, c.w, c.h)

        val lots = areas.filter { it.isLot }
        assertEquals(24, lots.size)
        assertEquals(setOf(1, 2), lots.map { it.block }.toSet())
        assertEquals((1..12).toList(), lots.filter { it.block == 1 }.map { it.lot })
        assertEquals(1, areas.count { !it.isLot })
        // Polygons are normalized and simplified to a handful of corners.
        lots.forEach { lot ->
            assertTrue(lot.polygon.size in 4..8)
            assertTrue(lot.polygon.all { (x, y) -> x in 0f..1f && y in 0f..1f })
        }
    }

    @Test
    fun traceAtOutlinesTheLotUnderTheTap() {
        val c = Canvas(400, 300)
        c.block(50, 50, cols = 3, rows = 1, lw = 40, lh = 60)
        // Middle lot spans x 90..130, y 50..110; tap its centre, and on the inner edge of its left line.
        for (tapX in listOf(110f, 91f)) {
            val poly = LotDetector.traceAt(c.dark, c.w, c.h, tapX / c.w, 80f / c.h)!!
            val xs = poly.map { it.first * c.w }; val ys = poly.map { it.second * c.h }
            assertTrue(xs.min() in 88f..94f && xs.max() in 128f..133f)
            assertTrue(ys.min() in 48f..54f && ys.max() in 108f..113f)
        }
        // Outside every outline: open to the page edge, not a lot.
        assertEquals(null, LotDetector.traceAt(c.dark, c.w, c.h, 0.9f, 0.9f))
    }

    @Test
    fun findsLotsInABlockTiltedAlongADiagonalRoad() {
        val c = Canvas(600, 600)
        val a = Math.toRadians(50.0)
        fun line(x0: Double, y0: Double, x1: Double, y1: Double) {
            val p = { x: Double, y: Double -> (300 + x * Math.cos(a) - y * Math.sin(a)) to (300 + x * Math.sin(a) + y * Math.cos(a)) }
            val (ax, ay) = p(x0, y0); val (bx, by) = p(x1, y1)
            for (i in 0..1000) {
                val x = (ax + (bx - ax) * i / 1000).toInt(); val y = (ay + (by - ay) * i / 1000).toInt()
                c.dark[y * c.w + x] = true
            }
        }
        // One row of 8 lots, 25 x 50 px, turned 50 degrees: their own boxes are only about half full.
        line(-100.0, 0.0, 100.0, 0.0); line(-100.0, 50.0, 100.0, 50.0)
        for (i in 0..8) line(-100.0 + i * 25, 0.0, -100.0 + i * 25, 50.0)
        assertEquals(8, LotDetector.detect(c.dark, c.w, c.h).count { it.isLot })
    }

    @Test
    fun blankOrScannedPageFindsNothing() {
        assertTrue(LotDetector.detect(BooleanArray(200 * 100), 200, 100).isEmpty())
    }
}
