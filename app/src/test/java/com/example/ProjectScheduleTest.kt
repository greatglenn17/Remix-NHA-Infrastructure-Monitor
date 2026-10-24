package com.example

import com.example.util.ProjectSchedule
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectScheduleTest {

    @Test
    fun midProject_countsStartDayAsElapsed() {
        // 30-day contract, Aug 1 - Aug 30; report week ends Sunday Aug 9
        assertEquals(9 to 21, ProjectSchedule.elapsedAndRemaining("2026-08-01", "2026-08-30", "2026-08-09"))
    }

    @Test
    fun afterCompletion_remainingIsZero() {
        assertEquals(40 to 0, ProjectSchedule.elapsedAndRemaining("2026-08-01", "2026-08-30", "2026-09-09"))
    }

    @Test
    fun beforeStart_elapsedIsZero() {
        assertEquals(0 to 30, ProjectSchedule.elapsedAndRemaining("2026-08-01", "2026-08-30", "2026-07-31"))
    }

    @Test
    fun unparseableDates_giveZeros() {
        assertEquals(0 to 0, ProjectSchedule.elapsedAndRemaining("", "-", "2026-08-09"))
    }

    @Test
    fun completionDate_prefersRevisedOverOriginal() {
        assertEquals("2026-09-15", ProjectSchedule.effectiveCompletion("2026-08-30", "2026-09-15"))
        assertEquals("2026-08-30", ProjectSchedule.effectiveCompletion("2026-08-30", ""))
        assertEquals("2026-08-30", ProjectSchedule.effectiveCompletion("2026-08-30", "-"))
    }
}
