package com.example

import com.example.util.LotArea
import org.junit.Assert.assertEquals
import org.junit.Test

class LotAreaTest {

    @Test
    fun aTenByFifteenMetreLotAtOneToThousand() {
        // On an A1 sheet (2384 x 1684 pt) at 1:1000, 10 m on the ground is 10 mm on paper = 28.3465 pt.
        val w = 2384.0; val h = 1684.0
        val dx = 10 / (0.0254 / 72 * 1000) / w
        val dy = 15 / (0.0254 / 72 * 1000) / h
        val lot = listOf(0.2 to 0.3, 0.2 + dx to 0.3, 0.2 + dx to 0.3 + dy, 0.2 to 0.3 + dy)
        assertEquals(150.0, LotArea.squareMetres(lot, w, h, 1000), 0.01)
        assertEquals(150.0, LotArea.squareMetres(lot.reversed(), w, h, 1000), 0.01)
        assertEquals(37.5, LotArea.squareMetres(lot, w, h, 500), 0.01)
    }

    @Test
    fun noScaleOrNoShapeGivesZero() {
        assertEquals(0.0, LotArea.squareMetres(listOf(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0), 100.0, 100.0, 0), 0.0)
        assertEquals(0.0, LotArea.squareMetres(listOf(0.0 to 0.0, 1.0 to 0.0), 100.0, 100.0, 1000), 0.0)
    }
}
