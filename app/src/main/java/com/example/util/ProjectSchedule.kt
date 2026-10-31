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

    // "Partial Work Suspension Order No. 1", or the older "Work Suspension No. 1 (Partial)".
    private val ORDER = Regex("^(Partial )?Work (Suspension|Resumption) (?:Order )?No\\. ?(\\S+?)(?: \\((Full|Partial)\\))?$", RegexOption.IGNORE_CASE)
    // "2nd Partial Continuance of …", or the older "Continuance No. 2 (Partial) of …".
    private val CONTINUANCE = Regex("^(?:Continuance No\\. ?(\\d+)(?: \\((Full|Partial)\\))?|\\d+\\w\\w( Partial)? Continuance) of (.+)$", RegexOption.IGNORE_CASE)

    private fun isPartial(order: MatchResult) = order.groupValues[1].isNotEmpty() || order.groupValues[4].equals("Partial", ignoreCase = true)

    fun isSuspension(doc: PendingDocument) = ORDER.find(doc.documentName.trim())?.groupValues?.get(2).equals("Suspension", ignoreCase = true)

    /** "Partial" when the suspension's name says so, else "Full". */
    fun suspensionType(suspension: PendingDocument) = if (ORDER.find(suspension.documentName.trim())?.let(::isPartial) == true) "Partial" else "Full"

    /** Name of a new suspension or resumption ([kind]) order: a Partial one says so in front, a Full one says nothing. */
    fun orderName(kind: String, no: String, type: String) = (if (type == "Partial") "Partial " else "") + "Work $kind Order No. ${no.trim()}"

    /** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st. */
    fun ordinal(n: Int) = "$n" + if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

    /** The continuances of [suspension] among [documents], in the order they were added. */
    fun continuances(suspension: PendingDocument, documents: List<PendingDocument>) =
        documents.filter { continuanceOf(it.documentName) == baseOf(suspension.documentName) }

    /**
     * Name of the next continuance of [suspension] of [type], e.g. "2nd Partial Continuance of Work Suspension Order No. 1",
     * and the day it starts: the latest End of the suspension and its continuances so far.
     */
    fun nextContinuance(suspension: PendingDocument, documents: List<PendingDocument>, type: String = suspensionType(suspension)): Pair<String, String> {
        val chain = continuances(suspension, documents)
        val lastEnd = (chain + suspension).mapNotNull { endOf(it.remarks) }.maxOrNull()
        return "${ordinal(chain.size + 1)}${if (type == "Partial") " Partial" else ""} Continuance of ${baseOf(suspension.documentName)}" to (lastEnd?.toString() ?: "")
    }

    /**
     * An order's name as the cards and forms show it. Older names read in the same form as new ones:
     * "Work Suspension No. 1 (Partial)" as "Partial Work Suspension Order No. 1", "Continuance No. 2 of …" as "2nd … Continuance of …".
     */
    fun orderLabel(name: String): String {
        val n = name.trim()
        CONTINUANCE.find(n)?.let { m ->
            val (no, type, _, base) = m.destructured
            if (no.isEmpty()) return n
            // An older continuance without a type took its suspension's.
            val partial = if (type.isEmpty()) ORDER.find(base)?.let(::isPartial) == true else type.equals("Partial", ignoreCase = true)
            return "${ordinal(no.toInt())}${if (partial) " Partial" else ""} Continuance of ${baseOf(base)}"
        }
        return ORDER.find(n)?.let { m -> orderName(m.groupValues[2].replaceFirstChar { it.uppercase() }, m.groupValues[3], if (isPartial(m)) "Partial" else "Full") } ?: n
    }

    /** "Work Suspension Order No. 1", the suspension a continuance names, whatever the suspension's type. */
    private fun baseOf(suspensionName: String) = ORDER.find(suspensionName.trim())?.let { "Work Suspension Order No. ${it.groupValues[3]}" } ?: suspensionName.trim()

    /** The suspension ([baseOf]) that [name] continues, or null when it is not a continuance. */
    private fun continuanceOf(name: String) = CONTINUANCE.find(name.trim())?.groupValues?.get(4)?.let(::baseOf)

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
