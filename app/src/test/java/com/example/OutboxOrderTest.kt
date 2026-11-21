package com.example

import com.example.data.remote.outboxOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** Projects go first, then the records' text, and photos last, so a weak signal never holds back the week's figures. */
class OutboxOrderTest {
    @Test
    fun projectsThenRecordsThenFiles_stableWithinEachGroup() {
        val events = listOf("file" to 1, "record:weekly_reports" to 2, "project" to 3, "file" to 4, "record:issues" to 5, "project" to 6)
        assertEquals(listOf(3, 6, 2, 5, 1, 4), events.sortedBy { outboxOrder(it.first) }.map { it.second })
    }
}
