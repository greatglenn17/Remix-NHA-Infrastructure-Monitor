package com.example

import com.example.data.model.Project
import com.example.ui.dialogs.completedOnError
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
}
