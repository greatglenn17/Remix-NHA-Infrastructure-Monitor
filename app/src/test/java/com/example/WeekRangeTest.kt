package com.example

import com.example.ui.dialogs.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekRangeTest {

    @Test
    fun friday_belongsToTheWeekThatStartedOnSaturday() {
        assertEquals("Oct 24, 2026 - Oct 30, 2026", WeekRange.containing(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun midweek_givesTheSameSaturdayToFridayRange() {
        assertEquals("Oct 24, 2026 - Oct 30, 2026", WeekRange.containing(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun saturday_startsANewWeek_acrossMonths() {
        assertEquals("Oct 31, 2026 - Nov 6, 2026", WeekRange.containing(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun weekAcrossNewYear_showsEachYear() {
        assertEquals("Dec 26, 2026 - Jan 1, 2027", WeekRange.containing(LocalDate.of(2026, 12, 31)))
    }
}
