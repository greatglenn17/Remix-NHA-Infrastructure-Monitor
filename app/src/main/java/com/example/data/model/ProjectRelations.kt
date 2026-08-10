package com.example.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class ProjectWithDetails(
    @Embedded val project: Project,
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val payments: List<ProjectPayment> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val issues: List<ProjectIssue> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val weeklyReports: List<WeeklyReport> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val monthlyReports: List<MonthlyReport> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val pendingDocuments: List<PendingDocument> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val weatherLogs: List<DailyHourlyWeather> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val inspections: List<ProjectInspection> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "projectId")
    val images: List<ProjectImage> = emptyList()
)

data class WeeklyReportWithWeather(
    @Embedded val report: WeeklyReport,
    @Relation(parentColumn = "id", entityColumn = "weeklyReportId")
    val weatherLogs: List<DailyHourlyWeather> = emptyList()
)
