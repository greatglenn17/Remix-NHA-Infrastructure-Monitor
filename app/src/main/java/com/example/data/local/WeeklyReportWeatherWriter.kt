package com.example.data.local

import androidx.room.withTransaction
import com.example.data.model.DailyHourlyWeather
import com.example.data.model.WeeklyReport

/**
 * Persists a weekly report and its weather rows as one Room transaction.
 *
 * Weather drafts intentionally do not carry a weeklyReportId supplied by the UI.
 * The database-generated report ID is assigned only after the parent row exists.
 */
class WeeklyReportWeatherWriter(
    private val database: AppDatabase,
    private val reportDao: ReportDao
) {
    suspend fun create(
        report: WeeklyReport,
        weatherDrafts: List<DailyHourlyWeather>
    ): Long = database.withTransaction {
        val weeklyReportId = reportDao.insertWeeklyReport(report)
        reportDao.insertDailyWeatherList(
            weatherDrafts.map { draft ->
                draft.copy(
                    id = 0,
                    projectId = report.projectId,
                    weeklyReportId = weeklyReportId
                )
            }
        )
        weeklyReportId
    }

    suspend fun update(
        report: WeeklyReport,
        weatherDrafts: List<DailyHourlyWeather>
    ) = database.withTransaction {
        require(report.id > 0) { "A persisted weekly report ID is required." }
        reportDao.updateWeeklyReport(report)
        reportDao.deleteDailyWeatherForWeeklyReport(report.id)
        reportDao.insertDailyWeatherList(
            weatherDrafts.map { draft ->
                draft.copy(
                    id = 0,
                    projectId = report.projectId,
                    weeklyReportId = report.id
                )
            }
        )
    }
}
