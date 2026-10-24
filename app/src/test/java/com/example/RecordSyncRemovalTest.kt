package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.RecordSyncSchema
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.TimeExtension
import com.example.data.remote.RecordSync
import com.example.data.remote.RecordSync.Unassigned
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private val sql get() = db.openHelper.writableDatabase

    private fun count(query: String, vararg args: Any?): Int =
        sql.query(query, args).use { it.moveToFirst(); it.getInt(0) }

    private fun outboxCount() = count("SELECT COUNT(*) FROM sync_outbox")

    private fun markAllSynced() = sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")

    private fun project(name: String, assignedUids: String = "[\"x\"]") = Project(
        name = name, location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
        contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
        dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
        assignedUids = assignedUids
    )

    private fun timeExtension(projectId: Long) = TimeExtension(
        projectId = projectId, extensionNo = 1, noOfDays = 10, revisedDurationDays = 11,
        periodConsidered = "June", reason = "Rain", revisedCompletionDate = "2027-01-10", remarks = ""
    )

    @Test
    fun localOnlyChildTables_comeFromForeignKeys() {
        val tables = recordSync.localOnlyChildTables()
        assertEquals("projectId", tables["time_extensions"])
        assertTrue("audit_logs" in tables)
        assertTrue(tables.keys.any { it.startsWith("sdp_") })
        for (synced in RecordSyncSchema.TABLES + "daily_hourly_weather") assertFalse(synced in tables)
    }

    @Test
    fun pendingRecordEdit_keepsProject_andRemovalTombstonesNothing() = runBlocking {
        val p = project("P")
        val id = db.projectDao().insertProject(p)
        db.projectDao().insertPendingDocument(PendingDocument(projectId = id, documentName = "Doc"))

        // The insert trigger queued the record, so the project must stay.
        assertTrue(recordSync.hasPendingUpload(id, p.projectUuid))
        markAllSynced()
        assertFalse(recordSync.hasPendingUpload(id, p.projectUuid))

        val before = outboxCount()
        var filesAsked: Set<String>? = null
        assertEquals(Unassigned.REMOVED, recordSync.unassignLocally(id, p.projectUuid, "[]") { filesAsked = it })

        assertNull(db.projectDao().getProjectByIdOnce(id))
        assertEquals(0, count("SELECT COUNT(*) FROM pending_documents WHERE projectId = ?", id))
        assertEquals("no delete event may be queued for the cascaded records", before, outboxCount())
        assertNotNull("files are handed over before the delete", filesAsked)
    }

    @Test
    fun localOnlyRows_hideTheProjectInsteadOfDeletingIt() = runBlocking {
        val p = project("P3")
        val id = db.projectDao().insertProject(p)
        db.projectDao().insertTimeExtension(timeExtension(id))
        markAllSynced()
        val before = outboxCount()

        assertEquals(Unassigned.HIDDEN, recordSync.unassignLocally(id, p.projectUuid, "[]") { error("no files may be deleted") })

        assertEquals("[]", db.projectDao().getProjectByIdOnce(id)!!.assignedUids)
        assertEquals(1, count("SELECT COUNT(*) FROM time_extensions WHERE projectId = ?", id))
        assertEquals(before, outboxCount())
    }

    @Test
    fun workQueuedBeforeTheTransaction_abortsTheRemoval() = runBlocking {
        val p = project("P4")
        val id = db.projectDao().insertProject(p)
        markAllSynced()
        // A record edit lands after the caller's own check but before unassignLocally runs.
        db.projectDao().insertPendingDocument(PendingDocument(projectId = id, documentName = "Late edit"))

        assertEquals(Unassigned.KEPT, recordSync.unassignLocally(id, p.projectUuid, "[]") { error("no files may be deleted") })

        val kept = db.projectDao().getProjectByIdOnce(id)!!
        assertEquals("[\"x\"]", kept.assignedUids)
        assertEquals(1, count("SELECT COUNT(*) FROM pending_documents WHERE projectId = ?", id))
    }

    @Test
    fun recordDocuments_carryTheProjectList_onlyOnceItIsKnown() = runBlocking {
        val p = project("P2", assignedUids = "")
        val id = db.projectDao().insertProject(p)
        val doc = PendingDocument(projectId = id, documentName = "Doc")
        db.projectDao().insertPendingDocument(doc)

        assertFalse("unknown list: the upload waits", "assignedUids" in recordSync.buildDocument("pending_documents", doc.uuid, "u")!!)
        recordSync.setAssignedUidsLocally(p.projectUuid, "[]")
        assertEquals(emptyList<String>(), recordSync.buildDocument("pending_documents", doc.uuid, "u")!!["assignedUids"])
        recordSync.setAssignedUidsLocally(p.projectUuid, "[\"u\"]")
        assertEquals(listOf("u"), recordSync.buildDocument("pending_documents", doc.uuid, "u")!!["assignedUids"])
    }
}
