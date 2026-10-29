package com.example

import androidx.compose.ui.graphics.Color
import com.example.data.model.SdpLot
import com.example.data.model.SdpLotProgress
import com.example.ui.detail.tabs.LOT_PROGRESS_FILTERS
import com.example.ui.detail.tabs.isLotMatchingFilter
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

    @Test
    fun eachProgressFilterPicksOnlyItsOwnBand() {
        val lot = SdpLot(id = 1, projectId = 1, sdpPlanId = 1, blockNumber = "1", lotNumber = "1", polygonNormalizedJson = "[]")
        val bands = LOT_PROGRESS_FILTERS.drop(1)
        for (p in listOf(0, 25, 26, 50, 51, 75, 76, 99, 100)) {
            val progress = mapOf(1L to SdpLotProgress(projectId = 1, sdpPlanId = 1, sdpLotId = 1, physicalProgress = p))
            val matching = bands.filter { isLotMatchingFilter(lot, "", it, "ALL", "ALL", progress) }
            assertEquals("at $p%", 1, matching.size)
        }
    }
}
