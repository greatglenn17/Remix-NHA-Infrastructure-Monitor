package com.example

import com.example.data.model.Project
import com.example.util.AttentionFlag
import com.example.util.AttentionKind
import com.example.util.AttentionKind.BEHIND
import com.example.util.AttentionKind.DUE_SOON
import com.example.util.AttentionKind.NO_RECENT_UPDATE
import com.example.util.AttentionKind.OVERDUE
import com.example.util.AttentionLevel
import com.example.util.AttentionLevel.CRITICAL
import com.example.util.AttentionLevel.STALE
import com.example.util.AttentionLevel.WARNING
import com.example.util.Portfolio
import com.example.util.PortfolioFilter
import com.example.util.PortfolioItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioTest {

    private fun item(
        name: String,
        vararg flags: Pair<AttentionKind, AttentionLevel>,
        variance: Double = 0.0,
        pendingDocs: Int = 0
    ) = PortfolioItem(
        project = Project(
            name = name, location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "[]",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 0,
            dateStarted = "", completionDateOriginal = "", completionDateRevised = "",
            targetAccomplishment = 50.0, actualAccomplishment = 50.0 + variance
        ),
        flags = flags.map { AttentionFlag(it.first, it.second, it.first.name) },
        billedFraction = null,
        billedTotal = 0.0,
        contractCost = 0.0,
        pendingDocsCount = pendingDocs,
        lastFieldUpdate = null
    )

    @Test fun summarize_countsProjectsPerFlagAndDocuments() {
        val s = Portfolio.summarize(listOf(
            item("A", BEHIND to WARNING, pendingDocs = 2),
            item("B", BEHIND to CRITICAL, DUE_SOON to WARNING, pendingDocs = 3),
            item("C", OVERDUE to CRITICAL)
        ))
        assertEquals(2, s.behind)
        assertEquals(5, s.waitingApproval)
        assertEquals(1, s.dueSoon)
        assertEquals(0, s.billingAhead)
    }

    @Test fun matches_nullFilter_matchesAll() {
        assertTrue(Portfolio.matches(item("A"), null))
    }

    @Test fun matches_waitingApproval_usesPendingDocs() {
        assertTrue(Portfolio.matches(item("A", pendingDocs = 2), PortfolioFilter.WAITING_APPROVAL))
    }

    @Test fun attentionQueue_ordersCriticalThenWarningThenStale() {
        val q = Portfolio.attentionQueue(listOf(
            item("A", NO_RECENT_UPDATE to STALE),
            item("B", DUE_SOON to WARNING),
            item("C", OVERDUE to CRITICAL)
        ))
        assertEquals(listOf(CRITICAL, WARNING, STALE), q.map { it.second.level })
    }

    @Test fun attentionQueue_breaksTiesByWorseVariance() {
        val q = Portfolio.attentionQueue(listOf(
            item("Mild", BEHIND to CRITICAL, variance = -16.0),
            item("Worse", BEHIND to CRITICAL, variance = -20.0)
        ))
        assertEquals(listOf("Worse", "Mild"), q.map { it.first.project.name })
    }

    @Test fun sortByAttention_flaggedFirst_thenByName() {
        val sorted = Portfolio.sortByAttention(listOf(
            item("Charlie"), item("Alpha"), item("Zulu", NO_RECENT_UPDATE to STALE), item("Bravo")
        ))
        assertEquals(listOf("Zulu", "Alpha", "Bravo", "Charlie"), sorted.map { it.project.name })
    }
}
