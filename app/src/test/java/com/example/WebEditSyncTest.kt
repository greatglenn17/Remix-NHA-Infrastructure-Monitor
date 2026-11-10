package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.RecordSyncSchema
import com.example.data.model.Project
import com.example.data.model.ProjectIssue
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
 * The website edits an issue the way web/logic.js editedRecord and auditRecord build it: the phone's own cloud
 * document with `data` changed, the revision up by one and a new edit time, plus a new audit_logs record. A phone
 * must take both and must not send them back.
 */
@RunWith(RobolectricTestRunner::class)
class WebEditSyncTest {
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

    private fun string(query: String, vararg args: Any?) = sql.query(query, args).use { it.moveToFirst(); it.getString(0) }
    private fun long(query: String, vararg args: Any?) = sql.query(query, args).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun websiteIssueEdit_andItsAuditEntry_reachThePhone() = runBlocking {
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
            assignedUids = "[\"web\"]"
        )
        val projectId = db.projectDao().insertProject(project)
        val issue = ProjectIssue(
            projectId = projectId, date = "2026-10-28", description = "Late rebars", actionTaken = "Wrote the supplier",
            remarks = "Follow up on Monday", loggedBy = "Engr. Sample", isCritical = true, actionBy = "Contractor/Developer"
        )
        db.projectDao().insertProjectIssue(issue)
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")

        // What the cloud holds after this phone's upload, then the website's edit on top of it.
        val cloud = recordSync.buildDocument("project_issues", issue.uuid, "phone")!!
        val data = JSONObject(cloud["data"] as String)
            .put("status", "Approved").put("actionTaken", "Supplier delivered the rebars").put("isCritical", 0)
        val revision = (cloud["revision"] as Long) + 1
        val edited = cloud + mapOf("revision" to revision, "updatedAt" to 1_794_292_093_231L, "updatedByUid" to "web", "data" to data.toString())
        val audit = mapOf(
            "uuid" to "0f8fad5b-d9cb-469f-a165-70867728950e", "type" to "audit_logs", "projectUuid" to project.projectUuid,
            "assignedUids" to listOf("web"), "revision" to 1L, "updatedAt" to 1_794_292_093_231L, "updatedByUid" to "web", "isDeleted" to false,
            "data" to JSONObject().put("timestamp", 1_794_292_093_231L).put("user", "Engr. Web").put("device", "Website")
                .put("actionType", "Issue Creation").put("oldValue", "Previous issue state")
                .put("newValue", "Status: Approved, Remarks: Follow up on Monday").put("details", "Updated issue from 2026-10-28").toString()
        )

        recordSync.applyRemote(listOf(edited, audit))

        assertEquals("Approved", string("SELECT status FROM project_issues WHERE uuid = ?", issue.uuid))
        assertEquals("Supplier delivered the rebars", string("SELECT actionTaken FROM project_issues WHERE uuid = ?", issue.uuid))
        assertEquals(0L, long("SELECT isCritical FROM project_issues WHERE uuid = ?", issue.uuid))
        assertEquals("fields the website left alone stay", "Follow up on Monday", string("SELECT remarks FROM project_issues WHERE uuid = ?", issue.uuid))
        assertEquals(revision, long("SELECT revision FROM project_issues WHERE uuid = ?", issue.uuid))
        assertEquals("Website", string("SELECT device FROM audit_logs WHERE uuid = ?", audit["uuid"]))
        assertEquals(projectId, long("SELECT projectId FROM audit_logs WHERE uuid = ?", audit["uuid"]))
        assertEquals("nothing goes back up", 0L, long("SELECT COUNT(*) FROM sync_outbox WHERE state = 'PENDING'"))
    }
}
