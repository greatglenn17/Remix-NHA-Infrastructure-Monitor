package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.RecordSyncSchema
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.remote.RecordSync
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordSyncRemovalTest {

    private lateinit var db: AppDatabase
    private lateinit var recordSync: RecordSync

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        RecordSyncSchema.install(db.openHelper.writableDatabase)
        recordSync = RecordSync(db)
    }

    @After
    fun tearDown() = db.close()

    private fun outboxCount(): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) }

    private fun markAllSynced() = db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")

    @Test
    fun pendingRecordEdit_keepsProject_andRemovalTombstonesNothing() = runBlocking {
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
            assignedUids = "[\"x\"]"
        )
        val id = db.projectDao().insertProject(project)
        db.projectDao().insertPendingDocument(PendingDocument(projectId = id, documentName = "Doc"))

        // The insert trigger queued the record, so the project must stay.
        assertTrue(recordSync.hasPendingUpload(id, project.projectUuid))
        markAllSynced()
        assertFalse(recordSync.hasPendingUpload(id, project.projectUuid))

        val before = outboxCount()
        recordSync.removeProjectLocally(id)

        assertNull(db.projectDao().getProjectByIdOnce(id))
        val docsLeft = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM pending_documents WHERE projectId = ?", arrayOf<Any?>(id))
            .use { it.moveToFirst(); it.getInt(0) }
        assertEquals(0, docsLeft)
        assertEquals("no delete event may be queued for the cascaded records", before, outboxCount())
    }

    @Test
    fun assignedUids_areCopiedOntoRecordDocuments() = runBlocking {
        val project = Project(
            name = "P2", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31"
        )
        val id = db.projectDao().insertProject(project)
        val doc = PendingDocument(projectId = id, documentName = "Doc")
        db.projectDao().insertPendingDocument(doc)

        assertEquals(emptyList<String>(), recordSync.buildDocument("pending_documents", doc.uuid, "u")!!["assignedUids"])
        recordSync.setAssignedUidsLocally(project.projectUuid, "[\"u\"]")
        assertEquals(listOf("u"), recordSync.buildDocument("pending_documents", doc.uuid, "u")!!["assignedUids"])
    }
}
