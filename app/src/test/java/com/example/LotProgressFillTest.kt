package com.example

import androidx.compose.ui.graphics.Color
import com.example.data.model.SdpLot
import com.example.data.model.SdpLotProgress
import com.example.data.model.billingLabel
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

    @Test
    fun lotsAreBilledTwice_m1At80AndM2At20() {
        val lot = SdpLot(id = 1, projectId = 1, sdpPlanId = 1, blockNumber = "1", lotNumber = "1", polygonNormalizedJson = "[]")
        fun progress(m1: Boolean, m2: Boolean) = SdpLotProgress(
            projectId = 1, sdpPlanId = 1, sdpLotId = 1,
            billingStatus = if (m1) "BILLED" else "NOT BILLED", m2BillingStatus = if (m2) "BILLED" else "NOT BILLED"
        )
        fun filters(p: SdpLotProgress?) = listOf("M1 BILLED", "M2 BILLED", "NOT BILLED")
            .filter { isLotMatchingFilter(lot, "", "ALL", it, "ALL", if (p == null) emptyMap() else mapOf(1L to p)) }

        assertEquals("NOT BILLED", (null as SdpLotProgress?).billingLabel)
        assertEquals(listOf("NOT BILLED"), filters(null))
        // The single "billed" mark of earlier versions is M1.
        assertEquals("M1 BILLED (80%)", progress(m1 = true, m2 = false).billingLabel)
        assertEquals(listOf("M1 BILLED"), filters(progress(m1 = true, m2 = false)))
        assertEquals("M1 + M2 BILLED (100%)", progress(m1 = true, m2 = true).billingLabel)
        assertEquals(listOf("M1 BILLED", "M2 BILLED"), filters(progress(m1 = true, m2 = true)))
    }
}
