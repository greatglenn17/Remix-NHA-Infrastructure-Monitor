package com.example

import com.example.util.BarChart
import com.example.util.BarChartExcel
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// A workbook laid out like the contractor's bar chart: header, Month row, item rows with yellow cells, then the total.
@RunWith(RobolectricTestRunner::class)
class BarChartExcelTest {
    private fun xlsx(sheet: String): ByteArray {
        val parts = mapOf(
            "xl/workbook.xml" to """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>
                <sheet name="Notes" sheetId="1" r:id="rId1"/><sheet name="Revised" sheetId="2" r:id="rId2"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Target="worksheets/sheet2.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst><si><t>ITEM NO.</t></si><si><t>DESCRIPTION</t></si><si><t>Amount (Php)</t></si>
                <si><t>Duration (C.D)</t></si><si><t>Month 1</t></si><si><r><t>Mobili</t></r><r><t>zation</t></r></si>
                <si><t>Earthworks</t></si><si><t>TOTAL PROJECT COST</t></si></sst>""",
            // fill 2 yellow, fill 3 white; xf 1 -> yellow, xf 2 -> white
            "xl/styles.xml" to """<styleSheet><fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFFFFFFF"/></patternFill></fill></fills>
                <cellXfs count="3"><xf fillId="0"/><xf fillId="2"/><xf fillId="3"/></cellXfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to """<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>hello</t></is></c></row></sheetData></worksheet>""",
            "xl/worksheets/sheet2.xml" to sheet,
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> parts.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()) } }
        return out.toByteArray()
    }

    // Columns: A item no, B description, C amount, D duration, E.. the day cells (E = Month 1, days 1-5).
    private val sheet = """<worksheet><sheetData>
        <row r="3"><c r="A3" t="s"><v>0</v></c><c r="B3" t="s"><v>1</v></c><c r="C3" t="s"><v>2</v></c><c r="D3" t="s"><v>3</v></c></row>
        <row r="4"><c r="E4" t="s"><v>4</v></c></row>
        <row r="6"><c r="A6"><v>1</v></c><c r="B6" t="s"><v>5</v></c><c r="C6"><v>1000</v></c><c r="D6"><v>10</v></c>
            <c r="E6" s="1"/><c r="F6" s="1"/><c r="G6" s="2"/><c r="M6" s="1"/></row>
        <row r="7"><c r="E7"><v>500</v></c></row>
        <row r="8"><c r="A8"><v>2</v></c><c r="B8" t="s"><v>6</v></c><c r="C8"><v>3000.5</v></c><c r="D8"><v>30</v></c><c r="G8" s="1"/></row>
        <row r="10"><c r="B10" t="s"><v>7</v></c><c r="C10"><v>4000.5</v></c></row>
        <row r="11"><c r="B11"><v>99</v></c><c r="C11"><v>99</v></c><c r="E11" s="1"/></row>
        </sheetData><mergeCells><mergeCell ref="G8:L8"/></mergeCells></worksheet>"""

    @Test fun readsItemsAndBars() {
        val sheets = BarChartExcel.read(xlsx(sheet).inputStream())
        assertEquals(listOf("Revised"), sheets.map { it.name })
        val items = sheets.single().items
        assertEquals(listOf("Mobilization", "Earthworks"), items.map { it.name })
        // E,F = days 1-10; white G skipped; M = Month 2's 3rd cell, days 41-45.
        assertEquals(listOf(BarChart.Bar(1, 10), BarChart.Bar(41, 45)), items[0].bars)
        // G8:L8 merged, coloured on G only: cells 3-8 = days 11-40.
        assertEquals(listOf(BarChart.Bar(11, 40)), items[1].bars)
        assertEquals(4000.5, items.sumOf { it.amount }, 0.0)
        assertEquals(mapOf("Mobilization" to 10, "Earthworks" to 30), sheets.single().durations)
    }

    @Test fun refusesWhatIsNotAWorkbook() {
        assertThrows(BarChartExcel.NotAnExcelFile::class.java) { BarChartExcel.read("not a zip".byteInputStream()) }
    }
}
