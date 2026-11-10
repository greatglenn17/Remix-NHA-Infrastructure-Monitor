package com.example

import com.example.data.model.AccomplishmentItem
import com.example.data.model.ScopeItem
import com.example.data.model.accomplishmentLine
import com.example.data.model.accomplishmentLines
import com.example.data.model.accomplishmentScope
import org.junit.Assert.assertEquals
import org.junit.Test

/** The weekly report's accomplishment is three lines; items of work fold into them by name. */
class AccomplishmentLinesTest {
    @Test fun itemsFoldByName() {
        assertEquals("Land Acquisition", accomplishmentLine("Land acquisition (TCT transfer)"))
        for (n in listOf("Housing Construction", "M1 - Housing unit", "Row houses", "200 units")) assertEquals(n, "Housing Construction", accomplishmentLine(n))
        // "Community" holds "unit" but is not a unit.
        for (n in listOf("Earthworks", "Roads and drainage", "Community facility", "Land Development")) assertEquals(n, "Land Development", accomplishmentLine(n))
    }

    @Test fun scopeWeightsAddUp() {
        val lines = accomplishmentScope(listOf(ScopeItem("Earthworks", 100.0, 10.0), ScopeItem("Roads", 300.0, 30.0), ScopeItem("Housing", 600.0, 60.0)))
        assertEquals(listOf("Land Acquisition" to 0.0, "Land Development" to 40.0, "Housing Construction" to 60.0), lines.map { it.name to it.weightPct })
    }

    @Test fun savedItemsBecomeWeightedLines_withTheSameTotal() {
        val saved = listOf(
            AccomplishmentItem("Earthworks", 10.0, 100.0, 80.0), AccomplishmentItem("Roads", 30.0, 20.0, 40.0),
            AccomplishmentItem("Housing", 60.0, 10.0, 5.0)
        )
        val lines = accomplishmentLines(saved)
        assertEquals(listOf("Land Acquisition", "Land Development", "Housing Construction"), lines.map { it.itemDescription })
        assertEquals(40.0, lines[1].targetPct, 1e-9)
        assertEquals(50.0, lines[1].actualPct, 1e-9)
        assertEquals(saved.sumOf { it.weightPct * it.actualPct }, lines.sumOf { it.weightPct * it.actualPct }, 1e-9)
        assertEquals(emptyList<AccomplishmentItem>(), accomplishmentLines(emptyList()))
    }
}
