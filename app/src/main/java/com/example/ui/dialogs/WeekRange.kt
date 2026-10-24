package com.example.ui.dialogs

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The Monday-to-Sunday week a new weekly report starts on, written the way the week picker
 * writes a chosen range ("Oct 19, 2026 - Oct 25, 2026"), so every screen that reads it parses it.
 */
object WeekRange {
    private val format = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    fun containing(day: LocalDate): String {
        val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return "${monday.format(format)} - ${monday.plusDays(6).format(format)}"
    }
}
