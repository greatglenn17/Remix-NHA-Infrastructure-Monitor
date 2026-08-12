package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    // Weekly Reports
    @Query("SELECT * FROM weekly_reports WHERE projectId = :projectId ORDER BY id DESC")
    fun getWeeklyReportsForProject(projectId: Long): Flow<List<WeeklyReport>>

    @Transaction
    @Query("SELECT * FROM weekly_reports WHERE projectId = :projectId ORDER BY id DESC")
    fun getWeeklyReportsWithWeatherForProject(projectId: Long): Flow<List<WeeklyReportWithWeather>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeeklyReport(report: WeeklyReport): Long

    @Update
    suspend fun updateWeeklyReport(report: WeeklyReport)

    @Query("DELETE FROM weekly_reports WHERE id = :id")
    suspend fun deleteWeeklyReport(id: Long)

    // Cloud Backup Queries
    @Query("SELECT * FROM weekly_reports")
    fun getAllWeeklyReports(): Flow<List<WeeklyReport>>

    // Daily Hourly Weather
    @Query("SELECT * FROM daily_hourly_weather")
    fun getAllDailyWeather(): Flow<List<DailyHourlyWeather>>

    @Query("SELECT * FROM daily_hourly_weather WHERE projectId = :projectId ORDER BY date ASC")
    fun getDailyWeatherForProject(projectId: Long): Flow<List<DailyHourlyWeather>>

    @Query("SELECT * FROM daily_hourly_weather WHERE projectId = :projectId AND date LIKE :monthPrefix || '%' ORDER BY date ASC")
    fun getDailyWeatherForProjectAndMonth(projectId: Long, monthPrefix: String): Flow<List<DailyHourlyWeather>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyWeather(weather: DailyHourlyWeather)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyWeatherList(weatherList: List<DailyHourlyWeather>)

    // Weather may only be displayed when it belongs to an existing weekly
    // report. This removes legacy placeholder/orphan weather rows created by
    // pre-v1.2.2 builds without touching valid submitted reports.
    @Query("DELETE FROM daily_hourly_weather WHERE weeklyReportId IS NULL OR weeklyReportId NOT IN (SELECT id FROM weekly_reports)")
    suspend fun deleteWeatherLogsWithoutWeeklyReport()

    // Monthly Reports
    @Query("SELECT * FROM monthly_reports")
    fun getAllMonthlyReports(): Flow<List<MonthlyReport>>

    @Query("SELECT * FROM monthly_reports WHERE projectId = :projectId ORDER BY id DESC")
    fun getMonthlyReportsForProject(projectId: Long): Flow<List<MonthlyReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthlyReport(report: MonthlyReport): Long

    @Update
    suspend fun updateMonthlyReport(report: MonthlyReport)

    @Query("DELETE FROM monthly_reports WHERE id = :id")
    suspend fun deleteMonthlyReport(id: Long)
}
