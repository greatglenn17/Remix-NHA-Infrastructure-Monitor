package com.example

import com.example.util.AbstractExcel
import com.example.util.BarChartExcel
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The workbook of web/apa-excel.test.mjs: a made-up abstract laid out like the district's (three header rows, parts 1
// to 3 with their groups and subtotals, the grand total, then the small target table).
@RunWith(RobolectricTestRunner::class)
class AbstractExcelTest {
    /** Cells of ref to value as a workbook: numbers as they are, text as a formula's worked-out string. */
    private fun xlsx(cells: List<Pair<String, Any>>): ByteArray {
        val sheet = cells.joinToString("", "<worksheet><sheetData>", "</sheetData></worksheet>") { (r, v) ->
            if (v is Number) "<c r=\"$r\"><f>X1</f><v>$v</v></c>" else "<c r=\"$r\" t=\"str\"><v>$v</v></c>"
        }
        val parts = mapOf(
            "xl/workbook.xml" to """<workbook xmlns:r="x"><sheets><sheet name="MONITORING" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheet,
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> parts.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()) } }
        return out.toByteArray()
    }

    private fun read(cells: List<Pair<String, Any>>) = AbstractExcel.read(xlsx(cells).inputStream())

    private val head: List<Pair<String, Any>> = listOf(
        "A7" to "ABSTRACT OF PHYSICAL ACCOMPLSIHMENT", "A8" to "As of March 14, 2025",
        "A20" to "ITEM NO.", "B20" to "ITEM DESCRIPTION", "E20" to "QTY", "F20" to "UNIT", "G20" to "UNIT COST", "H20" to "AMOUNT", "I20" to "ACCOMPLISHMENT",
        "S20" to "BALANCE", "V20" to "EQUIVALENT TO 100 UNITS", "I21" to "PREVIOUS", "L21" to "THIS PERIOD", "O21" to "TO DATE",
        "I22" to "QTY", "J22" to "AMOUNT", "K22" to "% WT", "L22" to "QTY", "M22" to "AMOUNT", "N22" to "% WT", "O22" to "QTY", "P22" to "AMOUNT", "Q22" to "% WT",
        "S22" to "QTY", "T22" to "AMOUNT", "V22" to "PREVIOUS", "W22" to "TO DATE", "X22" to "BALANCE",
    )

    private val actual = head + listOf(
        "A23" to 1, "B23" to "LAND ACQUISITION COST", "H23" to 1000000, "P23" to 500000, "Q23" to 0.5,
        "B24" to "Subtotal (Land Acquisition Cost)", "H24" to 1000000, "P24" to 500000, "Q24" to 0.5,
        "A26" to 2, "B26" to "LAND DEVELOPMENT",
        "A27" to 2.1, "B27" to "GENERAL REQUIREMENTS",
        "A28" to "2.1.1", "B28" to "Mobilization/Demobilization", "H28" to 400000, "P28" to 100000, "Q28" to 0.25,
        "E28" to 1, "F28" to "l.s.", "G28" to 400000, "I28" to 0, "J28" to 0, "K28" to 0, "L28" to 0.25, "M28" to 100000, "N28" to 0.25, "O28" to 0.25,
        "S28" to 0.75, "T28" to 300000,
        "B29" to "Subtotal (General Requrements)", "H29" to 400000, "P29" to 100000,
        "A30" to 2.2, "B30" to "ROADWORKS",
        "A31" to "2.2.1", "B31" to "Road Excavation", "H31" to 1600000, "P31" to 404000, "Q31" to 0.2525,
        "B32" to "Subtotal (Roadworks)", "H32" to 1600000, "P32" to 404000,
        // 504,000 of 2,000,000 is 25.20%; the sheet says 25.21% (two rounded figures added), and that is what is printed.
        "B33" to "Subtotal (Land Development)", "H33" to 2000000, "P33" to 504000, "Q33" to 0.25210000000000005,
        "A35" to 3, "B35" to "HOUSING CONSTRUCTION",
        "A36" to 3.1, "B36" to "STRUCTURAL WORKS",
        "A37" to "3.1.1", "B37" to "Column Footing", "H37" to 7000000, "P37" to 70000, "Q37" to 0.01,
        "B38" to "Subtotal (Structural Works)", "H38" to 7000000, "P38" to 70000,
        "B39" to "Subtotal (Housing Construction)", "H39" to 7000000, "P39" to 70000,
        "B40" to "GRAND TOTAL", "H40" to 10000000, "P40" to 1074000, "Q40" to 0.1073,
        "A43" to "Prepared by:", "M48" to 45730, "O48" to "TARGET", "M50" to "LAND DEVELOPMENT", "O50" to 0.4, "P50" to 0.2521,
    )

    @Test fun readsTheAsOfDateEachPartAndTheGrandTotal() {
        val found = read(actual)!!
        assertEquals(LocalDate.of(2025, 3, 14), found.asOf)
        assertEquals(10.73, found.overallPct!!, 0.0)
        assertEquals(false, found.billing)
        assertEquals(
            listOf(
                AbstractExcel.Part("LAND ACQUISITION COST", 1000000.0, 50.0),
                AbstractExcel.Part("LAND DEVELOPMENT", 2000000.0, 25.21),
                AbstractExcel.Part("HOUSING CONSTRUCTION", 7000000.0, 1.0), // no % WT on its subtotal: 70,000 of 7,000,000
            ),
            found.parts
        )
        // The parts fill the three Actual % boxes; a part the abstract does not have leaves its box alone.
        assertEquals(mapOf("Land Acquisition" to 50.0, "Land Development" to 25.21, "Housing Construction" to 1.0), AbstractExcel.actuals(found.parts))
        assertEquals(mapOf("Land Development" to 25.21), AbstractExcel.actuals(found.parts.subList(1, 2)))
    }

    // The same expectations as web/apa-excel.test.mjs: what an uploaded abstract keeps as its rowsJson.
    @Test fun readsEveryRowDownToTheGrandTotal() {
        val found = read(actual)!!
        val rows = found.rows
        assertEquals(
            listOf("part", "subtotal", "part", "group", "item", "subtotal", "group", "item", "subtotal", "subtotal", "part", "group", "item", "subtotal", "subtotal", "total"),
            rows.map { it.kind }
        )
        assertEquals(
            AbstractExcel.Row(
                "item", "2.1.1", "Mobilization/Demobilization", 1.0, "l.s.", 400000.0, 400000.0,
                prev = listOf(0.0, 0.0, 0.0), period = listOf(0.25, 100000.0, 25.0), toDate = listOf(0.25, 100000.0, 25.0), balance = listOf(0.75, 300000.0)
            ),
            rows[4]
        )
        assertEquals(
            AbstractExcel.Row(
                "item", "2.2.1", "Road Excavation", null, "", null, 1600000.0,
                prev = listOf(null, null, null), period = listOf(null, null, null), toDate = listOf(null, 404000.0, 25.25), balance = listOf(null, null)
            ),
            rows[7]
        )
        assertEquals(listOf("1", "", "2", "2.1"), rows.take(4).map { it.no })
        val landDevelopment = rows.single { it.name == "Subtotal (Land Development)" }
        assertEquals(2000000.0, landDevelopment.amount!!, 0.0)
        assertEquals(listOf(null, 504000.0, 25.21), landDevelopment.toDate) // the sheet's own % WT
        assertEquals(listOf(null, 70000.0, 1.0), rows.single { it.name == "Subtotal (Housing Construction)" }.toDate) // none on the sheet: 70,000 of 7,000,000
        assertEquals(10000000.0, rows.last().amount!!, 0.0)
        assertEquals(listOf(null, 1074000.0, 10.73), rows.last().toDate)
        // The parts and the overall % are what the rows say.
        assertEquals(found.parts, AbstractExcel.parts(rows))
        assertEquals(10.73, found.overallPct!!, 0.0)
        assertNull(read(actual.filterNot { it.first.endsWith("40") })!!.overallPct) // no GRAND TOTAL row
    }

    @Test fun tellsTheBillingFormatApartAndRefusesOtherSheets() {
        val billing = head + listOf(
            "A23" to 1, "B23" to "LAND ACQUISITION COST", "B24" to "Subtotal (Land Acquisition Cost)", "H24" to 1000000, "Q24" to 0.4,
            "A27" to 3, "B27" to "HOUSING CONSTRUCTION", "A28" to 3.1, "B28" to "MILESTONE 1 (80%)", "B30" to "Subtotal (Housing Construction)", "H30" to 7000000, "Q30" to 0,
        )
        val found = read(billing)!!
        assertEquals(true, found.billing)
        assertEquals(listOf(40.0, 0.0), found.parts.map { it.actualPct })
        assertNull(read(listOf("A1" to "ITEM NO.", "B1" to "DESCRIPTION", "C1" to "Amount (Php)"))) // a bar chart
        assertNull(read(head)) // the header alone
        assertThrows(BarChartExcel.NotAnExcelFile::class.java) { AbstractExcel.read("not a zip".byteInputStream()) }
    }

    @Test fun readsTheAsOfLine() {
        assertEquals(LocalDate.of(2026, 10, 30), AbstractExcel.asOfDate("As of October 30, 2026"))
        assertEquals(LocalDate.of(2026, 5, 5), AbstractExcel.asOfDate(" as of  May 5 2026 "))
        assertNull(AbstractExcel.asOfDate("As of last week"))
        assertNull(AbstractExcel.asOfDate("As of September 31, 2026")) // no such day
    }
}
