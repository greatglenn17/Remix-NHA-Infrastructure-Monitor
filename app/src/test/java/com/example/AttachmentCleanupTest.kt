package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.files.Attachments
import com.example.data.local.AppDatabase
import com.example.data.model.ContractorLetter
import com.example.data.model.DeveloperRequest
import com.example.data.model.Project
import com.example.data.model.SyncOutboxEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The clean-up of unused attachment files keeps the ones a developer request or a letter to the contractor holds. */
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
        val projectId = db.projectDao().insertProject(
            Project(
                name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
                contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
                dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31"
            )
        )
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
        val cloudDeletes = db.openHelper.writableDatabase
            .query("SELECT entityUuid FROM sync_outbox WHERE entityType = 'file' AND operation = 'DELETE'")
            .use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertEquals("only the unused file's cloud copy is deleted", listOf("$project/0f8fad5b-d9cb-469f-a165-70867728950d.jpg"), cloudDeletes)
    }
}
