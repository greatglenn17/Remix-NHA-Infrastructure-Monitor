package com.example.util

/**
 * The day of a weekly activity: one day ("Monday") or a range ("Monday - Wednesday").
 * A range may wrap past Sunday, e.g. "Saturday - Friday" is the whole week.
 */
object ActivityDays {
    val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun label(from: String, to: String?): String = if (to == null || to == from) from else "$from - $to"

    /** (from, to); to is null for a single day. */
    fun split(label: String): Pair<String, String?> {
        val parts = label.split("-").map { it.trim() }
        return if (parts.size == 2) parts[0] to parts[1] else label.trim() to null
    }

    private fun index(day: String) = DAYS.indexOfFirst { it.equals(day, ignoreCase = true) }

    /** Index in Monday..Sunday of the first day; unknown labels sort last. */
    fun start(label: String): Int = index(split(label).first).takeIf { it >= 0 } ?: DAYS.size

    fun covered(label: String): Set<Int> {
        val (from, to) = split(label)
        val a = index(from).takeIf { it >= 0 } ?: return emptySet()
        val b = to?.let(::index)?.takeIf { it >= 0 } ?: return setOf(a)
        val length = (b - a + DAYS.size) % DAYS.size
        return (0..length).map { (a + it) % DAYS.size }.toSet()
    }
}
