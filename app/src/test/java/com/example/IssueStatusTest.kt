package com.example

import com.example.data.model.ISSUE_STATUSES
import com.example.data.model.ProjectIssue
import com.example.data.model.isOverdue
import com.example.data.model.isResolved
import com.example.data.model.shownStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Issues are Pending until Resolved; statuses saved before that (Approved, No Action, Closed) read as resolved. */
class IssueStatusTest {
    private fun issue(status: String, target: String = "") =
        ProjectIssue(projectId = 1, date = "2026-11-01", description = "d", actionTaken = "a", remarks = "", loggedBy = "x", status = status, targetDate = target)

    @Test fun theDialogOffersPendingAndResolved() {
        assertEquals(listOf("Pending", "Resolved"), ISSUE_STATUSES)
    }

    @Test fun oldStatusesReadAsResolved_othersStayOpen() {
        val statuses = listOf("Resolved", "Closed", "Approved", "No Action", "Pending", "Open", "")
        assertEquals(listOf(true, true, true, true, false, false, false), statuses.map { issue(it).isResolved })
        assertEquals(listOf("Resolved", "Resolved", "Resolved", "Resolved", "Pending", "Pending", "Pending"), statuses.map { issue(it).shownStatus })
    }

    @Test fun onlyAnUnresolvedIssueIsOverdue() {
        val today = LocalDate.parse("2026-11-09")
        assertEquals(listOf(true, true, false), listOf("Pending", "Open", "Approved").map { issue(it, "2026-11-01").isOverdue(today) })
        assertEquals(false, issue("Pending", "2026-12-01").isOverdue(today))
    }
}
