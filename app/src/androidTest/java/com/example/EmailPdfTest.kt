package com.example

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.util.BlueBookPrint
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Emailing a report makes the print's A4 pages into a PDF without the print screen.
@RunWith(AndroidJUnit4::class)
class EmailPdfTest {
    @Test
    fun reportBecomesAnA4Pdf_withItsPageBreaks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val html = "<html><body><h1>STATUS OF CONSTRUCTION</h1><div style='break-after: page'>Page one</div><div>Page two</div></body></html>"
        var made: File? = null
        val done = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            BlueBookPrint.pdf(context, "SDP Progress - Test", html) { made = it; done.countDown() }
        }
        assertTrue("PDF not made in time", done.await(60, TimeUnit.SECONDS))
        val file = assertNotNull("PDF failed", made).let { made!! }
        assertEquals("SDP Progress - Test.pdf", file.name)
        assertEquals("%PDF", file.inputStream().use { String(it.readNBytes(4)) })
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            assertEquals(2, pdf.pageCount)
            // A4 is 595 x 842 points.
            pdf.openPage(0).use { page ->
                // Android rounds A4 to whole points.
                assertEquals(595.0, page.width.toDouble(), 1.0)
                assertEquals(842.0, page.height.toDouble(), 1.0)
            }
        }
    }
}
