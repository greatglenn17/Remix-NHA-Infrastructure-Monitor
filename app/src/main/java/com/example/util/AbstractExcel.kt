package com.example.util

import com.example.data.model.lineAverages
import java.io.InputStream
import java.time.LocalDate

/**
 * Reads the Abstract of Physical Accomplishment of the actual work (the "as of" one) from its Excel file (.xlsx).
 * Under the "ITEM NO. / ITEM DESCRIPTION / AMOUNT" header, each part (item no. 1, 2, 3) with the contract amount and
 * the TO DATE "% WT" of its subtotal row, the GRAND TOTAL's, and the "As of" date. The pay items stay in Excel.
 * web/apa-excel.js is the website's copy; change both together.
 */
object AbstractExcel {
    data class Part(val name: String, val amount: Double, val actualPct: Double)

    /** [billing] is true for the billing format, which is not the actual work. */
    data class Abstract(val asOf: LocalDate?, val parts: List<Part>, val overallPct: Double?, val billing: Boolean)

    /**
     * The sheet laid out as an abstract, or null without one; a file that is not a workbook throws
     * [BarChartExcel.NotAnExcelFile]. A workbook can hold both formats: the actual one wins wherever its sheet is.
     */
    fun read(input: InputStream): Abstract? =
        BarChartExcel.sheetTexts(input).mapNotNull(::abstract).let { all -> all.firstOrNull { !it.billing } ?: all.firstOrNull() }

    private val AS_OF = Regex("^as of\\s+([a-z]+)\\s+(\\d{1,2}),?\\s+(\\d{4})$", RegexOption.IGNORE_CASE)
    private val MONTHS = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    /** "As of October 30, 2026" as that day; null for anything else. */
    fun asOfDate(text: String): LocalDate? {
        val (month, day, year) = AS_OF.find(text.trim())?.destructured ?: return null
        return runCatching { LocalDate.of(year.toInt(), MONTHS.indexOf(month.lowercase()) + 1, day.toInt()) }.getOrNull()
    }

    /**
     * The parts as the three lines' actual % (parts of one line averaged by their contract amounts); a line the
     * abstract has no part for is left out, so its box stays as it is.
     */
    fun actuals(parts: List<Part>): Map<String, Double> = lineAverages(parts.filter { it.amount > 0 }, Part::name, Part::amount, Part::actualPct)

    /** One sheet's cells, (row, column) to text, as an abstract; null when it is not laid out as one. */
    private fun abstract(cells: Map<Pair<Int, Int>, String>): Abstract? {
        val all = cells.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))
        val header = all.firstOrNull { it.value.equals("ITEM NO.", ignoreCase = true) }?.key ?: return null
        fun inHeader(test: (String) -> Boolean) = all.firstOrNull { it.key.first == header.first && test(it.value) }?.key?.second
        val descCol = inHeader { it.contains("description", ignoreCase = true) } ?: return null
        val amountCol = inHeader { it.equals("AMOUNT", ignoreCase = true) } ?: return null
        // The first TO DATE under the header is the accomplishment's: QTY, AMOUNT, % WT.
        val toDate = all.firstOrNull { it.key.first > header.first && it.value.equals("TO DATE", ignoreCase = true) }?.key ?: return null
        fun at(row: Int, col: Int) = cells[row to col].orEmpty()
        if (!at(toDate.first + 1, toDate.second + 2).startsWith("%")) return null
        // The sheet's own "% WT" (it adds two rounded figures); without one, the amount to date over the contract amount.
        fun pct(row: Int): Double? {
            val total = at(row, amountCol).toDoubleOrNull()
            val done = at(row, toDate.second + 1).toDoubleOrNull()
            val v = at(row, toDate.second + 2).toDoubleOrNull() ?: (if (total != null && total > 0 && done != null) done / total else return null)
            return Math.round(v * 10000) / 100.0
        }
        val parts = mutableListOf<Part>()
        var overall: Double? = null
        var billing = false
        for (row in all.map { it.key.first }.filter { it > toDate.first + 1 }.distinct()) {
            val name = at(row, descCol)
            if (name.startsWith("grand total", ignoreCase = true)) { overall = pct(row); break }
            if (name.startsWith("milestone", ignoreCase = true)) billing = true // the billing format's lines
            if (at(row, header.second).matches(Regex("\\d+")) && name.isNotEmpty()) parts += Part(name, 0.0, 0.0)
            // A part's own subtotal is the last one before the next part, so it overwrites its groups'.
            else if (name.startsWith("subtotal", ignoreCase = true) && parts.isNotEmpty())
                parts[parts.lastIndex] = parts.last().copy(amount = at(row, amountCol).toDoubleOrNull() ?: 0.0, actualPct = pct(row) ?: 0.0)
        }
        if (parts.isEmpty()) return null
        return Abstract(all.firstNotNullOfOrNull { asOfDate(it.value) }, parts, overall, billing)
    }
}
