package com.example

import com.example.util.BlueBookPrint
import org.junit.Assert.assertEquals
import org.junit.Test

// Emailed reports keep their print name as the file name.
class PdfNameTest {
    @Test fun pdfName_keepsTheReportName() {
        assertEquals("Weekly Report - Oct 24, 2026 - Oct 30, 2026.pdf", BlueBookPrint.pdfName("Weekly Report - Oct 24, 2026 - Oct 30, 2026"))
        assertEquals("SDP Progress - Block 1_2 Phase _A_.pdf", BlueBookPrint.pdfName("SDP Progress - Block 1/2 Phase <A>"))
        assertEquals("NHA Report.pdf", BlueBookPrint.pdfName("  "))
    }
}
