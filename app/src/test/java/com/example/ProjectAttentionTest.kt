package com.example

import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.util.AttentionKind
import com.example.util.AttentionKind.BEHIND
import com.example.util.AttentionKind.BILLING_AHEAD
import com.example.util.AttentionKind.DUE_SOON
import com.example.util.AttentionKind.NO_RECENT_UPDATE
import com.example.util.AttentionKind.OVERDUE
import com.example.util.AttentionKind.WAITING_APPROVAL
import com.example.util.AttentionLevel
import com.example.util.AttentionLevel.CRITICAL
import com.example.util.AttentionLevel.STALE
import com.example.util.AttentionLevel.WARNING
import com.example.util.ProjectAttention
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: ContractCost reads the scope of work with org.json.
@RunWith(RobolectricTestRunner::class)
class ProjectAttentionTest {

    private val today = LocalDate.of(2026, 10, 26)
    private val recent = LocalDate.of(2026, 10, 25)

    private fun project(
        target: Double = 0.0,
        actual: Double = 0.0,
        status: String = "On-going",
        start: String = "2026-01-01",
        completion: String = "2026-12-31",
        revised: String = "-",
        cost: Double = 1_000_000.0
    ) = Project(
        id = 1, name = "P", location = "L", implementingOffice = "O", contractor = "C",
        scopeOfWork = "[]", contractCostOriginal = cost, contractCostRevised = cost,
        contractDurationDays = 365, dateStarted = start, completionDateOriginal = completion,
        completionDateRevised = revised, status = status,
        targetAccomplishment = target, actualAccomplishment = actual
    )

    private fun flags(
        p: Project,
        docs: List<PendingDocument> = emptyList(),
        billed: Double = 0.0,
        latest: LocalDate? = recent
    ) = ProjectAttention.flags(p, docs, billed, latest, today)

    private fun List<com.example.util.AttentionFlag>.of(kind: AttentionKind) = filter { it.kind == kind }
    private fun List<com.example.util.AttentionFlag>.level(kind: AttentionKind): AttentionLevel? = of(kind).singleOrNull()?.level

    @Test fun slippageJustAboveWarning_notFlagged() {
        assertEquals(emptyList<Any>(), flags(project(50.0, 40.1)).of(BEHIND))
    }

    @Test fun slippageAtTen_isWarning() {
        assertEquals(WARNING, flags(project(50.0, 40.0)).level(BEHIND))
    }

    @Test fun slippageAtTen_withFloatingPointError_isWarning() {
        // 6.40 - 16.40 is -9.999999999999998 in doubles.
        assertEquals(WARNING, flags(project(16.40, 6.40)).level(BEHIND))
    }

    @Test fun slippageAtFifteen_withFloatingPointError_isCritical() {
        assertEquals(CRITICAL, flags(project(16.06, 1.06)).level(BEHIND))
    }

    @Test fun slippageAtFifteen_isCritical() {
        val f = flags(project(50.0, 35.0)).of(BEHIND).single()
        assertEquals(CRITICAL, f.level)
        assertEquals("15.0% behind schedule", f.message)
    }

    @Test fun behindScheduleStatus_isWarningEvenWithSmallSlippage() {
        assertEquals(WARNING, flags(project(50.0, 47.0, status = "Behind Schedule")).level(BEHIND))
    }

    @Test fun billingGapOfExactlyTen_notFlagged() {
        assertEquals(emptyList<Any>(), flags(project(40.0, 40.0), billed = 500_000.0).of(BILLING_AHEAD))
    }

    @Test fun billingGapAboveTen_isWarning() {
        val f = flags(project(40.0, 40.0), billed = 501_000.0).of(BILLING_AHEAD).single()
        assertEquals(WARNING, f.level)
        assertEquals("Billed 50.1%, work done 40.0%", f.message)
    }

    @Test fun zeroContractCost_noBillingFlag() {
        assertEquals(emptyList<Any>(), flags(project(cost = 0.0), billed = 100_000.0).of(BILLING_AHEAD))
    }

    @Test fun pendingOrder_isWarning_checklistIsNot() {
        val docs = listOf(
            PendingDocument(projectId = 1, documentName = "Variation Order No. 1 (Additive) - PHP 5", status = "Pending"),
            PendingDocument(projectId = 1, documentName = "Contractor Notice to Proceed (NTP)", status = "Pending")
        )
        val f = flags(project(), docs).of(WAITING_APPROVAL).single()
        assertEquals(WARNING, f.level)
        assertEquals("1 order waiting approval", f.message)
    }

    @Test fun waitingApproval_countsOnlyUnapprovedOrders() {
        val docs = listOf(
            PendingDocument(projectId = 1, documentName = "Time Extension No. 1 - 30 Days", status = "Submitted"),
            PendingDocument(projectId = 1, documentName = "Work Suspension Order No. 1", status = "Pending"),
            PendingDocument(projectId = 1, documentName = "Variation Order No. 1 (Additive) - PHP 5", status = "approved"),
            PendingDocument(projectId = 1, documentName = "Program of Works", status = "Pending")
        )
        assertEquals(2, ProjectAttention.waitingApproval(docs))
    }

    @Test fun dueInThirtyDays_isWarning() {
        val f = flags(project(completion = "2026-11-25")).of(DUE_SOON).single()
        assertEquals(WARNING, f.level)
        assertEquals("Due in 30 days", f.message)
    }

    @Test fun dueInThirtyOneDays_notFlagged() {
        assertEquals(emptyList<Any>(), flags(project(completion = "2026-11-26")).of(DUE_SOON))
    }

    @Test fun revisedCompletion_wins() {
        assertEquals(emptyList<Any>(), flags(project(completion = "2026-10-30", revised = "2027-03-01")).of(DUE_SOON))
    }

    @Test fun pastCompletion_isOverdueCritical() {
        val f = flags(project(actual = 80.0, completion = "2026-10-20")).of(OVERDUE).single()
        assertEquals(CRITICAL, f.level)
        assertEquals("Overdue by 6 days", f.message)
    }

    @Test fun finishedProject_notOverdueNorStale() {
        val f = flags(project(actual = 100.0, completion = "2026-10-20"), latest = null)
        assertEquals(emptyList<Any>(), f.of(OVERDUE))
        assertEquals(emptyList<Any>(), f.of(NO_RECENT_UPDATE))
    }

    @Test fun reportFourteenDaysAgo_isStale() {
        val f = flags(project(), latest = LocalDate.of(2026, 10, 12)).of(NO_RECENT_UPDATE).single()
        assertEquals(STALE, f.level)
        assertEquals("No field update in 14 days", f.message)
    }

    @Test fun reportThirteenDaysAgo_notStale() {
        assertEquals(emptyList<Any>(), flags(project(), latest = LocalDate.of(2026, 10, 13)).of(NO_RECENT_UPDATE))
    }

    @Test fun noReports_countsFromStart() {
        assertEquals(STALE, flags(project(start = "2026-10-01"), latest = null).level(NO_RECENT_UPDATE))
    }

    @Test fun futureStart_notStale() {
        assertEquals(emptyList<Any>(), flags(project(start = "2026-11-15"), latest = null).of(NO_RECENT_UPDATE))
    }

    @Test fun suspended_notStale() {
        assertEquals(emptyList<Any>(), flags(project(status = "Suspended"), latest = null).of(NO_RECENT_UPDATE))
    }

    @Test fun blankDates_noDateFlags() {
        val f = flags(project(start = "", completion = "-", revised = "-"), latest = null)
        assertFalse(f.any { it.kind in setOf(DUE_SOON, OVERDUE, NO_RECENT_UPDATE) })
    }

    @Test fun flags_areSortedByLevel() {
        val f = flags(project(50.0, 30.0), latest = LocalDate.of(2026, 9, 1))
        assertEquals(CRITICAL, f.first().level)
        assertEquals(STALE, f.last().level)
    }
}
