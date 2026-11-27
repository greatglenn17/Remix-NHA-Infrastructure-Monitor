package com.example.util

import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads the contractor's bar chart with cash flow from its Excel file (.xlsx): under the "ITEM NO. / DESCRIPTION /
 * Amount / Duration" header (or the office's "ITEMS / DESCRIPTION / COST (PHP) / NO. OF DAYS"), each item row with an
 * amount, and its bar as the filled cells from "Month 1" ("1st Month") on, 6 cells of 5 days a month, or as many cells
 * as lie between the "Month 1" and "Month 2" headings (30 for a sheet drawn a day to a cell). Where a bar's
 * stretches hold the month's share of the item, as the office's sheet does, the shares give their days and the other
 * colours on the row (the suspensions) are not bars. The cash flow rows are worked out again from the bars, so they
 * are not read. Change both when the website's reader (web/chart-excel.js) changes.
 */
object BarChartExcel {
    /** One sheet read as a chart; [durations] is the Duration column, to check the bars against. */
    data class Sheet(val name: String, val items: List<BarChart.Item>, val durations: Map<String, Int>)

    class NotAnExcelFile : Exception("Not an Excel workbook (.xlsx)")

    private const val MAX_BYTES = 20L * 1024 * 1024

    /** The workbook's sheets laid out as a bar chart, in workbook order; others are left out. */
    fun read(input: InputStream): List<Sheet> {
        val files = unzip(input)
        val strings = sharedStrings(files["xl/sharedStrings.xml"])
        val filled = filledStyles(files["xl/styles.xml"])
        return sheetXml(files).mapNotNull { (name, xml) -> chart(name, xml, strings, filled)?.takeIf { it.items.isNotEmpty() } }
    }

    /** Every sheet's cells as text by (row, column), in workbook order: what the other readers (AbstractExcel) start from. */
    internal fun sheetTexts(input: InputStream): List<Map<Pair<Int, Int>, String>> {
        val files = unzip(input)
        val strings = sharedStrings(files["xl/sharedStrings.xml"])
        return sheetXml(files).map { (_, xml) -> cells(xml, strings, mutableListOf()).mapValues { it.value.text } }
    }

    /** The workbook's sheets as (name, xml), in workbook order. */
    private fun sheetXml(files: Map<String, ByteArray>): List<Pair<String, ByteArray>> {
        val workbook = files["xl/workbook.xml"] ?: throw NotAnExcelFile()
        val targets = relationships(files["xl/_rels/workbook.xml.rels"])
        return sheets(workbook).mapNotNull { (name, rel) ->
            val path = targets[rel]?.let { if (it.startsWith("/")) it.removePrefix("/") else "xl/$it" } ?: return@mapNotNull null
            files[path]?.let { name to it }
        }
    }

    private class Cell(val text: String, val style: Int)

    /** A sheet's cells by (row, column); a formula's text is its last worked-out value. Merged ranges go to [merges] as row1, col1, row2, col2. */
    private fun cells(xml: ByteArray, strings: List<String>, merges: MutableList<IntArray>): Map<Pair<Int, Int>, Cell> {
        val cells = HashMap<Pair<Int, Int>, Cell>()
        parse(xml) { p ->
            when (p.name) {
                "c" -> {
                    val (row, col) = ref(p.getAttributeValue(null, "r") ?: return@parse)
                    val type = p.getAttributeValue(null, "t")
                    val style = p.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                    val raw = cellValue(p)
                    val text = if (type == "s") raw.toIntOrNull()?.let { strings.getOrNull(it) }.orEmpty() else raw
                    cells[row to col] = Cell(text.trim(), style)
                }
                "mergeCell" -> p.getAttributeValue(null, "ref")?.split(":")?.takeIf { it.size == 2 }?.let { (a, b) ->
                    val (r1, c1) = ref(a); val (r2, c2) = ref(b)
                    merges += intArrayOf(r1, c1, r2, c2)
                }
            }
        }
        return cells
    }

    private fun chart(name: String, xml: ByteArray, strings: List<String>, filled: Set<Int>): Sheet? {
        val merges = mutableListOf<IntArray>()
        val cells = cells(xml, strings, merges)
        fun find(test: (String) -> Boolean) = cells.entries.filter { test(it.value.text) }.minByOrNull { it.key.first * 100000 + it.key.second }?.key
        // The headings vary by project: "ITEM NO.", "ITEMS" or a bare "NO." on DESCRIPTION's row ("ITEM DESCRIPTION" too), "Amount" or "COST (PHP)",
        // "Duration", "NO. OF DAYS" or "DAYS", "Month 1" or "1st Month", and the total's row.
        val described = cells.entries.filter { it.value.text.startsWith("DESCRIPTION", ignoreCase = true) || it.value.text.startsWith("ITEM DESCRIPTION", ignoreCase = true) }.map { it.key.first }.toSet()
        val header = cells.entries.filter { HEADER.matches(it.value.text) && it.key.first in described }
            .minByOrNull { it.key.first * 100000 + it.key.second }?.key ?: return null
        fun column(vararg prefixes: String) = cells.entries
            .filter { it.key.first == header.first && prefixes.any { p -> it.value.text.startsWith(p, ignoreCase = true) } }.minOfOrNull { it.key.second }
        val descCol = column("DESCRIPTION", "ITEM DESCRIPTION") ?: return null
        val amountCol = column("Amount", "Cost") ?: return null
        val durationCol = column("Duration", "No. of Days", "Days")
        val dayCol = find { it.equals("Month 1", ignoreCase = true) || it.equals("1st Month", ignoreCase = true) }?.second ?: return null
        // A month is 6 cells of 5 days as the contractors draw it; a sheet with another count of cells from one month's heading
        // to the next (30 when it is drawn a day to a cell) has that many, where they divide the month's 30 days evenly.
        val gap = find { it.equals("Month 2", ignoreCase = true) || it.equals("2nd Month", ignoreCase = true) }?.second?.minus(dayCol) ?: 0
        val cellDays = BarChart.MONTH_DAYS / (if (gap > 0 && BarChart.MONTH_DAYS % gap == 0) gap else BarChart.CELLS_PER_MONTH)
        val end = cells.entries.filter { it.key.first > header.first && TOTAL.containsMatchIn(it.value.text) }
            .minOfOrNull { it.key.first } ?: Int.MAX_VALUE
        val lastCol = cells.keys.maxOfOrNull { it.second } ?: dayCol

        val items = mutableListOf<BarChart.Item>()
        val durations = mutableMapOf<String, Int>()
        val rows = cells.keys.map { it.first }.filter { it > header.first && it < end }.distinct().sorted()
        for (row in rows) {
            val desc = cells[row to descCol]?.text.orEmpty()
            val amount = cells[row to amountCol]?.text?.toDoubleOrNull() ?: continue
            if (desc.isEmpty() || amount <= 0) continue
            val days = sortedSetOf<Int>()
            for (col in dayCol..lastCol) if (cells[row to col]?.style in filled) days += col - dayCol
            // A bar is often merged cells, its colour on the first one only.
            merges.filter { it[0] == row && it[1] >= dayCol && cells[row to it[1]]?.style in filled }
                .forEach { m -> (m[1]..m[3]).forEach { days += it - dayCol } }
            val said = durationCol?.let { cells[row to it]?.text?.toDoubleOrNull()?.toInt() }
            // A filled cell holding a number starts a stretch of the bar: the cell, or the merged cells it leads.
            val stretches = (dayCol..lastCol).mapNotNull { col ->
                val cell = cells[row to col]?.takeIf { it.style in filled } ?: return@mapNotNull null
                val share = cell.text.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                Triple(col - dayCol, (merges.firstOrNull { it[0] == row && it[1] == col }?.get(3) ?: col) - dayCol, share)
            }
            items += BarChart.Item(desc, Math.round(amount * 100) / 100.0, if (stretches.isEmpty()) runs(days.toList(), cellDays) else shareBars(stretches, said ?: 0, cellDays))
            said?.let { durations[desc] = it }
        }
        return Sheet(name, items, durations)
    }

    private val HEADER = Regex("""items?( no\.?)?|no\.?""", RegexOption.IGNORE_CASE)
    private val TOTAL = Regex("""^(grand )?total\b""", RegexOption.IGNORE_CASE)

    /**
     * Bars from a row's [stretches] (first cell, last cell, share of the item) when the sheet writes each month's share in
     * the bar: a stretch's days are its part of [duration] (the cells only round them: 21 days are drawn as 4 cells), counted
     * from its first cell and moved back where they would run past the month it is drawn in; a stretch drawn to its month's
     * last cell, with the bar going on from the next month's first, ends with the month. Without a duration, its cells' days.
     */
    private fun shareBars(stretches: List<Triple<Int, Int, Double>>, duration: Int, cellDays: Int): List<BarChart.Bar> {
        val sum = stretches.sumOf { it.third }
        val bars = mutableListOf<BarChart.Bar>()
        for ((k, stretch) in stretches.withIndex()) {
            val (first, last, share) = stretch
            val days = if (duration > 0) Math.round(share / sum * duration).toInt() else (last - first + 1) * cellDays
            if (days <= 0) continue
            val monthEnd = (last * cellDays / BarChart.MONTH_DAYS + 1) * BarChart.MONTH_DAYS
            val goesOn = (last + 1) * cellDays == monthEnd && stretches.getOrNull(k + 1)?.first == last + 1
            val end = if (goesOn) monthEnd else minOf(first * cellDays + days, monthEnd)
            val start = maxOf(end - days + 1, 1)
            if (bars.isNotEmpty() && bars.last().endDay + 1 >= start) bars[bars.lastIndex] = bars.last().copy(endDay = end)
            else bars += BarChart.Bar(start, end)
        }
        return bars
    }

    /** Consecutive cells (0 = Month 1's first), each [cellDays] long, as bars in contract days. */
    private fun runs(cells: List<Int>, cellDays: Int): List<BarChart.Bar> {
        val bars = mutableListOf<BarChart.Bar>()
        var start = -1; var prev = -2
        for (c in cells + Int.MIN_VALUE) {
            if (c != prev + 1) {
                if (start >= 0) bars += BarChart.Bar(start * cellDays + 1, (prev + 1) * cellDays)
                start = c
            }
            prev = c
        }
        return bars
    }

    /** "H28" as (row 28, column 8). */
    private fun ref(r: String): Pair<Int, Int> {
        val letters = r.takeWhile { it.isLetter() }
        val col = letters.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) }
        return (r.drop(letters.length).toIntOrNull() ?: 0) to col
    }

    /** The text of a <c>: its <v>, or its inline <is><t>; leaves the parser on </c>. */
    private fun cellValue(p: XmlPullParser): String {
        val out = StringBuilder()
        val depth = p.depth
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.END_TAG && p.depth == depth) break
            if (p.eventType == XmlPullParser.START_TAG && (p.name == "v" || p.name == "t")) out.append(p.nextText())
        }
        return out.toString()
    }

    private fun sheets(xml: ByteArray): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        parse(xml) { p ->
            // The parser reads namespaces, so r:id is the attribute "id" in the relationships namespace.
            if (p.name == "sheet") out += p.getAttributeValue(null, "name").orEmpty() to
                (0 until p.attributeCount).firstOrNull { p.getAttributeName(it) == "id" }?.let(p::getAttributeValue).orEmpty()
        }
        return out
    }

    private fun relationships(xml: ByteArray?): Map<String, String> {
        val out = mutableMapOf<String, String>()
        if (xml != null) parse(xml) { p ->
            if (p.name == "Relationship") out[p.getAttributeValue(null, "Id").orEmpty()] = p.getAttributeValue(null, "Target").orEmpty()
        }
        return out
    }

    private fun sharedStrings(xml: ByteArray?): List<String> {
        val out = mutableListOf<String>()
        if (xml != null) parse(xml) { p ->
            if (p.name == "si") {
                val text = StringBuilder()
                val depth = p.depth
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType == XmlPullParser.END_TAG && p.depth == depth) break
                    // Phonetic runs (<rPh>) are not part of the text.
                    if (p.eventType == XmlPullParser.START_TAG && p.name == "rPh") skip(p)
                    else if (p.eventType == XmlPullParser.START_TAG && p.name == "t") text.append(p.nextText())
                }
                out += text.toString()
            }
        }
        return out
    }

    /** Cell style indexes (cellXfs) with a solid fill other than white. */
    private fun filledStyles(xml: ByteArray?): Set<Int> {
        if (xml == null) return emptySet()
        val coloured = mutableListOf<Boolean>() // per fill
        val xfFills = mutableListOf<Int>()
        var inFills = false; var inCellXfs = false
        var solid = false
        val p = android.util.Xml.newPullParser()
        p.setInput(xml.inputStream(), null)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "fills" -> inFills = true
                    "cellXfs" -> inCellXfs = true
                    "fill" -> if (inFills) { solid = false; coloured += false }
                    "patternFill" -> if (inFills) solid = p.getAttributeValue(null, "patternType") == "solid"
                    "fgColor" -> if (inFills && solid && coloured.isNotEmpty()) {
                        val rgb = p.getAttributeValue(null, "rgb")
                        coloured[coloured.lastIndex] = rgb == null || !rgb.uppercase().endsWith("FFFFFF")
                    }
                    "xf" -> if (inCellXfs) xfFills += p.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "fills" -> inFills = false
                    "cellXfs" -> inCellXfs = false
                }
            }
        }
        return xfFills.indices.filter { coloured.getOrNull(xfFills[it]) == true }.toSet()
    }

    private fun skip(p: XmlPullParser) {
        var depth = 1
        while (depth > 0) when (p.next()) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.END_DOCUMENT -> return
        }
    }

    /** Calls [onStart] at each start tag. */
    private fun parse(xml: ByteArray, onStart: (XmlPullParser) -> Unit) {
        val p = android.util.Xml.newPullParser()
        p.setInput(xml.inputStream(), null)
        while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG) onStart(p)
    }

    /** The workbook's XML parts; a file that is not a zip, or larger than [MAX_BYTES] unpacked, is refused. */
    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val files = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.endsWith(".xml") && !entry.name.endsWith(".rels")) continue
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) throw NotAnExcelFile()
                    out.write(buf, 0, n)
                }
                files[entry.name] = out.toByteArray()
            }
        }
        if (files.isEmpty()) throw NotAnExcelFile()
        return files
    }
}
