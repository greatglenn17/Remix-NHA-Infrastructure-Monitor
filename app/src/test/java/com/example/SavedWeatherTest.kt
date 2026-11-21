package com.example

import com.example.data.model.DailyHourlyWeather
import com.example.ui.dialogs.savedWeather
import org.junit.Assert.assertEquals
import org.junit.Test

/** Editing a weekly report opens with the weather it was saved with, not defaults. */
class SavedWeatherTest {
    @Test
    fun readsEachDaysHours_shortOrUnknownHoursReadFair() {
        val logs = listOf(
            DailyHourlyWeather(projectId = 1, weeklyReportId = 7, date = "2026-07-22", dayOfWeek = "Wednesday",
                hourlyConditionsCsv = (List(12) { "RAINY" } + List(12) { "STORMY" }).joinToString(",")),
            DailyHourlyWeather(projectId = 1, weeklyReportId = 7, date = "2026-07-23", dayOfWeek = "Thursday",
                hourlyConditionsCsv = " CLOUDY ,HAIL")
        )
        val weather = savedWeather(logs)
        assertEquals(List(12) { "RAINY" } + List(12) { "STORMY" }, weather["Wednesday"])
        assertEquals(listOf("CLOUDY") + List(23) { "FAIR" }, weather["Thursday"])
        assertEquals(setOf("Wednesday", "Thursday"), weather.keys)
    }
}
