package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.RecordSyncSchema
import com.example.data.model.Project
import com.example.data.model.ProjectIssue
import com.example.data.remote.RecordSync
import kotlinx.coroutines.flow.first
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

    /** web/logic.js weatherChanges: the weekly report's record with one day's hours changed in `_weather`. */
    @Test
    fun websiteWeatherEdit_replacesThatWeeksWeather() = runBlocking {
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
            assignedUids = "[\"web\"]"
        )
        val projectId = db.projectDao().insertProject(project)
        val report = com.example.data.model.WeeklyReport(
            projectId = projectId, reportingWeek = "October 31, 2026 - November 06, 2026", daysElapsed = 90, remainingDays = 60,
            targetAccomplishmentPct = 40.0, actualAccomplishmentPct = 38.5, manpowerJson = "[]", equipmentJson = "[]",
            activitiesJson = "[{\"day\":\"Monday\",\"description\":\"Rebar works\"}]", issuesJson = "[]", accomplishmentItemsJson = "[]",
            documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = "Engr. Sample"
        )
        val days = listOf("Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
        val fair = List(24) { "FAIR" }.joinToString(",")
        com.example.data.local.WeeklyReportWeatherWriter(db, db.reportDao()).create(report, days.mapIndexed { i, day ->
            com.example.data.model.DailyHourlyWeather(projectId = projectId, date = "2026-${if (i == 0) "10-31" else "11-0$i"}", dayOfWeek = day, hourlyConditionsCsv = fair)
        })
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")

        val cloud = recordSync.buildDocument("weekly_reports", report.uuid, "phone")!!
        val data = JSONObject(cloud["data"] as String)
        val stormyAfternoon = List(24) { if (it in 14..17) "STORMY" else "FAIR" }.joinToString(",")
        data.getJSONArray("_weather").getJSONObject(3).put("hourlyConditionsCsv", stormyAfternoon) // Tuesday, 2026-11-03
        val revision = (cloud["revision"] as Long) + 1
        recordSync.applyRemote(listOf(cloud + mapOf("revision" to revision, "updatedAt" to 1_794_292_093_231L, "updatedByUid" to "web", "data" to data.toString())))

        assertEquals(7L, long("SELECT COUNT(*) FROM daily_hourly_weather WHERE projectId = ?", projectId))
        assertEquals(stormyAfternoon, string("SELECT hourlyConditionsCsv FROM daily_hourly_weather WHERE date = '2026-11-03'"))
        assertEquals("Tuesday", string("SELECT dayOfWeek FROM daily_hourly_weather WHERE date = '2026-11-03'"))
        assertEquals(fair, string("SELECT hourlyConditionsCsv FROM daily_hourly_weather WHERE date = '2026-11-02'"))
        assertEquals("the rest of the report stays", report.activitiesJson, string("SELECT activitiesJson FROM weekly_reports WHERE uuid = ?", report.uuid))
        assertEquals(revision, long("SELECT revision FROM weekly_reports WHERE uuid = ?", report.uuid))
        assertEquals("nothing goes back up", 0L, long("SELECT COUNT(*) FROM sync_outbox WHERE state = 'PENDING'"))
    }

    /** web/logic.js newIssueRecord: a brand-new issue the phone has never seen. */
    @Test
    fun websiteNewIssue_isAddedToTheProject() = runBlocking {
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
            assignedUids = "[\"web\"]"
        )
        val projectId = db.projectDao().insertProject(project)
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")
        val uuid = "6f1c2a9e-3b7d-4c55-9a0e-1d2f3a4b5c6d"
        val issue = mapOf(
            "uuid" to uuid, "type" to "project_issues", "projectUuid" to project.projectUuid, "assignedUids" to listOf("web"),
            "revision" to 1L, "updatedAt" to 1_794_292_093_231L, "updatedByUid" to "web", "isDeleted" to false,
            "data" to JSONObject().put("date", "2026-11-10").put("description", "Flooded access road").put("actionTaken", "Asked for a pump")
                .put("remarks", "").put("loggedBy", "Engr. Web").put("timestamp", 1_794_292_093_231L).put("status", "Pending")
                .put("isCritical", 1).put("targetDate", "").put("actionBy", "Contractor/Developer").toString()
        )

        recordSync.applyRemote(listOf(issue))

        assertEquals(projectId, long("SELECT projectId FROM project_issues WHERE uuid = ?", uuid))
        assertEquals("Flooded access road", string("SELECT description FROM project_issues WHERE uuid = ?", uuid))
        assertEquals("Engr. Web", string("SELECT loggedBy FROM project_issues WHERE uuid = ?", uuid))
        assertEquals(1L, long("SELECT isCritical FROM project_issues WHERE uuid = ?", uuid))
        assertEquals("Contractor/Developer", string("SELECT actionBy FROM project_issues WHERE uuid = ?", uuid))
        assertEquals("nothing goes back up", 0L, long("SELECT COUNT(*) FROM sync_outbox WHERE state = 'PENDING'"))
    }

    /** web/logic.js newPaymentRecord: a new housing billing, with JSON's whole numbers (2400000, not 2400000.0). */
    @Test
    fun websiteNewBilling_isAddedToTheProject() = runBlocking {
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 51_500_000.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
            assignedUids = "[\"web\"]"
        )
        val projectId = db.projectDao().insertProject(project)
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")
        val uuid = "9b2e7c41-5d3a-4f6e-8a1b-2c3d4e5f6a7b"
        val billing = mapOf(
            "uuid" to uuid, "type" to "project_payments", "projectUuid" to project.projectUuid, "assignedUids" to listOf("web"),
            "revision" to 1L, "updatedAt" to 1_794_292_093_231L, "updatedByUid" to "web", "isDeleted" to false,
            "data" to """{"name":"3rd partial billing","dvNo":"DV-2026-11-0007","date":"2026-11-11","periodCovered":"November 1 - 30, 2026",""" +
                """"grossAmount":2400000,"percentage":4.660194174757281,"balanceAmount":35100000,"balancePercentage":68.15533980582524,""" +
                """"fileUrl":"","billingType":"M1 - Housing unit","units":3,"unitPrice":800000,"retentionPct":10}"""
        )

        recordSync.applyRemote(listOf(billing))

        val saved = db.projectDao().getProjectPaymentsForProject(projectId).first().single()
        assertEquals("3rd partial billing", saved.name)
        assertEquals(2_400_000.0, saved.grossAmount, 0.0)
        assertEquals(35_100_000.0, saved.balanceAmount, 0.0)
        assertEquals(3, saved.units)
        assertEquals(800_000.0, saved.unitPrice, 0.0)
        assertEquals(10.0, saved.retentionPct, 0.0)
        assertEquals(2_160_000.0, saved.grossAmount * (1 - saved.retentionPct / 100), 1e-6)
        assertEquals("nothing goes back up", 0L, long("SELECT COUNT(*) FROM sync_outbox WHERE state = 'PENDING'"))
    }

    /** web/logic.js newLetterRecord: a new letter to the contractor, no attachments yet. */
    @Test
    fun websiteNewLetter_isAddedToTheProject() = runBlocking {
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 51_500_000.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
            assignedUids = "[\"web\"]"
        )
        val projectId = db.projectDao().insertProject(project)
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")
        val letter = mapOf(
            "uuid" to "3f1c9a52-7b4e-4d2a-9c6f-1a2b3c4d5e6f", "type" to "contractor_letters", "projectUuid" to project.projectUuid,
            "assignedUids" to listOf("web"), "revision" to 1L, "updatedAt" to 1_794_292_093_231L, "updatedByUid" to "web", "isDeleted" to false,
            "data" to """{"dateSent":"2026-11-12","subject":"Catch-up plan for Block 5","actionTaken":"","dateReplied":"","replyFileUrl":"",""" +
                """"dateReceived":"2026-11-13","receivedFileUrl":""}"""
        )

        recordSync.applyRemote(listOf(letter))

        val saved = db.projectDao().getContractorLettersForProject(projectId).first().single()
        assertEquals("Catch-up plan for Block 5", saved.subject)
        assertEquals("2026-11-12", saved.dateSent)
        assertEquals("2026-11-13", saved.dateReceived)
        assertEquals("", saved.dateReplied)
        assertEquals("3f1c9a52-7b4e-4d2a-9c6f-1a2b3c4d5e6f", saved.uuid)
        assertEquals("nothing goes back up", 0L, long("SELECT COUNT(*) FROM sync_outbox WHERE state = 'PENDING'"))
    }

    /** web/logic.js newRequestRecord and newDocumentRecord: a new developer request and the order it files in Documents. */
    @Test
    fun websiteNewRequest_isAddedWithItsPendingOrder() = runBlocking {
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 51_500_000.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
            assignedUids = "[\"web\"]"
        )
        val projectId = db.projectDao().insertProject(project)
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")
        fun record(uuid: String, type: String, data: String) = mapOf(
            "uuid" to uuid, "type" to type, "projectUuid" to project.projectUuid, "assignedUids" to listOf("web"), "revision" to 1L,
            "updatedAt" to 1_794_292_093_231L, "updatedByUid" to "web", "isDeleted" to false, "data" to data
        )

        recordSync.applyRemote(listOf(
            record(
                "7a2d4c10-5e3b-4f6a-8b1c-2d3e4f5a6b7c", "developer_requests",
                """{"requestType":"Variation Order","details":"Letter 12","dateRequested":"","dateReceived":"2026-11-12","datePackaged":"","requestFileUrl":""}"""
            ),
            record(
                "9c8b7a65-4d3c-4b2a-9f1e-0d9c8b7a6f5e", "pending_documents",
                """{"documentName":"Variation Order No. 2 (Deductive) - PHP 1,500,000.50","status":"Pending",""" +
                    """"remarks":"Reason: Less fill | Date Approved: 2026-11-05","isCoreChecklist":0,"fileUrl":""}"""
            )
        ))

        val request = db.projectDao().getDeveloperRequestsForProject(projectId).first().single()
        assertEquals("Variation Order", request.requestType)
        assertEquals("Letter 12", request.details)
        assertEquals("2026-11-12", request.dateReceived)
        assertEquals("", request.datePackaged)
        val order = db.projectDao().getPendingDocumentsForProject(projectId).first().single { it.uuid == "9c8b7a65-4d3c-4b2a-9f1e-0d9c8b7a6f5e" }
        assertEquals("Variation Order No. 2 (Deductive) - PHP 1,500,000.50", order.documentName)
        assertEquals("Pending", order.status)
        assertEquals("Reason: Less fill | Date Approved: 2026-11-05", order.remarks)
        assertEquals("off the core checklist", false, order.isCoreChecklist)
        assertEquals("nothing goes back up", 0L, long("SELECT COUNT(*) FROM sync_outbox WHERE state = 'PENDING'"))
    }

    /** web/app.js editOrder: an order approved on the website (revision up by one), then deleted there. */
    @Test
    fun websiteOrderApproval_thenDelete_reachThePhone() = runBlocking {
        val project = Project(
            name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
            contractCostOriginal = 51_500_000.0, contractCostRevised = 0.0, contractDurationDays = 1,
            dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
            assignedUids = "[\"web\"]"
        )
        val projectId = db.projectDao().insertProject(project)
        val uuid = "5e4d3c2b-1a0f-4e9d-8c7b-6a5f4e3d2c1b"
        fun order(revision: Long, status: String, remarks: String, deleted: Boolean = false) = mapOf(
            "uuid" to uuid, "type" to "pending_documents", "projectUuid" to project.projectUuid, "assignedUids" to listOf("web"),
            "revision" to revision, "updatedAt" to 1_794_292_093_231L + revision, "updatedByUid" to "web", "isDeleted" to deleted,
            "data" to """{"documentName":"Work Suspension Order No. 3","status":"$status","remarks":"$remarks","isCoreChecklist":0,"fileUrl":""}"""
        )
        recordSync.applyRemote(listOf(order(1, "Pending", "Duration: 10 Days | Effectivity: 2026-11-01 | End: 2026-11-10")))
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")

        recordSync.applyRemote(listOf(order(2, "Approved", "Duration: 12 Days | Effectivity: 2026-11-01 | End: 2026-11-12 | Typhoon")))

        val approved = db.projectDao().getPendingDocumentsForProject(projectId).first().single { it.uuid == uuid }
        assertEquals("Approved", approved.status)
        assertEquals("Duration: 12 Days | Effectivity: 2026-11-01 | End: 2026-11-12 | Typhoon", approved.remarks)
        assertEquals("Work Suspension Order No. 3", approved.documentName)

        recordSync.applyRemote(listOf(order(3, "Approved", "Duration: 12 Days | Effectivity: 2026-11-01 | End: 2026-11-12 | Typhoon", deleted = true)))

        assertEquals("the order is gone", 0, db.projectDao().getPendingDocumentsForProject(projectId).first().count { it.uuid == uuid })
        assertEquals("nothing goes back up", 0L, long("SELECT COUNT(*) FROM sync_outbox WHERE state = 'PENDING'"))
    }
}
