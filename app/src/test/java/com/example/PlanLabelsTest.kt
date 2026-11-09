package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.util.PlanLabels
import com.example.util.PlanLabels.Word
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlanLabelsTest {

    private val w = 1000.0
    private val h = 800.0

    /** A 60 x 40 point lot with its top left corner at ([x], [y]), as the detector gives it (0..1). */
    private fun lot(x: Double, y: Double) = listOf(x to y, x + 60 to y, x + 60 to y + 40, x to y + 40)
        .map { (px, py) -> (px / w).toFloat() to (py / h).toFloat() }

    /** Two rows of three lots from ([x], [y]). */
    private fun block(x: Double, y: Double) = (0..1).flatMap { r -> (0..2).map { c -> lot(x + c * 60, y + r * 40) } }

    /** [text] in the middle of lot [i] of the block at ([x], [y]). */
    private fun inLot(x: Double, y: Double, i: Int, text: String, size: Double = 6.0) = Word(text, x + (i % 3) * 60 + 30, y + (i / 3) * 40 + 20, size)

    @Test
    fun lotsTakeTheBlockAndLotPrintedOnThePlan() {
        val lots = block(100.0, 100.0) + block(100.0, 300.0) + block(500.0, 100.0) + block(500.0, 300.0)
        val blocks = (1..4).flatMap { b -> List(6) { b } }
        val words = listOf(
            // Block 1: "BLOCK" and "1" in the road beside it, lot numbers with their areas under them.
            Word("BLOCK", 60.0, 135.0, 8.0), Word("1", 60.0, 145.0, 8.0)
        ) + (0..5).flatMap { i -> listOf(inLot(100.0, 100.0, i, "${i + 1}"), inLot(100.0, 100.0, i, "60.00 sq.m.", 4.0).copy(y = 100.0 + (i / 3) * 40 + 30)) } +
            // Block 2: "BLK-2" on the line between its rows; lot labels written several ways; lot 6 has none,
            // lot 5's number is beside a smaller undecorated area.
            listOf(Word("BLK-2", 190.0, 340.0, 8.0)) +
            listOf("L-1", "LOT 2", "3", "l.4").mapIndexed { i, t -> inLot(100.0, 300.0, i, t) } +
            listOf(inLot(100.0, 300.0, 4, "5").copy(y = 345.0 + 10), inLot(100.0, 300.0, 4, "48", 4.0)) +
            // Block 3 names block and lot together in each lot; lots 5 and 6 are both "B3 L5".
            listOf("B3 L1", "B3-L2", "BLK3/LOT3", "B 3 L 4", "B3 L5", "B3 L5").mapIndexed { i, t -> inLot(500.0, 100.0, i, t) } +
            // Block 4 has lot numbers but no block label; "BLOCK 9" is in the title block, far away.
            (0..5).map { inLot(500.0, 300.0, it, "${it + 1}") } + Word("BLOCK 9", 950.0, 760.0, 8.0)

        val names = PlanLabels.name(lots, blocks, PlanLabels.PageText(words, w, h))

        assertEquals((1..6).map { "1" to "$it" }, names.subList(0, 6))
        assertEquals(listOf("2" to "1", "2" to "2", "2" to "3", "2" to "4", "2" to "5", "" to ""), names.subList(6, 12))
        assertEquals(listOf("3" to "1", "3" to "2", "3" to "3", "3" to "4", "" to "", "" to ""), names.subList(12, 18))
        assertEquals(List(6) { "" to "" }, names.subList(18, 24))
    }

    @Test
    fun aPlanWithNoTextLeavesEveryLotUnnamed() {
        assertEquals(List(6) { "" to "" }, PlanLabels.name(block(100.0, 100.0), List(6) { 1 }, PlanLabels.PageText(emptyList(), w, h)))
    }

    @Test
    fun wordsAreReadFromThePdfWithTheOriginTopLeft_andTurnWithThePage() {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext())
        for (rotation in listOf(0, 90)) {
            val file = java.io.File.createTempFile("plan", ".pdf")
            PDDocument().use { doc ->
                val page = PDPage(PDRectangle(w.toFloat(), h.toFloat())).apply { this.rotation = rotation }
                doc.addPage(page)
                PDPageContentStream(doc, page).use { s ->
                    // Placed by where they show, 100 and 300 points from the top. PDF y runs up from the bottom;
                    // on a page turned a quarter clockwise, CAD writes text turned back so it reads across.
                    for ((text, x, y) in listOf(Triple("BLOCK", 100f, 100f), Triple("12", 400f, 300f))) {
                        s.beginText(); s.setFont(PDType1Font.HELVETICA, 10f)
                        if (rotation == 0) s.newLineAtOffset(x, h.toFloat() - y)
                        else s.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.PI / 2, y, x))
                        s.showText(text); s.endText()
                    }
                }
                doc.save(file)
            }
            val page = PlanLabels.read(ApplicationProvider.getApplicationContext(), file)
            file.delete()
            assertEquals(if (rotation == 0) w to h else h to w, page.width to page.height)
            assertEquals("rotation $rotation", listOf("BLOCK", "12"), page.words.map { it.text })
            val twelve = page.words[1]
            assertEquals(406.0, twelve.x, 3.0); assertEquals(297.0, twelve.y, 3.0)
        }
    }
}
