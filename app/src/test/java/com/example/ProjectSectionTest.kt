package com.example

import com.example.ui.detail.SectionChips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSectionTest {

    @Test
    fun progress_housingAddsLots() {
        assertEquals(listOf("Weekly", "Monthly", "Lots", "Photos", "Weather", "Issues"), SectionChips.progress(true))
        assertFalse("Lots" in SectionChips.progress(false))
    }

    @Test
    fun documents_auditOnlyWithPermission() {
        assertEquals(listOf("Checklist", "Schedule orders", "Files", "Audit log"), SectionChips.documents(true))
        assertFalse("Audit log" in SectionChips.documents(false))
        assertTrue("Files" in SectionChips.documents(false))
    }

    @Test
    fun chipOrFirst_unknownChipFallsBackToFirst() {
        assertEquals("Weekly", SectionChips.chipOrFirst(SectionChips.progress(false), "Lots"))
        assertEquals("Weekly", SectionChips.chipOrFirst(SectionChips.progress(false), null))
        assertEquals("Issues", SectionChips.chipOrFirst(SectionChips.progress(false), "Issues"))
    }
}
