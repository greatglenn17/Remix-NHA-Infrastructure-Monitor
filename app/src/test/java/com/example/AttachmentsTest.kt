package com.example

import com.example.data.files.Attachments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AttachmentsTest {

    private val ref = "nhafile:0f8fad5b-d9cb-469f-a165-70867728950e.jpg"

    @Test
    fun parseList_readsPlainArray() {
        assertEquals(listOf(ref, "content://a/1"), Attachments.parseList("""["$ref","content://a/1"]"""))
    }

    @Test
    fun parseList_readsArrayNestedInsideString() {
        // Older versions wrapped the form's JSON array in another array.
        assertEquals(listOf("content://a/1", "content://a/2"), Attachments.parseList("""["[\"content://a/1\",\"content://a/2\"]"]"""))
    }

    @Test
    fun parseList_readsBareUriAndEmpty() {
        assertEquals(listOf("content://a/1"), Attachments.parseList("content://a/1"))
        assertEquals(emptyList<String>(), Attachments.parseList("[]"))
        assertEquals(emptyList<String>(), Attachments.parseList(""))
    }

    @Test
    fun refsIn_ignoresNamesThatAreNotOurs() {
        assertEquals(listOf(ref), Attachments.refsIn("""["$ref","nhafile:../../secret.jpg"]"""))
    }

    private val projectUuid = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private val fileId = "0f8fad5b-d9cb-469f-a165-70867728950e"
    private val newRef = "nhafile:$projectUuid/$fileId.jpg"
    private val legacyPdf = "nhafile:$fileId.pdf"

    @Test
    fun refsIn_findsProjectAndLegacyReferences() {
        assertEquals(listOf(newRef, legacyPdf), Attachments.refsIn("""["$newRef","$legacyPdf","nhafile:../x.jpg","nhafile:$projectUuid/../x.jpg"]"""))
    }

    @Test
    fun projectOf_isTheProjectForNewRefsAndNullForLegacy() {
        assertEquals(projectUuid, Attachments.projectOf(newRef))
        assertNull(Attachments.projectOf(legacyPdf))
    }

    @Test
    fun storagePathOf_nestsUnderTheProject() {
        assertEquals("attachments/$projectUuid/$fileId.jpg", Attachments.storagePathOf(newRef))
        assertEquals("attachments/$fileId.pdf", Attachments.storagePathOf(legacyPdf))
    }

    @Test
    fun storagePathOf_rejectsUnsafeReferences() {
        assertNull(Attachments.storagePathOf("nhafile:../x.jpg"))
        assertNull(Attachments.storagePathOf("nhafile:../$fileId.jpg"))
        assertNull(Attachments.storagePathOf("nhafile:$projectUuid/../$fileId.jpg"))
        assertNull(Attachments.storagePathOf("nhafile:$projectUuid/$projectUuid/$fileId.jpg"))
        assertNull(Attachments.storagePathOf("content://a/1"))
    }
}
