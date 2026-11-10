package com.example

import com.example.data.model.WEATHER_PRESETS
import com.example.data.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weekly report form's one-tap day weather; web/logic.js WEATHER_PRESETS must match. */
class WeatherPresetsTest {
    @Test
    fun everyPresetIs24KnownHours() {
        assertEquals(
            listOf("All Fair", "All Cloudy", "Cloudy AM / Showers PM", "All Rainy", "Rainy AM / Stormy PM", "All Stormy"),
            WEATHER_PRESETS.map { it.first }
        )
        val known = WeatherCondition.values().map { it.name }.toSet()
        WEATHER_PRESETS.forEach { (label, hours) ->
            assertEquals(label, 24, hours.size)
            assertTrue(label, hours.all { it in known })
        }
    }

    @Test
    fun halfAndHalf_givesTheFirstConditionToTheMorning() {
        val (_, hours) = WEATHER_PRESETS.first { it.first == "Rainy AM / Stormy PM" }
        assertEquals(List(12) { "RAINY" } + List(12) { "STORMY" }, hours)
        val (_, showers) = WEATHER_PRESETS.first { it.first == "Cloudy AM / Showers PM" }
        assertEquals(List(12) { "CLOUDY" } + List(12) { "RAIN_SHOWERS" }, showers)
    }
}
