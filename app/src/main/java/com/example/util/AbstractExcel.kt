package com.example.util

import com.example.data.model.lineAverages
import java.io.InputStream
import java.time.LocalDate

/**
 * Reads the Abstract of Physical Accomplishment of the actual work (the "as of" one) from its Excel file (.xlsx).
 * Under the "ITEM NO. / ITEM DESCRIPTION / AMOUNT" header, every row with a description down to the GRAND TOTAL
 * (parts, groups, pay items, subtotals) and the "As of" date; the parts (item no. 1, 2, 3, with the contract amount
 * and the TO DATE "% WT" of their subtotal rows) and the overall % come from those rows.
 * web/apa-excel.js is the website's copy; change both together.
 */
object AbstractExcel {
    data class Part(val name: String, val amount: Double, val actualPct: Double)

    /**
     * A row of the sheet. [kind] is "part" (item no. 1, 2, 3), "group" (a heading such as 2.1 ROADWORKS), "item",
     * "subtotal" or "total". [prev], [period] and [toDate] are quantity, amount, %; [balance] quantity, amount.
     * Null is an empty cell. Kept as the record's rowsJson (see [ProjectAbstracts]).
     */
    data class Row(
        val kind: String, val no: String, val name: String, val qty: Double?, val unit: String, val unitCost: Double?, val amount: Double?,
        val prev: List<Double?>, val period: List<Double?>, val toDate: List<Double?>, val balance: List<Double?>
    )

    /** [billing] is true for the billing format, which is not the actual work. */
    data class Abstract(val asOf: LocalDate?, val parts: List<Part>, val overallPct: Double?, val billing: Boolean, val rows: List<Row>)

    /** Each part with the amount and % to date of its own subtotal: the last one before the next part, so it overwrites its groups'. */
    fun parts(rows: List<Row>): List<Part> {
        val parts = mutableListOf<Part>()
        for (row in rows) {
            if (row.kind == "part") parts += Part(row.name, 0.0, 0.0)
            else if (row.kind == "subtotal" && parts.isNotEmpty())
                parts[parts.lastIndex] = parts.last().copy(amount = row.amount ?: 0.0, actualPct = row.toDate.getOrNull(2) ?: 0.0)
        }
        return parts
    }

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
        val (qtyCol, unitCol, unitCostCol, balanceCol) = listOf("QTY", "UNIT", "UNIT COST", "BALANCE").map { name -> inHeader { it.equals(name, ignoreCase = true) } }
        // The first PREVIOUS, THIS PERIOD and TO DATE under the header are the accomplishment's: QTY, AMOUNT, % WT.
        fun block(name: String) = all.firstOrNull { it.key.first > header.first && it.value.equals(name, ignoreCase = true) }?.key
        val toDate = block("TO DATE") ?: return null
        fun at(row: Int, col: Int?) = col?.let { cells[row to it] }.orEmpty()
        if (!at(toDate.first + 1, toDate.second + 2).startsWith("%")) return null
        val prev = block("PREVIOUS")
        val period = block("THIS PERIOD")
        // A cell's number at [times] decimals' worth: amounts 2, quantities 4, and a % (a fraction on the sheet) as 0 to 100 at 2.
        fun number(row: Int, col: Int?, times: Double, over: Double = times) = at(row, col).toDoubleOrNull()?.let { Math.round(it * times) / over }
        fun money(row: Int, col: Int?) = number(row, col, 100.0)
        fun qty(row: Int, col: Int?) = number(row, col, 10000.0)
        fun figures(row: Int, block: Pair<Int, Int>?) = block?.second.let { c -> listOf(qty(row, c), money(row, c?.plus(1)), number(row, c?.plus(2), 10000.0, 100.0)) }
        val rows = mutableListOf<Row>()
        var billing = false
        for (row in all.map { it.key.first }.filter { it > toDate.first + 1 }.distinct()) {
            val name = at(row, descCol)
            if (name.isEmpty()) continue
            if (name.startsWith("milestone", ignoreCase = true)) billing = true // the billing format's lines
            val no = at(row, header.second)
            val amount = money(row, amountCol)
            val kind = when {
                name.startsWith("grand total", ignoreCase = true) -> "total"
                name.startsWith("subtotal", ignoreCase = true) -> "subtotal"
                no.matches(Regex("\\d+")) -> "part"
                no.isNotEmpty() && amount == null -> "group"
                else -> "item"
            }
            var done = figures(row, toDate)
            // The sheet's own "% WT" (it adds two rounded figures); a subtotal without one, the amount to date over the contract amount.
            if ((kind == "subtotal" || kind == "total") && done[2] == null && amount != null && amount > 0)
                done = done.take(2) + done[1]?.let { Math.round(it / amount * 10000) / 100.0 }
            rows += Row(
                kind, no, name, qty(row, qtyCol), at(row, unitCol), money(row, unitCostCol), amount,
                figures(row, prev), figures(row, period), done, listOf(qty(row, balanceCol), money(row, balanceCol?.plus(1)))
            )
            if (kind == "total") break
        }
        val parts = parts(rows)
        if (parts.isEmpty()) return null
        return Abstract(all.firstNotNullOfOrNull { asOfDate(it.value) }, parts, rows.lastOrNull { it.kind == "total" }?.toDate?.get(2), billing, rows)
    }
}
