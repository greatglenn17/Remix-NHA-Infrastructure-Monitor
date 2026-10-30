package com.example

import com.example.data.model.PendingDocument
import com.example.util.ProjectSchedule
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun doc(name: String, status: String, remarks: String = "") =
        PendingDocument(projectId = 1, documentName = name, status = status, remarks = remarks)

    @Test
    fun revisedCompletion_addsApprovedExtensionDaysOnly() {
        val docs = listOf(
            doc("Time Extension No. 1 - 30 Days", "Approved"),
            doc("Work Suspension Order No. 1", "Approved", "Duration: 10 Days")
        )
        // The approved suspension stops the clock but adds no days; only the time extension does.
        assertEquals("2026-05-11", ProjectSchedule.revisedCompletion("2026-01-01", 100, docs))
    }

    @Test
    fun revisedCompletion_ignoresPending() {
        assertNull(ProjectSchedule.revisedCompletion("2026-01-01", 100, listOf(doc("Time Extension No. 1 - 30 Days", "Pending"))))
    }

    @Test
    fun revisedCompletion_blankStart_isNull() {
        assertNull(ProjectSchedule.revisedCompletion("", 100, listOf(doc("Time Extension No. 1 - 30 Days", "Approved"))))
    }

    @Test
    fun revisedDuration_fromApprovedOrders() {
        assertEquals(330, ProjectSchedule.revisedDurationDays("2026-01-05", 300, 30, "-"))
    }

    @Test
    fun revisedDuration_fromStoredDate() {
        assertEquals(330, ProjectSchedule.revisedDurationDays("2026-01-05", 300, 0, "2026-12-01"))
    }

    @Test
    fun revisedDuration_noneWhenSameOrBlank() {
        assertNull(ProjectSchedule.revisedDurationDays("2026-01-05", 300, 0, "-"))
        assertNull(ProjectSchedule.revisedDurationDays("2026-01-05", 300, 0, "2026-11-01"))
        // Revised date left equal to the original: an approved suspension alone must not show a duration.
        assertNull(ProjectSchedule.revisedDurationDays("2026-04-13", 360, 0, "2027-04-07", "2027-04-07"))
    }

    @Test
    fun revisedDuration_nullWhenStoredDateNotAfterStart() {
        assertNull(ProjectSchedule.revisedDurationDays("2026-01-05", 300, 0, "2026-01-05"))
        assertNull(ProjectSchedule.revisedDurationDays("2026-01-05", 300, 0, "2025-12-01"))
    }

    @Test
    fun revisedDuration_badDate() {
        assertNull(ProjectSchedule.revisedDurationDays("", 300, 0, "2026-12-01"))
    }

    @Test
    fun addedDays_sumsApprovedTimeExtensionsOnly() {
        val docs = listOf(
            doc("Time Extension No. 1 - 30 Days", "Approved"),
            doc("Time Extension No. 2 - 15 Days", "Pending"),
            doc("Work Suspension Order No. 1", "Approved", "Duration: 10 Days")
        )
        assertEquals(30, ProjectSchedule.addedDays(docs))
        assertEquals(0, ProjectSchedule.addedDays(emptyList()))
    }

    @Test
    fun continuance_numbersAndStartsWhereTheChainEnds() {
        val ws = doc("Work Suspension No. 1 (Full)", "Approved", "Duration: 30 Days | Effectivity: 2026-01-01 | End: 2026-01-31")
        assertEquals("Continuance No. 1 of Work Suspension No. 1 (Full)" to "2026-01-31", ProjectSchedule.nextContinuance(ws, listOf(ws)))
        val c1 = doc("Continuance No. 1 of Work Suspension No. 1 (Full)", "Approved", ProjectSchedule.suspensionRemarks(15, "2026-01-31"))
        assertEquals("Duration: 15 Days | Effectivity: 2026-01-31 | End: 2026-02-15", c1.remarks)
        assertEquals("Continuance No. 2 of Work Suspension No. 1 (Full)" to "2026-02-15", ProjectSchedule.nextContinuance(ws, listOf(ws, c1)))
        assertTrue(ProjectSchedule.isSuspension(ws))
        assertFalse(ProjectSchedule.isSuspension(c1))
        // A continuance may be Partial on a Full suspension; the type sits before "of" so the chain still counts it.
        assertEquals("Full", ProjectSchedule.suspensionType(ws))
        val c2 = doc(ProjectSchedule.nextContinuance(ws, listOf(ws, c1), "Partial").first, "Pending", ProjectSchedule.suspensionRemarks(10, "2026-02-15"))
        assertEquals("Continuance No. 2 (Partial) of Work Suspension No. 1 (Full)", c2.documentName)
        assertEquals("Continuance No. 3 (Full) of Work Suspension No. 1 (Full)" to "2026-02-25", ProjectSchedule.nextContinuance(ws, listOf(ws, c1, c2), "Full"))
        assertEquals("Partial", ProjectSchedule.suspensionType(doc("Work Suspension No. 2 (Partial)", "Pending", "")))
    }
}
