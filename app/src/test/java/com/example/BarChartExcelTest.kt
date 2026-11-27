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
    private val strings = """<sst><si><t>ITEM NO.</t></si><si><t>DESCRIPTION</t></si><si><t>Amount (Php)</t></si>
                <si><t>Duration (C.D)</t></si><si><t>Month 1</t></si><si><r><t>Mobili</t></r><r><t>zation</t></r></si>
                <si><t>Earthworks</t></si><si><t>TOTAL PROJECT COST</t></si></sst>"""

    private fun xlsx(sheet: String, strings: String = this.strings): ByteArray {
        val parts = mapOf(
            "xl/workbook.xml" to """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>
                <sheet name="Notes" sheetId="1" r:id="rId1"/><sheet name="Revised" sheetId="2" r:id="rId2"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Target="worksheets/sheet2.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to strings,
            // fill 2 yellow, fill 3 white, fill 4 red; xf 1 -> yellow, xf 2 -> white, xf 3 -> red
            "xl/styles.xml" to """<styleSheet><fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFFFFF00"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFFFFFFF"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFFF0000"/></patternFill></fill></fills>
                <cellXfs count="4"><xf fillId="0"/><xf fillId="2"/><xf fillId="3"/><xf fillId="4"/></cellXfs></styleSheet>""",
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

    @Test fun readsAHeaderOfNoDescriptionCostDays_withAStrayNoAboveIt() {
        val strings = strings.replace("ITEM NO.", "NO.").replace("Amount (Php)", "COST").replace("Duration (C.D)", "DAYS").replace("Month 1", "MONTH 1")
        val stray = """<sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>No.</t></is></c><c r="B1" t="inlineStr"><is><t>12</t></is></c></row>"""
        val read = BarChartExcel.read(xlsx(sheet.replace("<sheetData>", stray), strings).inputStream()).single()
        assertEquals(listOf("Mobilization", "Earthworks"), read.items.map { it.name })
        assertEquals(listOf(BarChart.Bar(1, 10), BarChart.Bar(41, 45)), read.items[0].bars)
        assertEquals(mapOf("Mobilization" to 10, "Earthworks" to 30), read.durations)
    }

    // A sheet drawn a day to a cell: E = Month 1's day 1, and Month 2's heading 30 cells on (AI).
    private val daily = """<worksheet><sheetData>
        <row r="3"><c r="A3" t="inlineStr"><is><t>NO.</t></is></c><c r="B3" t="inlineStr"><is><t>DESCRIPTION</t></is></c><c r="C3" t="inlineStr"><is><t>COST</t></is></c><c r="D3" t="inlineStr"><is><t>DAYS</t></is></c>
            <c r="E3" t="inlineStr"><is><t>MONTH 1</t></is></c><c r="AI3" t="inlineStr"><is><t>MONTH 2</t></is></c></row>
        <row r="4"><c r="E4"><v>1</v></c><c r="F4"><v>2</v></c></row>
        <row r="5"><c r="A5"><v>1</v></c><c r="B5" t="inlineStr"><is><t>Fence</t></is></c><c r="C5"><v>1000</v></c><c r="D5"><v>5</v></c>
            <c r="AG5" s="1"/><c r="AH5" s="1"/><c r="AI5" s="1"/><c r="AJ5" s="1"/><c r="AK5" s="1"/></row>
        <row r="6"><c r="A6"><v>2</v></c><c r="B6" t="inlineStr"><is><t>Gate</t></is></c><c r="C6"><v>500</v></c><c r="D6"><v>3</v></c><c r="F6" s="1"/><c r="H6" s="1"/><c r="I6" s="1"/></row>
        <row r="7"><c r="B7" t="inlineStr"><is><t>GRAND TOTAL</t></is></c><c r="C7"><v>1500</v></c></row>
        </sheetData></worksheet>"""

    @Test fun readsASheetDrawnADayToACell_theDaysExactly() {
        val read = BarChartExcel.read(xlsx(daily).inputStream()).single()
        assertEquals(listOf("Fence", "Gate"), read.items.map { it.name })
        // AG-AK are days 29-33, across the end of month 1; F is day 2, H-I days 4-5.
        assertEquals(listOf(BarChart.Bar(29, 33)), read.items[0].bars)
        assertEquals(listOf(BarChart.Bar(2, 2), BarChart.Bar(4, 5)), read.items[1].bars)
        assertEquals(mapOf("Fence" to 5, "Gate" to 3), read.durations)
    }

    // The office's sheet: B items, C description, F cost, I no. of days, J.. the day cells (J = 1st Month, days 1-5). A bar's
    // stretch holds the month's share of the item in its first cell; the red cells are a suspension.
    private val office = """<worksheet><sheetData>
        <row r="49"><c r="B49" t="s"><v>0</v></c><c r="C49" t="s"><v>1</v></c><c r="F49" t="s"><v>2</v></c><c r="I49" t="s"><v>3</v></c><c r="J49" t="s"><v>4</v></c></row>
        <row r="50"><c r="J50"><v>5</v></c><c r="K50"><v>10</v></c></row>
        <row r="51"><c r="C51" t="s"><v>5</v></c><c r="F51"><v>2000</v></c><c r="I51"><v>45</v></c>
            <c r="N51" s="1"><v>0.10</v></c><c r="O51" s="1"/><c r="P51" s="3"/><c r="Q51" s="3"/>
            <c r="R51" s="1"><v>0.21</v></c><c r="V51" s="1"><v>0.14</v></c><c r="AZ51"><v>0.45</v></c></row>
        <row r="52"><c r="C52" t="s"><v>6</v></c><c r="P52" s="3"/></row>
        <row r="53"><c r="C53" t="s"><v>7</v></c><c r="F53"><v>500</v></c><c r="I53"><v>5</v></c><c r="Y53" s="1"><v>0.05</v></c><c r="AZ53"><v>0.05</v></c></row>
        <row r="54"><c r="C54" t="inlineStr"><is><t>FENCING</t></is></c><c r="F54"><v>300</v></c><c r="I54"><v>12</v></c>
            <c r="AA54" s="1"><v>0.02</v></c><c r="AB54" s="1"><v>0.10</v></c><c r="AC54" s="1"/><c r="AZ54"><v>0.12</v></c></row>
        <row r="55"><c r="B55" t="s"><v>8</v></c><c r="F55"><v>2800</v></c></row>
        <row r="56"><c r="B56" t="s"><v>9</v></c><c r="F56"><v>1</v></c><c r="J56" s="1"><v>0.1</v></c></row>
        </sheetData><mergeCells><mergeCell ref="N51:O51"/><mergeCell ref="R51:U51"/><mergeCell ref="V51:X51"/><mergeCell ref="AB54:AC54"/></mergeCells></worksheet>"""

    @Test fun readsTheOfficeLayout_daysFromTheSharesWrittenInABar() {
        val sheet = BarChartExcel.read(xlsx(office, """<sst><si><t>ITEMS</t></si><si><t>DESCRIPTION</t></si><si><t>COST (PHP)</t></si><si><t>NO. OF DAYS</t></si>
            <si><t>1st Month</t></si><si><t>SITE PREPARATIONS</t></si><si><t>LAND DEVELOPMENT</t></si><si><t>TURNOVER</t></si><si><t>GRAND TOTAL</t></si>
            <si><t>MONTHLY ACCOMPLISHMENT</t></si></sst>""").inputStream()).single()
        // 45 days as 10, 21 and 14: N-O is cells 25-30 of month 1, days 21-30; R-U is drawn as 20 days from day 41, so its 21
        // end with month 2, days 40-60; V-X follows from day 61 for 14 days, one bar with the stretch before it.
        assertEquals(listOf(
            BarChart.Item("SITE PREPARATIONS", 2000.0, listOf(BarChart.Bar(21, 30), BarChart.Bar(40, 74))),
            BarChart.Item("TURNOVER", 500.0, listOf(BarChart.Bar(76, 80))), // the heading above it has no cost; the rows from the total on are not items
            // 12 days as 2 and 10: AA is month 3's last cell and AB-AC goes on from month 4's first, so the 2 days end the month: no gap.
            BarChart.Item("FENCING", 300.0, listOf(BarChart.Bar(89, 100)))
        ), sheet.items)
        assertEquals(mapOf("SITE PREPARATIONS" to 45, "TURNOVER" to 5, "FENCING" to 12), sheet.durations)
    }

    @Test fun refusesWhatIsNotAWorkbook() {
        assertThrows(BarChartExcel.NotAnExcelFile::class.java) { BarChartExcel.read("not a zip".byteInputStream()) }
    }
}
