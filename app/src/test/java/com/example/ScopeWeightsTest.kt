package com.example

import com.example.data.model.ScopeItem
import com.example.ui.dialogs.withAutoWeights
import org.junit.Assert.assertEquals
import org.junit.Test

class ScopeWeightsTest {

    @Test
    fun weightsAreEachItemsShareOfTheTotal() {
        val items = withAutoWeights(listOf(ScopeItem("Land Development", 750_000.0, 0.0), ScopeItem("Housing", 250_000.0, 0.0)))
        assertEquals(listOf(75.0, 25.0), items.map { it.weightPct })
    }

    @Test
    fun noAmounts_keepTheWeightsTheyHad() {
        val legacy = listOf(ScopeItem("Whole project", 0.0, 100.0))
        assertEquals(legacy, withAutoWeights(legacy))
    }
}
