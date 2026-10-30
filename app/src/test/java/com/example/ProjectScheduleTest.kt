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
        val ws = doc(ProjectSchedule.orderName("Suspension", "1", "Full"), "Approved", "Duration: 30 Days | Effectivity: 2026-01-01 | End: 2026-01-31")
        assertEquals("Work Suspension Order No. 1", ws.documentName)
        assertEquals("1st Continuance of Work Suspension Order No. 1" to "2026-01-31", ProjectSchedule.nextContinuance(ws, listOf(ws)))
        val c1 = doc("1st Continuance of Work Suspension Order No. 1", "Approved", ProjectSchedule.suspensionRemarks(15, "2026-01-31"))
        assertEquals("Duration: 15 Days | Effectivity: 2026-01-31 | End: 2026-02-15", c1.remarks)
        assertEquals("2nd Partial Continuance of Work Suspension Order No. 1" to "2026-02-15", ProjectSchedule.nextContinuance(ws, listOf(ws, c1), "Partial"))
        assertTrue(ProjectSchedule.isSuspension(ws))
        assertFalse(ProjectSchedule.isSuspension(c1))
        assertEquals("Full", ProjectSchedule.suspensionType(ws))
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(ProjectSchedule::ordinal))
    }

    @Test
    fun partialOrders_sayPartialInFront_andOlderNamesReadTheSame() {
        val ws = doc("Partial Work Suspension Order No. 2", "Approved", "Duration: 90 Days | Effectivity: 2026-05-26 | End: 2026-08-24")
        assertTrue(ProjectSchedule.isSuspension(ws))
        assertEquals("Partial", ProjectSchedule.suspensionType(ws))
        assertEquals("Partial Work Resumption Order No. 1", ProjectSchedule.orderName("Resumption", " 1 ", "Partial"))
        // A suspension saved before the new names: still a suspension, and its continuances still count.
        val old = doc("Work Suspension No. 1 (Partial)", "Approved", "Duration: 90 Days | Effectivity: 2026-05-26 | End: 2026-08-24")
        assertTrue(ProjectSchedule.isSuspension(old))
        assertEquals("Partial", ProjectSchedule.suspensionType(old))
        val oldC1 = doc("Continuance No. 1 of Work Suspension No. 1 (Partial)", "Approved", ProjectSchedule.suspensionRemarks(10, "2026-08-24"))
        val oldC2 = doc("Continuance No. 2 (Full) of Work Suspension No. 1 (Partial)", "Pending", ProjectSchedule.suspensionRemarks(5, "2026-09-03"))
        assertEquals("3rd Partial Continuance of Work Suspension Order No. 1" to "2026-09-08", ProjectSchedule.nextContinuance(old, listOf(old, oldC1, oldC2)))
        assertEquals("Partial Work Suspension Order No. 1", ProjectSchedule.orderLabel(old.documentName))
        assertEquals("1st Partial Continuance of Work Suspension Order No. 1", ProjectSchedule.orderLabel(oldC1.documentName))
        assertEquals("2nd Continuance of Work Suspension Order No. 1", ProjectSchedule.orderLabel(oldC2.documentName))
        assertEquals("Work Resumption Order No. 3", ProjectSchedule.orderLabel("Work Resumption No. 3"))
        for (same in listOf("Time Extension No. 1 - 30 Days", "2nd Partial Continuance of Work Suspension Order No. 1", "Partial Work Suspension Order No. 2"))
            assertEquals(same, ProjectSchedule.orderLabel(same))
    }
}
