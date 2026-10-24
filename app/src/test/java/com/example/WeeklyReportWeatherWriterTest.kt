package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.WeeklyReportWeatherWriter
import com.example.data.model.DailyHourlyWeather
import com.example.data.model.Project
import com.example.data.model.WeeklyReport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Regression coverage for the v1.2.2 orphan-weather data-loss defect. */
@RunWith(RobolectricTestRunner::class)
class WeeklyReportWeatherWriterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: AppDatabase
    private var projectId = 0L

    @Before
    fun setUp() = runBlocking {
        database = openDatabase()
        projectId = database.projectDao().insertProject(testProject())
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun create_usesRoomGeneratedReportId_andKeepsWeatherWhenOrphanCleanupIsRun() = runBlocking {
        val writer = WeeklyReportWeatherWriter(database, database.reportDao())
        val reportId = writer.create(testReport(), listOf(testWeather()))

        val beforeRestart = database.reportDao().getDailyWeatherForProject(projectId).first()
        assertEquals(listOf(reportId), beforeRestart.mapNotNull { it.weeklyReportId })

        // A valid report/weather pair must not be deleted by any maintenance query.
        database.reportDao().deleteWeatherLogsWithoutWeeklyReport()
        val weather = database.reportDao().getDailyWeatherForProject(projectId).first()
        assertEquals(listOf(reportId), weather.mapNotNull { it.weeklyReportId })
    }

    @Test
    fun failedParentInsert_rollsBackWithoutWeatherRows() = runBlocking {
        val writer = WeeklyReportWeatherWriter(database, database.reportDao())
        val invalidReport = testReport().copy(projectId = projectId + 10_000)

        runCatching { writer.create(invalidReport, listOf(testWeather())) }
            .onSuccess { throw AssertionError("Expected the foreign-key insert to fail") }

        assertTrue(database.reportDao().getWeeklyReportsForProject(projectId).first().isEmpty())
        assertTrue(database.reportDao().getDailyWeatherForProject(projectId).first().isEmpty())
    }

    private fun openDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun testProject() = Project(
        name = "Weather Test Project",
        location = "Bulacan",
        implementingOffice = "NHA",
        contractor = "Test Contractor",
        scopeOfWork = "Test scope",
        contractCostOriginal = 1.0,
        contractCostRevised = 1.0,
        contractDurationDays = 1,
        dateStarted = "2026-08-01",
        completionDateOriginal = "2026-08-02",
        completionDateRevised = "2026-08-02"
    )

    private fun testReport() = WeeklyReport(
        projectId = projectId,
        reportingWeek = "Week 1: Aug 1 - Aug 7, 2026",
        daysElapsed = 1,
        remainingDays = 0,
        targetAccomplishmentPct = 10.0,
        actualAccomplishmentPct = 10.0,
        manpowerJson = "[]",
        equipmentJson = "[]",
        activitiesJson = "[]",
        issuesJson = "[]",
        accomplishmentItemsJson = "[]",
        documentsIssuedReceivedJson = "[]",
        attachedPhotoUrlsJson = "[]",
        submittedByStaff = "Test User"
    )

    private fun testWeather() = DailyHourlyWeather(
        projectId = projectId,
        date = "2026-08-01",
        dayOfWeek = "Monday",
        hourlyConditionsCsv = List(24) { "FAIR" }.joinToString(",")
    )
}
