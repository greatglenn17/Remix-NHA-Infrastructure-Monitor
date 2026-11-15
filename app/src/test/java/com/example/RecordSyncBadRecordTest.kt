package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.RecordSyncSchema
import com.example.data.model.Project
import com.example.data.remote.RecordSync
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Any writer assigned to a project can put a record in the cloud that a phone cannot store. Such a record
 * must only lose itself: the records after it, in the same pass and in later ones, are still applied.
 */
@RunWith(RobolectricTestRunner::class)
class RecordSyncBadRecordTest {
    private lateinit var db: AppDatabase
    private lateinit var recordSync: RecordSync
    private val sql get() = db.openHelper.writableDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        RecordSyncSchema.install(sql)
        recordSync = RecordSync(db)
    }

    @After
    fun tearDown() = db.close()

    private fun long(query: String, vararg args: Any?) = sql.query(query, args).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun recordThePhoneCannotApply_doesNotHoldBackTheOthers() = runBlocking {
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
            assignedUids = "[\"web\"]"
        )
        db.projectDao().insertProject(project)
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")

        fun record(uuid: String, type: String, data: String) = mapOf(
            "uuid" to uuid, "type" to type, "projectUuid" to project.projectUuid, "assignedUids" to listOf("web"),
            "revision" to 1L, "updatedAt" to 1_794_292_093_231L, "updatedByUid" to "web", "isDeleted" to false, "data" to data
        )
        val issue = JSONObject().put("date", "2026-11-10").put("description", "Flooded access road").put("actionTaken", "Asked for a pump")
            .put("remarks", "").put("loggedBy", "Engr. Web").put("timestamp", 1_794_292_093_231L).put("status", "Pending")
            .put("isCritical", 1).put("targetDate", "").put("actionBy", "Contractor/Developer")
        val bad = listOf(
            record("bad-json", "project_issues", "not json"),
            record("bad-null", "project_issues", JSONObject(issue.toString()).put("description", JSONObject.NULL).toString()),
            record("bad-missing", "pending_documents", "{\"documentName\":\"Memo\"}")
        )

        // Records of one type are applied in the order they came, so the good one is behind two bad ones.
        recordSync.applyRemote(bad + record("good", "project_issues", issue.toString()))

        assertEquals("the good record is applied", 1L, long("SELECT COUNT(*) FROM project_issues WHERE uuid = 'good'"))
        assertEquals("nothing of the bad ones is stored", 1L, long("SELECT (SELECT COUNT(*) FROM project_issues) + (SELECT COUNT(*) FROM pending_documents)"))

        // A later pass (what a new project snapshot runs before it sends this phone's own changes) is not stopped either.
        recordSync.retryDeferred()
        recordSync.applyRemote(listOf(record("later", "project_issues", issue.toString())))
        assertEquals(1L, long("SELECT COUNT(*) FROM project_issues WHERE uuid = 'later'"))

        // Once the cloud copy is put right, it is taken like any other record.
        recordSync.applyRemote(listOf(record("bad-json", "project_issues", issue.toString()) + ("revision" to 2L)))
        assertEquals(2L, long("SELECT revision FROM project_issues WHERE uuid = 'bad-json'"))

        assertEquals("a skipped record is neither sent back nor deleted in the cloud", 0L, long("SELECT COUNT(*) FROM sync_outbox WHERE state != 'SYNCED'"))
        assertEquals("local edits are uploaded again afterwards", 0L, long("SELECT applyingRemote FROM sync_state"))
    }
}
