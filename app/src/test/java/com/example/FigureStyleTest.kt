package com.example

import com.example.ui.theme.isFigure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only a measured figure is set in the mono; the website's `L.isFigure` has the same cases. */
class FigureStyleTest {
    @Test
    fun anAmountAPercentageOrANumberOnItsOwn_isAFigure() {
        for (v in listOf("₱30,000,000.00", "-₱500.00", "41.25%", "400", "0", " 7.50% ")) assertTrue(v, isFigure(v))
    }

    @Test
    fun wordsAndFiguresMixedWithWords_areNot() {
        for (v in listOf("No billings yet", "540 days", "Jan 5, 2026", "2026-10-15", "18.00% behind", "Behind Schedule", "", "-")) assertFalse(v, isFigure(v))
    }
}
