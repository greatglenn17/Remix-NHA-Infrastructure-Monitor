package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.barChartPayload
import com.example.data.model.SyncOutboxEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Only a pending bar chart save uploads the chart, so other project saves can't replace another phone's chart. */
@RunWith(RobolectricTestRunner::class)
class BarChartOutboxTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<android.content.Context>(), AppDatabase::class.java
    ).allowMainThreadQueries().build()

    @After
    fun tearDown() = database.close()

    private fun event(uuid: String, payload: String) =
        SyncOutboxEvent(entityType = "project", entityUuid = uuid, operation = "UPDATE", revision = 1, payloadJson = payload)

    @Test
    fun onlyAPendingChartSaveCounts() = runBlocking {
        val dao = database.syncOutboxDao()
        dao.enqueue(event("p1", "{\"projectUuid\":\"p1\"}"))
        assertFalse("an ordinary project save", dao.hasPendingBarChartFor("p1"))

        val chart = event("p1", barChartPayload("p1"))
        dao.enqueue(chart)
        assertTrue(dao.hasPendingBarChartFor("p1"))
        assertFalse("another project", dao.hasPendingBarChartFor("p2"))

        dao.markSynced(chart.eventId)
        assertFalse("once uploaded, the cloud's chart is taken again", dao.hasPendingBarChartFor("p1"))
    }
}
