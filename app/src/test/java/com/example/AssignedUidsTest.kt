package com.example

import com.example.data.model.AssignedUids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssignedUidsTest {

    @Test
    fun parse_readsArrayAndToleratesBadInput() {
        assertEquals(emptyList<String>(), AssignedUids.parse(""))
        assertEquals(listOf("a", "b"), AssignedUids.parse("[\"a\",\"b\"]"))
        assertEquals(emptyList<String>(), AssignedUids.parse("garbage"))
    }

    @Test
    fun toJson_writesArrayAndRemovesDuplicates() {
        assertEquals("[\"a\"]", AssignedUids.toJson(listOf("a")))
        assertEquals("[\"a\"]", AssignedUids.toJson(listOf("a", "a")))
        assertEquals("[\"b\",\"a\"]", AssignedUids.toJson(listOf("b", "a", "b")))
    }

    @Test
    fun contains_checksMembership() {
        assertTrue(AssignedUids.contains("[\"a\"]", "a"))
        assertFalse(AssignedUids.contains("", "a"))
        assertFalse(AssignedUids.contains("[\"a\"]", "b"))
    }
}
