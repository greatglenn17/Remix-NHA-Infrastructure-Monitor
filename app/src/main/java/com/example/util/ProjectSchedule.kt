package com.example.util

import com.example.data.model.PendingDocument
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Contract-time figures for weekly reports, from the project's own dates. */
object ProjectSchedule {

    private val DISPLAY_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    /** "2026-10-20" as "Oct 20, 2026" for the screen; anything that is not a yyyy-MM-dd date is shown as stored. */
    fun displayDate(iso: String): String = runCatching { LocalDate.parse(iso.trim()).format(DISPLAY_DATE) }.getOrDefault(iso)

    fun displayDate(date: LocalDate): String = date.format(DISPLAY_DATE)

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
     * Day [days] counting [start] as day 1: a contract's completion date, a suspension's End.
     * 100 days from Jan 1, 2026 end Apr 10, 2026; 90 days from May 26, 2026 end Aug 23, 2026.
     */
    fun lastDay(start: LocalDate, days: Int): LocalDate = start.plusDays(days - 1L)

    /**
     * The last day ([lastDay]) of the contract duration plus the days of approved time extensions (" - N Days" in the name).
     * Null when nothing approved adds days or the start date is unparseable.
     */
    fun revisedCompletion(dateStarted: String, contractDurationDays: Int, documents: List<PendingDocument>): String? {
        val added = addedDays(documents)
        if (added <= 0) return null
        return parse(dateStarted)?.let { lastDay(it, contractDurationDays + added) }?.toString()
    }

    /**
     * Contract duration plus approved order days, else the days from the start through the stored revised date (both counted).
     * Null when neither applies, the dates are unparseable, the span equals the contract duration,
     * or the stored revised date is just the original completion (nothing was revised).
     */
    fun revisedDurationDays(dateStarted: String, contractDurationDays: Int, addedDays: Int, completionDateRevised: String, completionDateOriginal: String = ""): Int? {
        if (addedDays > 0) return contractDurationDays + addedDays
        if (completionDateRevised.trim() == completionDateOriginal.trim()) return null
        val start = parse(dateStarted) ?: return null
        val end = parse(completionDateRevised) ?: return null
        // A stored date on or before the start is no revision (a span of 1 day or less).
        return (ChronoUnit.DAYS.between(start, end).toInt() + 1).takeIf { it > 1 && it != contractDurationDays }
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

    /** Key dates' [orders]: each suspension, then its continuances, then its resumption; the other orders keep their place. */
    fun chained(orders: List<PendingDocument>): List<PendingDocument> {
        fun resumes(r: PendingDocument, s: PendingDocument) =
            ORDER.find(r.documentName.trim())?.groupValues?.get(2).equals("Resumption", ignoreCase = true) && baseOf(r.documentName) == baseOf(s.documentName)
        val chains = orders.filter(::isSuspension).associateWith { s -> listOf(s) + continuances(s, orders) + orders.filter { resumes(it, s) } }
        val inChain = chains.values.flatten().toSet()
        return orders.flatMap { chains[it] ?: if (it in inChain) emptyList() else listOf(it) }
    }

    /**
     * Name of the next continuance of [suspension] of [type], e.g. "2nd Partial Continuance of Work Suspension Order No. 1",
     * and the day it starts: the day after the latest End of the suspension and its continuances so far.
     */
    fun nextContinuance(suspension: PendingDocument, documents: List<PendingDocument>, type: String = suspensionType(suspension)): Pair<String, String> {
        val chain = continuances(suspension, documents)
        val lastEnd = (chain + suspension).mapNotNull { suspensionEnd(it.remarks) }.maxOrNull()
        return "${ordinal(chain.size + 1)}${if (type == "Partial") " Partial" else ""} Continuance of ${baseOf(suspension.documentName)}" to (lastEnd?.plusDays(1)?.toString() ?: "")
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

    /** Days from [effectivity] through [end], both counted. */
    fun suspensionDays(effectivity: LocalDate, end: LocalDate): Int = ChronoUnit.DAYS.between(effectivity, end).toInt() + 1

    /** The End a form shows for [days] typed from [effectivity]; blank until both are set. */
    fun endText(effectivity: String, days: String): String {
        val n = days.trim().toIntOrNull()?.takeIf { it > 0 } ?: return ""
        return parse(effectivity)?.let { lastDay(it, n).toString() } ?: ""
    }

    /** The day count for an End picked on a form, or null when it is before [effectivity] or either date is unset. */
    fun daysText(effectivity: String, end: String): String? {
        val from = parse(effectivity) ?: return null
        return parse(end)?.takeIf { !it.isBefore(from) }?.let { suspensionDays(from, it).toString() }
    }

    /** Remarks in the same "Duration | Effectivity | End" form as a suspension, so the schedule and forms read it. */
    fun suspensionRemarks(days: Int, effectivity: String): String {
        val end = parse(effectivity)?.takeIf { days > 0 }?.let { lastDay(it, days) }
        return "Duration: $days Days | Effectivity: $effectivity" + (end?.let { " | End: $it" } ?: "")
    }

    /**
     * [remarks] with its End worked out again from Duration and Effectivity, so orders saved before the
     * effectivity date counted as day 1 (May 26 + 90 days saved as Aug 24) read right. Unchanged without both.
     */
    fun withEnd(remarks: String): String {
        val days = DURATION.find(remarks)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: return remarks
        val effectivity = EFFECTIVITY.find(remarks) ?: return remarks
        val end = "End: ${lastDay(parse(effectivity.groupValues[1]) ?: return remarks, days)}"
        return if (END.containsMatchIn(remarks)) remarks.replace(END, end) else remarks.replaceRange(effectivity.range, "${effectivity.value} | $end")
    }

    /** [remarks] ending on [end] instead: its Duration follows. Unchanged when [end] is before the Effectivity. */
    fun withEndDate(remarks: String, end: String): String {
        val days = daysText(EFFECTIVITY.find(remarks)?.groupValues?.get(1).orEmpty(), end) ?: return remarks
        return withEnd(if (DURATION.containsMatchIn(remarks)) remarks.replace(DURATION, "Duration: $days") else "Duration: $days Days | $remarks")
    }

    /** The last day of a suspension or continuance from its remarks ([withEnd]), or null when it has none. */
    fun suspensionEnd(remarks: String): LocalDate? = END.find(withEnd(remarks))?.groupValues?.get(1)?.let(::parse)

    private val DURATION = Regex("Duration: (\\d+)")
    private val EFFECTIVITY = Regex("Effectivity: (\\d{4}-\\d{2}-\\d{2})")
    private val END = Regex("End: (\\d{4}-\\d{2}-\\d{2})")

    private fun parse(date: String): LocalDate? = try {
        LocalDate.parse(date.trim())
    } catch (_: Exception) {
        null
    }
}
