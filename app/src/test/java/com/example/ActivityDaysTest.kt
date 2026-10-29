package com.example

import com.example.util.ActivityDays
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityDaysTest {

    @Test
    fun rangesCoverEveryDayBetween() {
        assertEquals(setOf(0, 1, 2), ActivityDays.covered("Monday - Wednesday"))
        assertEquals(setOf(3), ActivityDays.covered("Thursday"))
        // A Saturday-to-Friday week wraps past Sunday.
        assertEquals((0..6).toSet(), ActivityDays.covered("Saturday - Friday"))
    }

    @Test
    fun labelAndSplitRoundTrip() {
        assertEquals("Thursday - Saturday", ActivityDays.label("Thursday", "Saturday"))
        assertEquals("Friday", ActivityDays.label("Friday", "Friday"))
        assertEquals("Thursday" to "Saturday", ActivityDays.split("Thursday - Saturday"))
        assertEquals("Monday" to null, ActivityDays.split("Monday"))
    }
}
