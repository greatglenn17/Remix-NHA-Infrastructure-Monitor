package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.files.Attachments
import com.example.data.local.AppDatabase
import com.example.data.local.RecordSyncSchema
import com.example.data.model.ContractorLetter
import com.example.data.model.DeveloperRequest
import com.example.data.model.Project
import com.example.data.model.SyncOutboxEvent
import com.example.data.model.WeeklyReport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Files held by a developer request or a letter to the contractor are attachments like any other: the clean-up of
 * unused files keeps them, they follow their project to a new uuid, and the one-time upgrade of old uris leaves them.
 */
@RunWith(RobolectricTestRunner::class)
class AttachmentCleanupTest {
    private lateinit var db: AppDatabase
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val project = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private val sql get() = db.openHelper.writableDatabase
    private fun count(query: String, vararg args: Any?): Int = sql.query(query, args).use { it.moveToFirst(); it.getInt(0) }
    private fun stringOf(query: String, vararg args: Any?): String = sql.query(query, args).use { it.moveToFirst(); it.getString(0) }

    private fun project() = Project(
        name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
        contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
        dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31"
    )

    private fun file(ref: String) = Attachments.fileForName(context, ref.substringAfterLast('/'))!!

    /** An attachment this phone added two days ago: its file, and the outbox event that uploaded it. Returns its reference. */
    private suspend fun oldAttachment(name: String): String {
        val ref = "nhafile:$project/$name"
        file(ref).apply { writeText("x"); setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L) }
        db.syncOutboxDao().enqueue(
            SyncOutboxEvent(entityType = Attachments.ENTITY_TYPE, entityUuid = "$project/$name", operation = "UPSERT", revision = 1, payloadJson = "{}")
        )
        return ref
    }

    @Test
    fun requestAndLetterFiles_areKept_andAnUnusedFileIsRemoved() = runBlocking {
        val projectId = db.projectDao().insertProject(project())
        val request = oldAttachment("0f8fad5b-d9cb-469f-a165-70867728950a.pdf")
        val received = oldAttachment("0f8fad5b-d9cb-469f-a165-70867728950b.jpg")
        val reply = oldAttachment("0f8fad5b-d9cb-469f-a165-70867728950c.pdf")
        val unused = oldAttachment("0f8fad5b-d9cb-469f-a165-70867728950d.jpg")
        db.projectDao().insertDeveloperRequest(DeveloperRequest(projectId = projectId, requestType = "Time Extension", requestFileUrl = request))
        db.projectDao().insertContractorLetter(
            ContractorLetter(projectId = projectId, dateSent = "2026-10-20", subject = "Notice of delay", replyFileUrl = reply, receivedFileUrl = received)
        )

        Attachments.collectGarbage(context, db)

        assertTrue("the developer's request letter", file(request).exists())
        assertTrue("the receiving copy", file(received).exists())
        assertTrue("the reply letter", file(reply).exists())
        assertFalse("a file no record holds", file(unused).exists())
        val cloudDeletes = sql
            .query("SELECT entityUuid FROM sync_outbox WHERE entityType = 'file' AND operation = 'DELETE'")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertEquals("only the unused file's cloud copy is deleted", listOf("$project/0f8fad5b-d9cb-469f-a165-70867728950d.jpg"), cloudDeletes)
    }

    private fun weeklyReport(projectId: Long, photos: List<String>) = WeeklyReport(
        projectId = projectId, reportingWeek = "W", daysElapsed = 0, remainingDays = 0,
        targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0, manpowerJson = "[]", equipmentJson = "[]",
        activitiesJson = "[]", issuesJson = "[]", accomplishmentItemsJson = "[]", documentsIssuedReceivedJson = "[]",
        // As the weekly report form saves it: Android's JSONArray writes "/" as "\/".
        attachedPhotoUrlsJson = org.json.JSONArray(photos).toString(), submittedByStaff = "S"
    )

    @Test
    fun weeklyReportPhoto_inTheListAsThePhoneSavesIt_isKept() = runBlocking {
        val projectId = db.projectDao().insertProject(project())
        val photo = oldAttachment("0f8fad5b-d9cb-469f-a165-70867728950e.jpg")
        val report = weeklyReport(projectId, listOf(photo))
        assertTrue("the saved list escapes the slash", "$project\\/" in report.attachedPhotoUrlsJson)
        db.reportDao().insertWeeklyReport(report)

        Attachments.collectGarbage(context, db)

        assertTrue("a photo its weekly report still lists", file(photo).exists())
        assertEquals("its cloud copy is not deleted", 0, count("SELECT COUNT(*) FROM sync_outbox WHERE entityType = 'file' AND operation = 'DELETE'"))
    }

    @Test
    fun movedProject_takesWeeklyReportPhotosAlong() = runBlocking {
        RecordSyncSchema.install(sql)
        val p = project()
        val projectId = db.projectDao().insertProject(p)
        val old = p.projectUuid
        val new = "9b2e7c41-5d3a-4f6e-8a1b-2c3d4e5f6a7b"
        val name = "0f8fad5b-d9cb-469f-a165-70867728950e.jpg"
        val report = weeklyReport(projectId, listOf("nhafile:$old/$name"))
        db.reportDao().insertWeeklyReport(report)
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")

        db.runInTransaction {
            sql.execSQL("UPDATE projects SET projectUuid = ? WHERE id = ?", arrayOf<Any>(new, projectId))
            Attachments.moveProjectRefs(sql, old, new)
        }

        assertEquals(listOf("nhafile:$new/$name"), Attachments.parseList(stringOf("SELECT attachedPhotoUrlsJson FROM weekly_reports WHERE uuid = ?", report.uuid)))
        assertEquals("the photo gets a copy at the new project's path", 1,
            count("SELECT COUNT(*) FROM sync_outbox WHERE entityType = 'file' AND entityUuid = ? AND state = 'PENDING'", "$new/$name"))
    }

    /** As a project that takes the cloud's uuid, or is merged into its duplicate (Attachments.moveProjectRefs). */
    @Test
    fun movedProject_takesRequestAndLetterFilesAlong() = runBlocking {
        RecordSyncSchema.install(sql)
        val p = project()
        val projectId = db.projectDao().insertProject(p)
        val old = p.projectUuid
        val new = "9b2e7c41-5d3a-4f6e-8a1b-2c3d4e5f6a7b"
        val names = listOf("a.pdf", "b.jpg", "c.pdf").map { "0f8fad5b-d9cb-469f-a165-70867728950$it" }
        val request = DeveloperRequest(projectId = projectId, requestType = "Time Extension", requestFileUrl = "nhafile:$old/${names[0]}")
        val letter = ContractorLetter(
            projectId = projectId, dateSent = "2026-10-20", subject = "Notice of delay",
            receivedFileUrl = "nhafile:$old/${names[1]}", replyFileUrl = "nhafile:$old/${names[2]}"
        )
        db.projectDao().insertDeveloperRequest(request)
        db.projectDao().insertContractorLetter(letter)
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")

        db.runInTransaction {
            sql.execSQL("UPDATE projects SET projectUuid = ? WHERE id = ?", arrayOf<Any>(new, projectId))
            Attachments.moveProjectRefs(sql, old, new)
        }

        assertEquals("nhafile:$new/${names[0]}", stringOf("SELECT requestFileUrl FROM developer_requests WHERE uuid = ?", request.uuid))
        assertEquals("nhafile:$new/${names[1]}", stringOf("SELECT receivedFileUrl FROM contractor_letters WHERE uuid = ?", letter.uuid))
        assertEquals("nhafile:$new/${names[2]}", stringOf("SELECT replyFileUrl FROM contractor_letters WHERE uuid = ?", letter.uuid))
        assertEquals("the other fields are untouched", "Notice of delay", stringOf("SELECT subject FROM contractor_letters WHERE uuid = ?", letter.uuid))
        for ((table, uuid) in listOf("developer_requests" to request.uuid, "contractor_letters" to letter.uuid)) {
            assertTrue("$table is uploaded again with its new references",
                count("SELECT COUNT(*) FROM sync_outbox WHERE entityType = ? AND entityUuid = ? AND operation = 'UPSERT' AND state = 'PENDING'", "record:$table", uuid) >= 1)
        }
        for (name in names) {
            assertEquals("$name gets a copy at the new project's path", 1,
                count("SELECT COUNT(*) FROM sync_outbox WHERE entityType = 'file' AND entityUuid = ? AND state = 'PENDING'", "$new/$name"))
        }
        assertEquals("nothing is deleted", 0, count("SELECT COUNT(*) FROM sync_outbox WHERE operation = 'DELETE'"))
    }

    /** Attachments.migrateLegacy now reads these columns too, on a phone it has not run on yet. */
    @Test
    fun legacyUpgrade_leavesRequestAndLetterFilesAsTheyAre() = runBlocking {
        val projectId = db.projectDao().insertProject(project())
        val request = DeveloperRequest(projectId = projectId, requestType = "Time Extension", requestFileUrl = "nhafile:$project/0f8fad5b-d9cb-469f-a165-70867728950a.pdf")
        val letter = ContractorLetter(
            projectId = projectId, dateSent = "2026-10-20", subject = "Notice of delay",
            receivedFileUrl = "nhafile:$project/0f8fad5b-d9cb-469f-a165-70867728950b.jpg", replyFileUrl = ""
        )
        db.projectDao().insertDeveloperRequest(request)
        db.projectDao().insertContractorLetter(letter)
        context.getSharedPreferences("attachments", Context.MODE_PRIVATE).edit().clear().commit()

        Attachments.migrateLegacy(context, db)

        assertEquals(request.requestFileUrl, stringOf("SELECT requestFileUrl FROM developer_requests WHERE uuid = ?", request.uuid))
        assertEquals(letter.receivedFileUrl, stringOf("SELECT receivedFileUrl FROM contractor_letters WHERE uuid = ?", letter.uuid))
        assertEquals("", stringOf("SELECT replyFileUrl FROM contractor_letters WHERE uuid = ?", letter.uuid))
        assertEquals(1, count("SELECT COUNT(*) FROM developer_requests"))
        assertEquals(1, count("SELECT COUNT(*) FROM contractor_letters"))
    }
}
