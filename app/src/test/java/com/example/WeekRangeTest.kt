package com.example

import com.example.ui.dialogs.WeekRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekRangeTest {

    @Test
    fun sunday_belongsToTheWeekThatStartedOnMonday() {
        assertEquals("Oct 19, 2026 - Oct 25, 2026", WeekRange.containing(LocalDate.of(2026, 10, 25)))
    }

    @Test
    fun midweek_givesTheSameMondayToSundayRange() {
        assertEquals("Oct 19, 2026 - Oct 25, 2026", WeekRange.containing(LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun monday_startsANewWeek_acrossMonths() {
        assertEquals("Oct 26, 2026 - Nov 1, 2026", WeekRange.containing(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun weekAcrossNewYear_showsEachYear() {
        assertEquals("Dec 28, 2026 - Jan 3, 2027", WeekRange.containing(LocalDate.of(2026, 12, 31)))
    }
}
