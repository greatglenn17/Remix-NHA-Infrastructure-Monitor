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
     * Start date plus the contract duration plus the days of approved time extensions (" - N Days" in the name).
     * Null when nothing approved adds days or the start date is unparseable.
     */
    fun revisedCompletion(dateStarted: String, contractDurationDays: Int, documents: List<PendingDocument>): String? {
        val added = addedDays(documents)
        if (added <= 0) return null
        return parse(dateStarted)?.plusDays((contractDurationDays + added).toLong())?.toString()
    }

    /**
     * Contract duration plus approved order days, else the span from the start to the stored revised date.
     * Null when neither applies, the dates are unparseable, the span equals the contract duration,
     * or the stored revised date is just the original completion (nothing was revised).
     */
    fun revisedDurationDays(dateStarted: String, contractDurationDays: Int, addedDays: Int, completionDateRevised: String, completionDateOriginal: String = ""): Int? {
        if (addedDays > 0) return contractDurationDays + addedDays
        if (completionDateRevised.trim() == completionDateOriginal.trim()) return null
        val start = parse(dateStarted) ?: return null
        val end = parse(completionDateRevised) ?: return null
        return ChronoUnit.DAYS.between(start, end).toInt().takeIf { it > 0 && it != contractDurationDays }
    }

    /**
     * Days added to the contract by approved Time Extensions (" - N Days"). A suspension stops the
     * clock but adds no days by itself: the days come with the time extension approved for it.
     */
    fun addedDays(documents: List<PendingDocument>): Int {
        val approved = documents.filter { it.status.equals("Approved", ignoreCase = true) }
        return approved.filter { it.documentName.contains("Time Extension", ignoreCase = true) }.sumOf {
            Regex(".* - (\\d+) Days", RegexOption.IGNORE_CASE).find(it.documentName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
    }

    fun isSuspension(doc: PendingDocument) = doc.documentName.startsWith("Work Suspension No.", ignoreCase = true)

    /**
     * Name of the next Continuance of [suspension], e.g. "Continuance No. 2 of Work Suspension No. 1 (Full)",
     * and the day it starts: the latest End of the suspension and its continuances so far.
     */
    fun nextContinuance(suspension: PendingDocument, documents: List<PendingDocument>): Pair<String, String> {
        val base = suspension.documentName.trim()
        val chain = documents.filter { it.documentName.trim().endsWith("of $base", ignoreCase = true) }
        val no = chain.size + 1
        val lastEnd = (chain + suspension).mapNotNull { endOf(it.remarks) }.maxOrNull()
        return "Continuance No. $no of $base" to (lastEnd?.toString() ?: "")
    }

    /** Remarks in the same "Duration | Effectivity | End" form as a suspension, so the schedule and forms read it. */
    fun suspensionRemarks(days: Int, effectivity: String): String {
        val end = parse(effectivity)?.plusDays(days.toLong())
        return "Duration: $days Days | Effectivity: $effectivity" + (end?.let { " | End: $it" } ?: "")
    }

    private fun endOf(remarks: String): LocalDate? =
        Regex("End: (\\d{4}-\\d{2}-\\d{2})").find(remarks)?.groupValues?.get(1)?.let(::parse)

    private fun parse(date: String): LocalDate? = try {
        LocalDate.parse(date.trim())
    } catch (_: Exception) {
        null
    }
}
