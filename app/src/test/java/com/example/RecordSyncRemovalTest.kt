package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.files.Attachments
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectDuplicateMerger
import com.example.data.local.RecordSyncSchema
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.SyncOutboxEvent
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
import java.util.UUID

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

    private fun fileId() = UUID.randomUUID().toString()

    @Test
    fun legacyAttachment_hidesTheProjectAndKeepsItsFiles() = runBlocking {
        val p = project("P5")
        val id = db.projectDao().insertProject(p)
        // A 1.4.x reference: its cloud copy is Super Admin only, so the local file can't come back.
        db.projectDao().insertPendingDocument(PendingDocument(projectId = id, documentName = "Old scan", fileUrl = "nhafile:${fileId()}.pdf"))
        markAllSynced()

        assertEquals(Unassigned.HIDDEN, recordSync.unassignLocally(id, p.projectUuid, "[]") { error("no files may be deleted") })

        assertEquals("[]", db.projectDao().getProjectByIdOnce(id)!!.assignedUids)
        assertEquals(1, count("SELECT COUNT(*) FROM pending_documents WHERE projectId = ?", id))
    }

    @Test
    fun projectAttachments_areStillRemovedWithTheProject() = runBlocking {
        val p = project("P6")
        val id = db.projectDao().insertProject(p)
        val name = "${fileId()}.pdf"
        db.projectDao().insertPendingDocument(PendingDocument(projectId = id, documentName = "Scan", fileUrl = "nhafile:${p.projectUuid}/$name"))
        markAllSynced()

        var filesAsked: Set<String>? = null
        assertEquals(Unassigned.REMOVED, recordSync.unassignLocally(id, p.projectUuid, "[]") { filesAsked = it })
        assertEquals(setOf(name), filesAsked)
    }

    @Test
    fun hiddenProject_withTheSameList_isNotRewritten() = runBlocking {
        val p = project("P7", assignedUids = "[\"other\"]")
        val id = db.projectDao().insertProject(p)
        db.projectDao().insertTimeExtension(timeExtension(id))
        markAllSynced()
        sql.execSQL("CREATE TEMP TABLE project_writes (n INTEGER)")
        sql.execSQL("CREATE TEMP TRIGGER count_project_writes AFTER UPDATE ON projects BEGIN INSERT INTO project_writes VALUES (1); END")

        assertEquals(Unassigned.HIDDEN, recordSync.unassignLocally(id, p.projectUuid, "[\"other\"]") { error("no files may be deleted") })
        recordSync.setAssignedUidsLocally(p.projectUuid, "[\"other\"]")
        assertEquals("an identical list is not written again", 0, count("SELECT COUNT(*) FROM project_writes"))

        recordSync.setAssignedUidsLocally(p.projectUuid, "[]")
        assertEquals(1, count("SELECT COUNT(*) FROM project_writes"))
    }

    @Test
    fun movedProject_takesItsAttachmentRefsAndOpenUploadsAlong() = runBlocking {
        val p = project("P8")
        val id = db.projectDao().insertProject(p)
        val old = p.projectUuid
        val new = UUID.randomUUID().toString()
        val pending = "${fileId()}.pdf"
        val uploaded = "${fileId()}.jpg"
        val legacy = "nhafile:${fileId()}.jpg"
        val doc = PendingDocument(projectId = id, documentName = "Scan", fileUrl = "nhafile:$old/$pending")
        db.projectDao().insertPendingDocument(doc)
        sql.execSQL("UPDATE projects SET latestUpdatePhotoUrl = ? WHERE id = ?", arrayOf<Any>("nhafile:$old/$uploaded", id))
        db.syncOutboxDao().enqueue(SyncOutboxEvent(entityType = Attachments.ENTITY_TYPE, entityUuid = "$old/$uploaded", operation = "UPSERT", revision = 1, payloadJson = "{}"))
        db.projectDao().insertPendingDocument(PendingDocument(projectId = id, documentName = "Old", fileUrl = legacy))
        markAllSynced()
        db.syncOutboxDao().enqueue(SyncOutboxEvent(entityType = Attachments.ENTITY_TYPE, entityUuid = "$old/$pending", operation = "UPSERT", revision = 1, payloadJson = "{}"))
        val revisionBefore = count("SELECT revision FROM pending_documents WHERE uuid = ?", doc.uuid)

        db.runInTransaction {
            sql.execSQL("UPDATE projects SET projectUuid = ? WHERE id = ?", arrayOf<Any>(new, id))
            Attachments.moveProjectRefs(sql, old, new)
        }

        assertEquals("nhafile:$new/$pending", stringOf("SELECT fileUrl FROM pending_documents WHERE uuid = ?", doc.uuid))
        assertEquals("nhafile:$new/$uploaded", stringOf("SELECT latestUpdatePhotoUrl FROM projects WHERE id = ?", id))
        assertEquals("legacy references are left alone", 1, count("SELECT COUNT(*) FROM pending_documents WHERE fileUrl = ?", legacy))
        assertEquals("the record is re-uploaded with the new reference", revisionBefore + 1, count("SELECT revision FROM pending_documents WHERE uuid = ?", doc.uuid))
        assertEquals(1, count("SELECT COUNT(*) FROM sync_outbox WHERE entityType = 'record:pending_documents' AND entityUuid = ? AND state = 'PENDING'", doc.uuid))
        assertEquals("open upload re-keyed", 1, count("SELECT COUNT(*) FROM sync_outbox WHERE entityUuid = ? AND state = 'PENDING'", "$new/$pending"))
        assertEquals("already uploaded file queued under the new project", 1, count("SELECT COUNT(*) FROM sync_outbox WHERE entityUuid = ? AND state = 'PENDING'", "$new/$uploaded"))
        assertEquals("no open upload left on the old path", 0, count("SELECT COUNT(*) FROM sync_outbox WHERE entityType = 'file' AND entityUuid LIKE ? AND state != 'SYNCED'", "$old/%"))
    }

    @Test
    fun duplicateMerge_pointsMovedRecordsAtTheKeptProject() = runBlocking {
        val keep = project("Same")
        val keepId = db.projectDao().insertProject(keep)
        val duplicate = project("Same")
        val duplicateId = db.projectDao().insertProject(duplicate)
        val name = "${fileId()}.pdf"
        val doc = PendingDocument(projectId = duplicateId, documentName = "Scan", fileUrl = "nhafile:${duplicate.projectUuid}/$name")
        db.projectDao().insertPendingDocument(doc)
        db.syncOutboxDao().enqueue(SyncOutboxEvent(entityType = Attachments.ENTITY_TYPE, entityUuid = "${duplicate.projectUuid}/$name", operation = "UPSERT", revision = 1, payloadJson = "{}"))

        assertEquals(1, ProjectDuplicateMerger.merge(db))

        assertEquals(keepId.toInt(), count("SELECT projectId FROM pending_documents WHERE uuid = ?", doc.uuid))
        assertEquals("nhafile:${keep.projectUuid}/$name", stringOf("SELECT fileUrl FROM pending_documents WHERE uuid = ?", doc.uuid))
        assertEquals(1, count("SELECT COUNT(*) FROM sync_outbox WHERE entityUuid = ? AND state = 'PENDING'", "${keep.projectUuid}/$name"))
    }

    private fun stringOf(query: String, vararg args: Any?): String =
        sql.query(query, args).use { it.moveToFirst(); it.getString(0) }
}
