package com.example

import com.example.data.files.Attachments
import org.junit.Assert.assertEquals
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
}
