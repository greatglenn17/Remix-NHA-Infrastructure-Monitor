package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.Project
import com.example.data.model.SdpPlan
import com.example.data.model.SdpSites
import com.example.data.repository.ProjectRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// One contract, two sites: each keeps its own plan in use and its own revisions.
@RunWith(RobolectricTestRunner::class)
class SdpSitesTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<android.content.Context>(), AppDatabase::class.java
    ).allowMainThreadQueries().build()
    private val dao = database.projectDao()

    @After
    fun tearDown() = database.close()

    private fun plan(id: Long, site: String, version: Int, active: Boolean) =
        SdpPlan(id = id, projectId = 1, planName = "P$id", pdfFileUrl = "", version = version, isActive = active, uploadedDate = "", uploadedBy = "", siteName = site)

    @Test
    fun eachSiteShowsItsActiveRevisionInTheOrderSitesWereAdded() {
        val plans = listOf(plan(3, "Phase 2", 1, true), plan(1, "Phase 1", 1, false), plan(2, "Phase 1", 2, true))
        assertEquals(listOf(2L, 3L), SdpSites.current(plans).map { it.id })
    }

    @Test
    fun aSiteWithNoActivePlanFallsBackToItsLatestVersion() {
        // An older app switches off every plan of the project when it uploads or picks a version.
        val plans = listOf(plan(1, "Phase 1", 1, false), plan(2, "Phase 1", 2, false), plan(3, "Phase 2", 1, true))
        assertEquals(listOf(2L, 3L), SdpSites.current(plans).map { it.id })
    }

    @Test
    fun revisionsAndVersionPicksStayInsideTheirSite() = runBlocking {
        dao.insertProject(
            Project(
                id = 1, name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "[]",
                contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 0,
                dateStarted = "", completionDateOriginal = "", completionDateRevised = ""
            )
        )
        dao.insertSdpPlan(plan(1, "Phase 1", 1, true))
        dao.insertSdpPlan(plan(2, "Phase 2", 1, true))
        // A Phase 1 revision switches off only Phase 1's old plan.
        dao.deactivateSdpPlansForSite(1, "Phase 1")
        dao.insertSdpPlan(plan(3, "Phase 1", 2, true))
        assertEquals(mapOf(1L to false, 2L to true, 3L to true), dao.getSdpPlansForProject(1).first().associate { it.id to it.isActive })

        dao.setActiveSdpPlan(1, 1)
        assertEquals(mapOf(1L to true, 2L to true, 3L to false), dao.getSdpPlansForProject(1).first().associate { it.id to it.isActive })

        dao.renameSdpSite(1, "Phase 2", "North")
        assertEquals(listOf("Phase 1", "North"), SdpSites.current(dao.getSdpPlansForProject(1).first()).map { it.siteName })
    }

    @Test
    fun aSecondSiteUploadedAsARevisionCanBecomeItsOwnSite() = runBlocking {
        dao.insertProject(
            Project(
                id = 1, name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "[]",
                contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 0,
                dateStarted = "", completionDateOriginal = "", completionDateRevised = ""
            )
        )
        // Before sites existed: Phase 2 went in as v2 of the unnamed site and hid v1.
        dao.insertSdpPlan(plan(1, "", 1, false))
        dao.insertSdpPlan(plan(2, "", 2, true))
        val v2 = dao.getSdpPlansForProject(1).first().single { it.id == 2L }
        ProjectRepository(dao, database.reportDao()).moveSdpPlanToNewSite(v2, "Phase 2", "Phase 1")

        val plans = dao.getSdpPlansForProject(1).first().associateBy { it.id }
        assertEquals("Phase 1" to 1, plans.getValue(1).siteName to plans.getValue(1).version)
        assertEquals("Phase 2" to 1, plans.getValue(2).siteName to plans.getValue(2).version)
        assertEquals(listOf(1L, 2L), SdpSites.current(plans.values.toList()).map { it.id })
    }
}
