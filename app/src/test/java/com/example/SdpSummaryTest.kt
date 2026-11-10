package com.example

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
    fun onlySaleableLotsCountByBand() {
        val items = listOf(
            Item("Saleable", 40.0, 0), Item("Saleable", 40.0, 20), Item("Saleable", 40.0, 21),
            Item("Saleable", 40.0, 60), Item("Saleable", 40.0, 61), Item("Saleable", 40.0, 100),
            Item("Road Lot", 300.0, 100), Item("Open Space", 500.0, 0), Item("Easement", 80.0, 0)
        )
        val s = SdpSummary.of(items)
        assertEquals(listOf(1, 1, 1, 1, 1, 1), s.saleableBands)
        assertEquals(SdpSummary.Totals(6, 240.0), s.saleable)
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
    fun printShowsOnlyTheLegendOverThePlan() {
        val project = Project(
            name = "San Ildefonso <Heights>", location = "Bulacan", implementingOffice = "", contractor = "", scopeOfWork = "",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 360,
            dateStarted = "2026-04-13", completionDateOriginal = "2027-04-07", completionDateRevised = ""
        )
        val s = SdpSummary.of(listOf(
            Item("Saleable", 40.0, 10), Item("Saleable", 40.0, 30), Item("Open Space", 100.0, 0),
            Item("Road Lot", 200.0, 0), Item("Road Lot", 200.0, 0), Item("Road Lot", 200.0, SdpSummary.ROAD_CONCRETING), Item("Road Lot", 200.0, 100)
        ))
        val bands = listOf("#111111", "#222222", "#333333", "#444444", "#555555", "#666666")
        val html = BlueBookForms.sdpPlan(project, listOf(BlueBookForms.SdpSheet("", "data:image/png;base64,AA", s)), bands, LocalDate.of(2026, 10, 30))
        assertTrue(html.contains("size: A4; margin: 6mm;"))
        assertTrue(html.contains("<h1>STATUS OF CONSTRUCTION</h1>"))
        assertTrue(html.contains("San Ildefonso &lt;Heights&gt;, Bulacan &#183; as of October 30, 2026"))
        assertTrue(html.contains("<div class='plan'><img src='data:image/png;base64,AA'><table class='lg'><tr><th class='sec' colspan='2'>LEGEND</th>"))
        assertTrue(html.contains("background:#111111'></i>Not yet started</td><td class='n'>0</td>"))
        assertTrue(html.contains("background:#222222'></i>1-20%</td><td class='n'>1</td>"))
        assertTrue(html.contains("<td>Total units</td><td class='n'>2</td>"))
        // Road lots: red not completed, yellow concreting, green completed; colours only, no counts.
        assertTrue(html.contains("<th class='sub' colspan='2'>Road lot</th>"))
        assertTrue(html.contains("<td colspan='2'><i class='sw' style='background:#111111'></i>Not completed</td></tr>"))
        assertTrue(html.contains("<td colspan='2'><i class='sw' style='background:#444444'></i>Concreting</td></tr>"))
        assertTrue(html.contains("<td colspan='2'><i class='sw' style='background:#666666'></i>Completed</td></tr>"))
        for (gone in listOf("NON-SALEABLE", "ROAD LOTS", "Drainage", "Area (sq.m.)")) assertTrue(gone, !html.contains(gone))
        // No road lots yet: the road lot key still shows.
        val noRoads = BlueBookForms.sdpPlan(project, listOf(BlueBookForms.SdpSheet("", "data:image/png;base64,AA", SdpSummary.of(listOf(Item("Saleable", 40.0, 10))))), bands, LocalDate.of(2026, 10, 30))
        for (state in listOf("Road lot", "Not completed", "Concreting", "Completed")) assertTrue(state, noRoads.contains(state))
    }

    @Test
    fun twoSitesPrintSideBySideOnALandscapePageWithEachSitesCounts() {
        val project = Project(
            name = "P", location = "", implementingOffice = "", contractor = "", scopeOfWork = "",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 0,
            dateStarted = "", completionDateOriginal = "", completionDateRevised = ""
        )
        val bands = listOf("#111111", "#222222", "#333333", "#444444", "#555555", "#666666")
        val sheets = listOf(
            BlueBookForms.SdpSheet("Phase 1", "data:a", SdpSummary.of(listOf(Item("Saleable", 40.0, 10), Item("Saleable", 40.0, 10)))),
            BlueBookForms.SdpSheet("Phase 2", "data:b", SdpSummary.of(listOf(Item("Saleable", 40.0, 100))))
        )
        val html = BlueBookForms.sdpPlan(project, sheets, bands, LocalDate.of(2026, 11, 7), landscape = true)
        assertTrue(html.contains("size: A4 landscape; margin: 6mm;"))
        assertTrue(html.contains("<div class='plan multi row'><figure><figcaption>Phase 1</figcaption><img src='data:a' style='max-height:151mm'></figure>"))
        assertTrue(html.contains("<th class='sub'>Progress</th><th class='sub'>Phase 1</th><th class='sub'>Phase 2</th><th class='sub'>Total</th>"))
        assertTrue(html.contains("1-20%</td><td class='n'>2</td><td class='n'>0</td><td class='n'>2</td>"))
        assertTrue(html.contains("<td>Total units</td><td class='n'>2</td><td class='n'>1</td><td class='n'>3</td>"))
        // Portrait: one above the other, sharing the height left under the legend.
        val portrait = BlueBookForms.sdpPlan(project, sheets, bands, LocalDate.of(2026, 11, 7))
        assertTrue(portrait.contains("size: A4; margin: 6mm;"))
        assertTrue(portrait.contains("<div class='plan multi'><figure><figcaption>Phase 1</figcaption><img src='data:a' style='max-height:77mm'>"))
        assertTrue(html.contains("<tr><th class='sub' colspan='4'>Road lot</th></tr>"))
    }
}
