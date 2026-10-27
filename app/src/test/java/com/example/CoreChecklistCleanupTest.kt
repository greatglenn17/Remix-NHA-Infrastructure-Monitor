package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.SampleData
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoreChecklistCleanupTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<android.content.Context>(), AppDatabase::class.java
    ).allowMainThreadQueries().build()

    @After
    fun tearDown() = database.close()

    @Test
    fun removesOnlyUntouchedCoreItems() = runBlocking {
        val dao = database.projectDao()
        dao.insertProject(
            Project(
                id = 1, name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "[]",
                contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 0,
                dateStarted = "", completionDateOriginal = "", completionDateRevised = ""
            )
        )
        val ntp = "Contractor Notice to Proceed (NTP)"
        dao.insertPendingDocument(PendingDocument(projectId = 1, documentName = ntp, status = "Pending", isCoreChecklist = true))
        dao.insertPendingDocument(PendingDocument(projectId = 1, documentName = "PERT/CPM Schedule Approval", status = "Approved", isCoreChecklist = true))
        dao.insertPendingDocument(PendingDocument(projectId = 1, documentName = "As-Built Plans & Turnover Docs", status = "Pending", remarks = "Due at turnover", isCoreChecklist = true))
        dao.insertPendingDocument(PendingDocument(projectId = 1, documentName = ntp, status = "Pending", isCoreChecklist = false))

        SampleData.removeUntouchedCoreChecklist(database)

        val left = dao.getPendingDocumentsForProject(1).first().map { it.documentName to it.isCoreChecklist }
        assertEquals(
            listOf("PERT/CPM Schedule Approval" to true, "As-Built Plans & Turnover Docs" to true, ntp to false),
            left
        )
    }
}
