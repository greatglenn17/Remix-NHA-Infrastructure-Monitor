package com.example

import com.example.data.model.Project
import com.example.ui.dialogs.completedOnError
import com.example.ui.dialogs.defaultCompletedOn
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletedProjectTest {

    private val today = LocalDate.of(2026, 11, 5)

    private fun project(actual: Double, status: String = "On-going") = Project(
        name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "[]",
        contractCostOriginal = 1.0, contractCostRevised = 1.0, contractDurationDays = 1,
        dateStarted = "2020-01-01", completionDateOriginal = "2020-12-31", completionDateRevised = "2020-12-31",
        status = status, targetAccomplishment = actual, actualAccomplishment = actual
    )

    @Test
    fun displayStatus_readsCompletedFrom100PercentOnly() {
        assertEquals("Completed", project(100.0).displayStatus)
        assertEquals("Completed", project(100.0, "Behind Schedule").displayStatus)
        assertEquals("On-going", project(99.99).displayStatus)
        assertEquals("Suspended", project(40.0, "Suspended").displayStatus)
    }

    @Test
    fun completedOn_mustBeSetPastTheStartAndNotInTheFuture() {
        assertNull(completedOnError("2019-03-01", "2020-06-30", today))
        assertNull(completedOnError("2026-11-05", "2026-11-05", today))
        assertEquals("Required", completedOnError("2019-03-01", "", today))
        assertEquals("Before the start date", completedOnError("2020-07-01", "2020-06-30", today))
        assertEquals("Can't be in the future", completedOnError("2019-03-01", "2026-11-06", today))
    }

    @Test
    fun markedCompleted_setsWorkAndDate_onlyWhenADateIsGiven() {
        val logged = project(62.0).copy(targetAccomplishment = 70.0)
        assertEquals(logged, logged.markedCompleted(null, 100.0))
        val done = logged.markedCompleted("2025-08-15", 100.0)
        assertEquals(100.0, done.actualAccomplishment, 0.0)
        assertEquals(100.0, done.targetAccomplishment, 0.0)
        assertEquals("2025-08-15", done.completionDateRevised)
        assertEquals("Completed", done.displayStatus)
    }

    @Test
    fun defaultCompletedOn_isTheProjectsCompletionDate_neverAfterToday() {
        val p = project(62.0)  // revised 2020-12-31
        assertEquals("2020-12-31", defaultCompletedOn(p, today))
        assertEquals("2026-11-05", defaultCompletedOn(p.copy(completionDateRevised = "2027-03-01"), today))
        assertEquals("2026-11-05", defaultCompletedOn(p.copy(completionDateOriginal = "?", completionDateRevised = "-"), today))
    }
}
