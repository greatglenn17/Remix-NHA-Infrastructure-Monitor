package com.example.util

import com.example.data.model.PendingDocument
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Contract-time figures for weekly reports, from the project's own dates. */
object ProjectSchedule {

    /** The revised completion date when one has been set, otherwise the original. */
    fun effectiveCompletion(original: String, revised: String): String =
        revised.trim().takeIf { it.isNotEmpty() && it != "-" } ?: original

    /**
     * Days elapsed (counting the start date as day 1) and days remaining as of [asOf].
     * All dates are yyyy-MM-dd; unparseable dates give 0 to 0.
     */
    fun elapsedAndRemaining(dateStarted: String, completionDate: String, asOf: String): Pair<Int, Int> {
        val start = parse(dateStarted) ?: return 0 to 0
        val end = parse(completionDate) ?: return 0 to 0
        val day = parse(asOf) ?: return 0 to 0
        val elapsed = (ChronoUnit.DAYS.between(start, day) + 1).coerceAtLeast(0)
        val remaining = ChronoUnit.DAYS.between(day, end).coerceAtLeast(0)
        return elapsed.toInt() to remaining.toInt()
    }

    /**
     * Start date plus the contract duration plus the days of approved time extensions
     * (" - N Days" in the name) and approved work suspensions ("Duration: N Days" in the remarks).
     * Null when nothing approved adds days or the start date is unparseable.
     */
    fun revisedCompletion(dateStarted: String, contractDurationDays: Int, documents: List<PendingDocument>): String? {
        val approved = documents.filter { it.status.equals("Approved", ignoreCase = true) }
        val extensionDays = approved.filter { it.documentName.contains("Time Extension", ignoreCase = true) }.sumOf {
            Regex(".* - (\\d+) Days", RegexOption.IGNORE_CASE).find(it.documentName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        val suspensionDays = approved.filter { it.documentName.contains("Work Suspension", ignoreCase = true) }.sumOf {
            Regex("Duration: (\\d+) Days", RegexOption.IGNORE_CASE).find(it.remarks)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
        val added = extensionDays + suspensionDays
        if (added <= 0) return null
        return parse(dateStarted)?.plusDays((contractDurationDays + added).toLong())?.toString()
    }

    private fun parse(date: String): LocalDate? = try {
        LocalDate.parse(date.trim())
    } catch (_: Exception) {
        null
    }
}
