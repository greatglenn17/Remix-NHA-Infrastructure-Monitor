package com.example

import com.example.data.model.LOT_USES
import com.example.data.model.Project
import com.example.data.model.SdpLot
import com.example.data.model.use
import com.example.util.BlueBookForms
import com.example.util.SdpSummary
import com.example.util.SdpSummary.Item
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SdpSummaryTest {

    @Test
    fun saleableLotsCountByBand_roadsWeightByArea_otherUsesListed() {
        val items = listOf(
            Item("Saleable", 40.0, 0), Item("Saleable", 40.0, 25), Item("Saleable", 40.0, 26),
            Item("Saleable", 40.0, 75), Item("Saleable", 40.0, 76), Item("Saleable", 40.0, 100),
            Item("Road Lot", 300.0, 100, drainage = 50), Item("Road Lot", 100.0, 0, drainage = 10),
            Item("Open Space", 500.0, 0), Item("Easement", 80.0, 0)
        )
        val s = SdpSummary.of(items, LOT_USES)
        assertEquals(listOf(2, 1, 1, 1, 1), s.saleableBands)
        assertEquals(SdpSummary.Totals(6, 240.0), s.saleable)
        assertEquals(SdpSummary.Totals(2, 400.0), s.roads)
        // (100 x 300 + 0 x 100) / 400 and (50 x 300 + 10 x 100) / 400
        assertEquals(75.0, s.roadProgress, 0.001)
        assertEquals(40.0, s.drainageProgress, 0.001)
        assertEquals(listOf("Open Space", "Road Lot", "Easement"), s.nonSaleable.map { it.first })
    }

    @Test
    fun roadProgressIsAPlainAverageWhileAreasAreUnknown() {
        val s = SdpSummary.of(listOf(Item("Road Lot", 0.0, 80, 20), Item("Road Lot", 120.0, 20, 0)), LOT_USES)
        assertEquals(50.0, s.roadProgress, 0.001)
        assertEquals(10.0, s.drainageProgress, 0.001)
    }

    @Test
    fun frameFitsAroundLotsWithAMarginAndStaysOnThePage() {
        val f = SdpSummary.frameAround(listOf(listOf(0.01f to 0.2f, 0.4f to 0.6f), listOf(0.5f to 0.5f, 0.6f to 0.99f)))!!
        assertEquals(listOf(0f, 0.17f, 0.63f, 1f), f.map { Math.round(it * 100) / 100f })
        assertNull(SdpSummary.frameAround(emptyList()))
    }

    @Test
    fun savedFrameRoundTripsAndBadTextIsIgnored() {
        val f = listOf(0.1f, 0.2f, 0.7f, 0.9f)
        assertEquals(f, SdpSummary.parseFrame(SdpSummary.formatFrame(f)))
        for (bad in listOf("", "0.1,0.2,0.7", "0.7,0.2,0.1,0.9", "0,0,1.5,1", "a,b,c,d")) assertNull(bad, SdpSummary.parseFrame(bad))
    }

    @Test
    fun lotsSavedBeforeUsesAreSaleable_exceptTheOpenSpaceBlock() {
        fun lot(block: String, use: String = "") = SdpLot(projectId = 1, sdpPlanId = 1, blockNumber = block, lotNumber = "1", polygonNormalizedJson = "[]", lotUse = use)
        assertEquals("Saleable", lot("3").use)
        assertEquals("Open Space", lot("OS").use)
        assertEquals("Road Lot", lot("OS", "Road Lot").use)
    }

    @Test
    fun printShowsTheBandsNonSaleableTotalsAndRoadWorks() {
        val project = Project(
            name = "San Ildefonso <Heights>", location = "Bulacan", implementingOffice = "", contractor = "", scopeOfWork = "",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 360,
            dateStarted = "2026-04-13", completionDateOriginal = "2027-04-07", completionDateRevised = ""
        )
        val s = SdpSummary.of(
            listOf(Item("Saleable", 40.0, 10), Item("Saleable", 40.0, 30), Item("Road Lot", 200.0, 60, 30), Item("Open Space", 100.0, 0)),
            LOT_USES
        )
        val html = BlueBookForms.sdpPlan(project, s, "data:image/png;base64,AA", listOf("#111111", "#222222", "#333333", "#444444", "#555555"), LocalDate.of(2026, 10, 30))
        assertTrue(html.contains("size: A4 landscape"))
        assertTrue(html.contains("San Ildefonso &lt;Heights&gt;, Bulacan &#183; as of"))
        assertTrue(html.contains("background:#111111'></i>0–25%</td><td class='n'>1</td>"))
        assertTrue(html.contains("<td>Total units</td><td class='n'>2</td>"))
        assertTrue(html.contains("<td>Road Lot</td><td class='n'>1</td><td class='n'>200.00</td>"))
        assertTrue(html.contains("<td>Total</td><td class='n'>2</td><td class='n'>300.00</td>"))
        assertTrue(html.contains("<td>Road works</td><td class='n'>60.00%</td>"))
        assertTrue(html.contains("<td>Drainage</td><td class='n'>30.00%</td>"))
        assertTrue(html.contains("as of October 30, 2026"))
    }
}
