package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.RecordSyncSchema
import com.example.data.model.Project
import com.example.data.model.SdpLot
import com.example.data.model.SdpLotInspection
import com.example.data.model.SdpLotProgress
import com.example.data.model.SdpPlan
import com.example.data.model.SdpRoad
import com.example.data.model.UserRole
import com.example.data.remote.RecordSync
import com.example.data.repository.ProjectRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Deleting an uploaded SDP plan, and turning one: both must reach the cloud like any other record change.
@RunWith(RobolectricTestRunner::class)
class SdpPlanDeleteTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ProjectRepository
    private val sql get() = db.openHelper.writableDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        RecordSyncSchema.install(sql)
        repository = ProjectRepository(db.projectDao(), db.reportDao())
    }

    @After
    fun tearDown() = db.close()

    private fun count(query: String, vararg args: Any?): Int = sql.query(query, args).use { it.moveToFirst(); it.getInt(0) }

    private val project = Project(
        name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
        contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1,
        dateStarted = "2026-08-01", completionDateOriginal = "2026-12-31", completionDateRevised = "2026-12-31",
        assignedUids = "[\"x\"]"
    )

    /** A plan with one lot (with progress and an inspection) and one road; returns the plan. */
    private fun planWithLot(): SdpPlan = runBlocking {
        val dao = db.projectDao()
        val projectId = dao.insertProject(project)
        val plan = SdpPlan(projectId = projectId, planName = "SDP", pdfFileUrl = "", uploadedDate = "2026-11-09", uploadedBy = "x")
        val planId = dao.insertSdpPlan(plan)
        val lotId = dao.insertSdpLot(SdpLot(projectId = projectId, sdpPlanId = planId, blockNumber = "1", lotNumber = "1", polygonNormalizedJson = "[[0.1,0.1],[0.2,0.1],[0.2,0.2]]"))
        dao.insertOrUpdateLotProgress(SdpLotProgress(projectId = projectId, sdpPlanId = planId, sdpLotId = lotId, physicalProgress = 40))
        dao.insertSdpLotInspection(
            SdpLotInspection(
                projectId = projectId, sdpPlanId = planId, sdpLotId = lotId, inspectionDate = "2026-11-09", inspectedBy = "x",
                physicalProgress = 40, constructionStatus = "", currentActivity = "", contractor = "", remarks = "", createdDate = "2026-11-09"
            )
        )
        dao.insertSdpRoad(SdpRoad(projectId = projectId, sdpPlanId = planId, roadName = "R1", polylineNormalizedJson = "[[0,0],[1,1]]"))
        sql.execSQL("UPDATE sync_outbox SET state = 'SYNCED'")
        plan.copy(id = planId, projectId = projectId)
    }

    @Test
    fun delete_removesPlanAndItsLots_andQueuesTombstonesForEach() = runBlocking {
        val plan = planWithLot()
        repository.deleteSdpPlan(plan, UserRole.ENGINEER_ADMIN)

        for (table in RecordSyncSchema.SDP_TABLES) {
            assertEquals("$table rows left", 0, count("SELECT COUNT(*) FROM `$table`"))
            // Each row's delete is queued with its project, so the cloud copy is tombstoned and other phones and the website drop it.
            assertEquals(
                "$table delete queued", 1,
                count(
                    "SELECT COUNT(*) FROM sync_outbox WHERE entityType = ? AND operation = 'DELETE' AND state = 'PENDING' AND payloadJson LIKE ?",
                    "record:$table", "%${project.projectUuid}%"
                )
            )
        }
        // The project stays.
        assertEquals(1, count("SELECT COUNT(*) FROM projects"))
    }

    @Test
    fun delete_needsTheRoleThatUploadsPlans() {
        val plan = planWithLot()
        assertThrows(SecurityException::class.java) { runBlocking { repository.deleteSdpPlan(plan, UserRole.VIEWER) } }
        assertEquals(1, count("SELECT COUNT(*) FROM sdp_plans"))
    }

    // A turn is saved on the plan and goes up with it; the lots it draws over are not touched.
    @Test
    fun rotation_isSyncedWithThePlan_andLeavesLotsAlone() = runBlocking {
        val plan = planWithLot()
        val lotsBefore = db.projectDao().getAllSdpLots().first()
        repository.setSdpPlanScale(plan.copy(rotation = 90), emptyList(), UserRole.ENGINEER_ADMIN)

        assertEquals(1, count("SELECT COUNT(*) FROM sync_outbox WHERE entityType = 'record:sdp_plans' AND state = 'PENDING'"))
        val doc = RecordSync(db).buildDocument("sdp_plans", plan.uuid, "uid")!!
        assertEquals(90, org.json.JSONObject(doc["data"] as String).getInt("rotation"))
        assertEquals(lotsBefore, db.projectDao().getAllSdpLots().first())
        assertEquals(0, count("SELECT COUNT(*) FROM sync_outbox WHERE entityType = 'record:sdp_lots' AND state = 'PENDING'"))
    }
}
