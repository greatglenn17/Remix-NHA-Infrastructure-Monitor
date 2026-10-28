package com.example

import androidx.compose.ui.graphics.Color
import com.example.ui.detail.tabs.lotProgressFill
import com.example.ui.theme.LightNHAColors
import org.junit.Assert.assertEquals
import org.junit.Test

class LotProgressFillTest {
    @Test
    fun bandsChangeColourAtTheirEdges() {
        val c = LightNHAColors
        val red = c.behind; val orange = Color(0xFFEA580C); val yellow = Color(0xFFCA8A04) // light-theme orange, yellow
        val blue = Color(0xFF2563EB); val green = c.onTrack
        mapOf(0 to red, 25 to red, 26 to orange, 50 to orange, 51 to yellow, 75 to yellow,
            76 to blue, 99 to blue, 100 to green).forEach { (p, colour) ->
            assertEquals("at $p%", colour, lotProgressFill(p, c))
        }
    }
}
