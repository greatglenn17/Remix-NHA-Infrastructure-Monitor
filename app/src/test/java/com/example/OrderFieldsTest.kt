package com.example

import com.example.data.model.PendingDocument
import com.example.ui.dialogs.OrderFields
import com.example.util.ContractorLetters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The order a developer request logs to Documents, and old document letters shown as letters. */
class OrderFieldsTest {
    private val suspension = PendingDocument(
        id = 3, projectId = 1, documentName = "Work Suspension Order No. 1",
        remarks = "Duration: 10 Days | Effectivity: 2026-10-01 | End: 2026-10-11"
    )

    @Test
    fun timeExtension_needsWholeDays_andSavesAsTheDocumentsFormDoes() {
        val f = OrderFields()
        assertFalse(f.valid("Time Extension"))
        f.teNo = "2"; f.teDays = "45"; f.teReason = "Typhoon delays"
        assertTrue(f.valid("Time Extension"))
        assertEquals("Time Extension No. 2 - 45 Days", f.name("Time Extension"))
        assertEquals("Reason: Typhoon delays", f.remarks("Time Extension"))
    }

    @Test
    fun suspension_remarksCarryTheEndDate() {
        val f = OrderFields().apply { wsNo = "1"; wsType = "Partial"; wsDays = "10"; wsEffectivity = "2026-10-01"; wsReason = "Rain" }
        assertEquals("Partial Work Suspension Order No. 1", f.name("Work Suspension Order"))
        assertEquals("Duration: 10 Days | Effectivity: 2026-10-01 | End: 2026-10-10 | Reason: Rain", f.remarks("Work Suspension Order"))
    }

    @Test
    fun continuance_continuesTheLatestSuspension_andNeedsOne() {
        val f = OrderFields().apply { coDays = "5"; coEffectivity = "2026-10-11" }
        assertNull(OrderFields.continued(emptyList()))
        assertFalse(f.valid(OrderFields.CONTINUANCE, emptyList()))
        val docs = listOf(suspension)
        assertTrue(f.valid(OrderFields.CONTINUANCE, docs))
        assertEquals("1st Continuance of Work Suspension Order No. 1", f.name(OrderFields.CONTINUANCE, docs))
        assertEquals("Duration: 5 Days | Effectivity: 2026-10-11 | End: 2026-10-15", f.remarks(OrderFields.CONTINUANCE))
    }

    @Test
    fun oldDocumentLetter_showsAsALetter_keepingItsReceivingCopy() {
        val doc = PendingDocument(
            id = 9, projectId = 1, documentName = "Letter to Contractor No. 4",
            remarks = "Reason: Slow progress | Date Received: 2026-10-30", fileUrl = "nhafile:copy.jpg"
        )
        val letter = ContractorLetters.asLetter(doc)
        assertEquals(0L, letter.id)
        assertEquals(doc.uuid, letter.uuid)
        assertEquals("Letter to Contractor No. 4: Slow progress", letter.subject)
        assertEquals("2026-10-30", letter.dateReceived)
        assertEquals("nhafile:copy.jpg", letter.receivedFileUrl)
        assertEquals(listOf(letter), ContractorLetters.merged(emptyList(), listOf(doc, suspension)))
    }
}
