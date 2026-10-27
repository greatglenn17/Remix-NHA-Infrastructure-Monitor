package com.example.util

import com.example.data.model.Project
import java.time.LocalDate

data class PortfolioItem(
    val project: Project,
    val flags: List<AttentionFlag>,
    val billedFraction: Float?,
    val billedTotal: Double,
    val contractCost: Double,
    val pendingDocsCount: Int,       // this project's orders waiting approval
    val lastFieldUpdate: LocalDate?
)

data class PortfolioSummary(val behind: Int, val billingAhead: Int, val waitingApproval: Int, val dueSoon: Int)

enum class PortfolioFilter { BEHIND, BILLING_AHEAD, WAITING_APPROVAL, DUE_SOON }

/** Counts, filters and ordering for the portfolio home. */
object Portfolio {
    const val NEEDS_ATTENTION_SORT = "Needs attention first"

    fun summarize(items: List<PortfolioItem>) = PortfolioSummary(
        behind = items.count { it.has(AttentionKind.BEHIND) },
        billingAhead = items.count { it.has(AttentionKind.BILLING_AHEAD) },
        waitingApproval = items.sumOf { it.pendingDocsCount },
        dueSoon = items.count { it.has(AttentionKind.DUE_SOON) }
    )

    fun matches(item: PortfolioItem, filter: PortfolioFilter?): Boolean = when (filter) {
        null -> true
        PortfolioFilter.BEHIND -> item.has(AttentionKind.BEHIND)
        PortfolioFilter.BILLING_AHEAD -> item.has(AttentionKind.BILLING_AHEAD)
        PortfolioFilter.WAITING_APPROVAL -> item.pendingDocsCount > 0
        PortfolioFilter.DUE_SOON -> item.has(AttentionKind.DUE_SOON)
    }

    fun attentionQueue(items: List<PortfolioItem>): List<Pair<PortfolioItem, AttentionFlag>> =
        items.flatMap { item -> item.flags.map { item to it } }
            .sortedWith(compareBy({ it.second.level }, { it.first.project.variance }, { it.first.project.name }))

    fun sortByAttention(items: List<PortfolioItem>): List<PortfolioItem> =
        items.sortedWith(compareBy(
            { it.flags.minOfOrNull { f -> f.level.ordinal } ?: AttentionLevel.entries.size },
            { it.project.variance },
            { it.project.name }
        ))

    private fun PortfolioItem.has(kind: AttentionKind) = flags.any { it.kind == kind }
}
