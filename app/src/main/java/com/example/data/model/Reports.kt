package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weekly_reports",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class WeeklyReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val reportingWeek: String, // e.g. "Week 30: July 20 - July 26, 2026"
    val daysElapsed: Int,
    val remainingDays: Int,
    val targetAccomplishmentPct: Double,
    val actualAccomplishmentPct: Double,
    val manpowerJson: String, // JSON array of ManpowerItem
    val equipmentJson: String, // JSON array of EquipmentItem
    val activitiesJson: String, // JSON array of DailyActivity
    val issuesJson: String, // JSON array of WeeklyIssue
    val accomplishmentItemsJson: String, // JSON array of AccomplishmentItem
    val documentsIssuedReceivedJson: String, // JSON array of DocumentActionItem
    val attachedPhotoUrlsJson: String, // JSON array of image URLs
    val submittedByStaff: String,
    val createdAtTimestamp: Long = System.currentTimeMillis()
)

data class ManpowerItem(
    val designation: String,
    val count: Int,
    val remarks: String = ""
)

data class EquipmentItem(
    val description: String,
    val count: Int,
    val status: String, // "Operational", "Idle", "Reserve"
    val remarks: String = ""
)

data class DailyActivity(
    val day: String, // "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"
    val description: String,
    val remarks: String = ""
)

data class WeeklyIssue(
    val description: String,
    val actionTaken: String,
    val remarks: String = ""
)

data class ScopeItem(
    val name: String,
    val amount: Double,
    val weightPct: Double
)

data class AccomplishmentItem(
    val itemDescription: String,
    val weightPct: Double = 0.0,
    val targetPct: Double,
    val actualPct: Double
) {
    val variancePct: Double
        get() = actualPct - targetPct
}

data class DocumentActionItem(
    val document: String,
    val date: String,
    val implementingOfficeAction: String,
    val contractorAction: String
)

enum class WeatherCondition(val label: String, val isWorkable: Boolean) {
    FAIR("Fair", true),
    CLOUDY("Cloudy", true),
    RAIN_SHOWERS("Rain Showers", true),
    RAINY("Rainy", false),
    STORMY("Stormy", false)
}

@Entity(
    tableName = "daily_hourly_weather",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WeeklyReport::class,
            parentColumns = ["id"],
            childColumns = ["weeklyReportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["weeklyReportId"])]
)
data class DailyHourlyWeather(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val weeklyReportId: Long? = null,
    val date: String, // YYYY-MM-DD
    val dayOfWeek: String, // "Monday", "Tuesday", etc.
    // 24 entries separated by commas or JSON: e.g. "FAIR,FAIR,CLOUDY,RAINY,..."
    val hourlyConditionsCsv: String
)

@Entity(
    tableName = "monthly_reports",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class MonthlyReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val reportingMonth: String, // e.g. "July 2026"
    val scopeWeightPct: Double,
    val scopeTargetPct: Double,
    val scopeActualPct: Double,
    val paymentsJson: String, // List of PaymentEntry
    val unworkableDaysCount: Int, // Auto-derived from weather logs
    val workableDaysCount: Int,
    val cpesIssuesJson: String, // List of CpesIssue
    val recommendations: String,
    val preparedByName: String = "Engr. Juan Dela Cruz",
    val preparedByStatus: String = "Reviewed", // "Pending Review", "Reviewed", "Noted"
    val checkedByName: String = "Engr. Roberto Santos",
    val checkedByStatus: String = "Reviewed",
    val notedByName: String = "Director Maria Perez",
    val notedByStatus: String = "Noted",
    val auditTrailJson: String = "", // Timestamps & approval notes
    val accomplishmentItemsJson: String = "[]" // List of AccomplishmentItem
)

data class PaymentEntry(
    val name: String,
    val dvNo: String = "",
    val date: String,
    val periodCovered: String,
    val grossAmount: Double,
    val percentage: Double,
    val balanceAmount: Double,
    val balancePercentage: Double,
    val fileUrl: String = ""
)

data class CpesIssue(
    val description: String,
    val date: String,
    val implementingOfficeAction: String,
    val contractorAction: String
)
