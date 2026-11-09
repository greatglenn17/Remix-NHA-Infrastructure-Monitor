package com.example.util

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Block and lot names for found lots, from the text printed on the plan. A lot's number is printed
 * inside it ("12", "L-12", "LOT 12", or "B3 L12" with its block); a block's name ("BLOCK 3",
 * "BLK. 3", "B-3") sits inside or beside its lots. Plans whose text was drawn as lines (a scan, or
 * CAD text exported as geometry) have no text to read; their lots stay unnamed.
 */
object PlanLabels {

    /** A word of the plan's text: its centre and letter height, in page points (y down). */
    data class Word(val text: String, val x: Double, val y: Double, val size: Double)

    /** The words of a page that is [width] x [height] points, as shown (after the page's own rotation). */
    class PageText(val words: List<Word>, val width: Double, val height: Double)

    private const val NUM = """(\d{1,4}[A-Z]?)"""
    private val LOT = Regex("""(?:LOT|L)?[\s.#-]*$NUM""", RegexOption.IGNORE_CASE)
    private val BLOCK = Regex("""(?:BLOCK|BLK|B)[\s.#-]*$NUM""", RegexOption.IGNORE_CASE)
    private val BLOCK_AND_LOT = Regex("""(?:BLOCK|BLK|B)[\s.#-]*$NUM[\s.,/#-]*(?:LOT|L)[\s.#-]*$NUM""", RegexOption.IGNORE_CASE)
    private val BLOCK_WORD = Regex("""BLOCK|BLK\.?""", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex(NUM, RegexOption.IGNORE_CASE)

    /** Page 1's words; empty words when the PDF has no text. */
    fun read(context: android.content.Context, pdf: java.io.File): PageText {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(pdf).use { doc ->
            val page = doc.getPage(0)
            val box = page.cropBox
            val words = mutableListOf<Word>()
            object : com.tom_roush.pdfbox.text.PDFTextStripper() {
                // Called once per word; positions are turned with the page, origin top left.
                override fun writeString(text: String, positions: MutableList<com.tom_roush.pdfbox.text.TextPosition>) {
                    if (positions.isEmpty() || text.isBlank()) return
                    val size = maxOf(positions.maxOf { it.height }, positions.map { it.width }.average().toFloat() / 0.6f).toDouble()
                    words += Word(text, positions.map { it.x + it.width / 2.0 }.average(), positions.map { it.y - it.height / 2.0 }.average(), size)
                }
            }.apply { startPage = 1; endPage = 1 }.getText(doc)
            val turned = page.rotation % 180 != 0
            return PageText(words, (if (turned) box.height else box.width).toDouble(), (if (turned) box.width else box.height).toDouble())
        }
    }

    /**
     * Block to lot for each of [lots] (outlines 0..1 of the page), in order; both blank where the
     * plan does not name the lot, or names two lots the same. [blocks] are the detector's block
     * numbers (lots that touch), so a label beside a block names each of its lots.
     */
    fun name(lots: List<List<Pair<Float, Float>>>, blocks: List<Int>, page: PageText): List<Pair<String, String>> {
        val words = page.words.map { it.copy(text = it.text.trim().uppercase()) }
        val outlines = lots.map { lot -> lot.map { (x, y) -> x * page.width to y * page.height } }
        val centres = outlines.map { o -> (o.minOf { it.first } + o.maxOf { it.first }) / 2 to (o.minOf { it.second } + o.maxOf { it.second }) / 2 }
        fun Word.to(c: Pair<Double, Double>) = hypot(x - c.first, y - c.second)
        val used = BooleanArray(words.size)

        // Block labels: "BLOCK 3" in one word, or "BLOCK" with its number beside or under it.
        val labels = mutableListOf<Word>()
        words.forEachIndexed { i, w ->
            if (!BLOCK_AND_LOT.matches(w.text)) BLOCK.matchEntire(w.text)?.let { labels += w.copy(text = it.groupValues[1]); used[i] = true }
        }
        words.forEachIndexed { i, w ->
            if (!BLOCK_WORD.matches(w.text)) return@forEachIndexed
            val n = words.indices.filter { !used[it] && NUMBER.matches(words[it].text) }.minByOrNull { words[it].to(w.x to w.y) }
                ?.takeIf { words[it].to(w.x to w.y) <= w.size * (w.text.length * 0.5 + 2) } ?: return@forEachIndexed
            used[i] = true; used[n] = true
            labels += Word(words[n].text, (w.x + words[n].x) / 2, (w.y + words[n].y) / 2, w.size)
        }
        // Each label names the found block whose nearest lot is within two lot widths of it.
        val typical = outlines.map { sqrt(abs(area(it))) }.sorted().getOrElse(outlines.size / 2) { 0.0 }
        val labelsOf = labels.groupBy { l ->
            centres.indices.minByOrNull { l.to(centres[it]) }?.takeIf { l.to(centres[it]) < typical * 2 }?.let { blocks[it] }
        }

        val names = outlines.indices.map { i ->
            val inside = words.filterIndexed { k, w -> !used[k] && contains(outlines[i], w.x, w.y) }
            inside.firstNotNullOfOrNull { BLOCK_AND_LOT.matchEntire(it.text) }?.let { return@map it.groupValues[1] to it.groupValues[2] }
            // The lot number is the biggest number in the lot (an undecorated area is smaller), else the one nearest its middle.
            val lot = inside.filter { LOT.matches(it.text) }
                .maxWithOrNull(compareBy<Word> { (it.size * 2).roundToInt() }.thenByDescending { it.to(centres[i]) })
                ?.let { LOT.matchEntire(it.text)!!.groupValues[1] }
            val block = labelsOf[blocks[i]]?.minByOrNull { it.to(centres[i]) }?.text
            if (lot != null && block != null) block to lot else "" to ""
        }
        val twice = names.filter { it.second.isNotEmpty() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return names.map { if (it in twice) "" to "" else it }
    }

    private fun area(o: List<Pair<Double, Double>>) =
        o.indices.sumOf { o[it].first * o[(it + 1) % o.size].second - o[(it + 1) % o.size].first * o[it].second } / 2

    private fun contains(o: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var inside = false
        var j = o.size - 1
        for (i in o.indices) {
            val (xi, yi) = o[i]; val (xj, yj) = o[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
