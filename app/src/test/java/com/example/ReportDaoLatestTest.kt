package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.Project
import com.example.data.model.ProjectLatestReport
import com.example.data.model.WeeklyReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReportDaoLatestTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<android.content.Context>(), AppDatabase::class.java
    ).allowMainThreadQueries().build()

    @After
    fun tearDown() = database.close()

    private fun project(id: Long) = Project(
        id = id, name = "P$id", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "[]",
        contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 0,
        dateStarted = "", completionDateOriginal = "", completionDateRevised = ""
    )

    private fun report(projectId: Long, at: Long) = WeeklyReport(
        projectId = projectId, reportingWeek = "W", daysElapsed = 0, remainingDays = 0,
        targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0, manpowerJson = "[]", equipmentJson = "[]",
        activitiesJson = "[]", issuesJson = "[]", accomplishmentItemsJson = "[]",
        documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]", submittedByStaff = "S",
        createdAtTimestamp = at
    )

    @Test
    fun latestTimes_arePerProjectMaximum() = runBlocking {
        database.projectDao().insertProject(project(1))
        database.projectDao().insertProject(project(2))
        val dao = database.reportDao()
        dao.insertWeeklyReport(report(1, 100))
        dao.insertWeeklyReport(report(1, 300))
        dao.insertWeeklyReport(report(2, 200))

        assertEquals(
            listOf(ProjectLatestReport(1, 300), ProjectLatestReport(2, 200)),
            dao.getLatestWeeklyReportTimes().first().sortedBy { it.projectId }
        )
    }
}
