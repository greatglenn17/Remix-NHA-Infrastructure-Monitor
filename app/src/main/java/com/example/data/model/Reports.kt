package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

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
    indices = [Index(value = ["projectId"]), Index(value = ["uuid"])]
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
    // Photo ref -> title, time taken and location; see PhotoDetails.
    val photoDetailsJson: String = "{}",
    val submittedByStaff: String,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
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
    val remarks: String = "",
    // One of ISSUE_ACTION_BY, or blank on issues saved before it existed.
    val actionBy: String = ""
)

/** The weekly report's manpower and equipment choices; any other can still be typed. */
val MANPOWER_DESIGNATIONS = listOf(
    "Project Manager", "Project Engineer", "Project Architect", "Safety Officer", "Foreman", "Operator", "Labor",
    "Security Guard", "Driver", "Warehouseman"
)
val EQUIPMENT_NAMES = listOf("Backhoe", "Bulldozer", "Road Roller", "Forward Truck", "Mini Dump Truck", "Generator")

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

/** The weekly report's only accomplishment lines; Scope of Works and bar chart items fold into them by name. */
val ACCOMPLISHMENT_LINES = listOf("Land Acquisition", "Land Development", "Housing Construction")
private val HOUSING_WORDS = Regex("\\b(house|houses|housing|units?)\\b", RegexOption.IGNORE_CASE)

/** "acquisition" is Land Acquisition, a house, housing or unit is Housing Construction, any other item of work is Land Development. */
fun accomplishmentLine(itemName: String): String = when {
    itemName.contains("acquisition", ignoreCase = true) -> ACCOMPLISHMENT_LINES[0]
    HOUSING_WORDS.containsMatchIn(itemName) -> ACCOMPLISHMENT_LINES[2]
    else -> ACCOMPLISHMENT_LINES[1]
}

/** The three lines' weights: each is the sum of its items'. */
fun accomplishmentScope(items: List<ScopeItem>): List<ScopeItem> = ACCOMPLISHMENT_LINES.map { line ->
    items.filter { accomplishmentLine(it.name) == line }.let { ScopeItem(line, it.sumOf(ScopeItem::amount), it.sumOf(ScopeItem::weightPct)) }
}

/** [value] of the items by line, each line its items' weighted average (a plain one when they have no weights). */
fun <T> lineAverages(items: List<T>, name: (T) -> String, weight: (T) -> Double, value: (T) -> Double): Map<String, Double> =
    items.groupBy { accomplishmentLine(name(it)) }.mapValues { (_, rows) ->
        val w = rows.sumOf(weight)
        rows.singleOrNull()?.let(value) ?: if (w > 0) rows.sumOf { value(it) * weight(it) } / w else rows.sumOf(value) / rows.size
    }

/** A saved report's rows as the three lines, so reports saved item by item read the same as new ones. */
fun accomplishmentLines(items: List<AccomplishmentItem>): List<AccomplishmentItem> {
    if (items.isEmpty()) return items
    val target = lineAverages(items, AccomplishmentItem::itemDescription, AccomplishmentItem::weightPct, AccomplishmentItem::targetPct)
    val actual = lineAverages(items, AccomplishmentItem::itemDescription, AccomplishmentItem::weightPct, AccomplishmentItem::actualPct)
    return ACCOMPLISHMENT_LINES.map { line ->
        AccomplishmentItem(line, items.filter { accomplishmentLine(it.itemDescription) == line }.sumOf { it.weightPct }, target[line] ?: 0.0, actual[line] ?: 0.0)
    }
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

/**
 * The weekly report form's one-tap weather for a day: its label and 24 hourly conditions, 12 AM first. A half-and-half
 * preset gives the first condition to 12 AM-11 AM and the second to 12 PM-11 PM. web/logic.js WEATHER_PRESETS matches.
 */
val WEATHER_PRESETS: List<Pair<String, List<String>>> = listOf(
    "All Fair" to List(24) { "FAIR" },
    "All Cloudy" to List(24) { "CLOUDY" },
    "Cloudy AM / Showers PM" to List(24) { if (it < 12) "CLOUDY" else "RAIN_SHOWERS" },
    "All Rainy" to List(24) { "RAINY" },
    "Rainy AM / Stormy PM" to List(24) { if (it < 12) "RAINY" else "STORMY" },
    "All Stormy" to List(24) { "STORMY" }
)

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
    indices = [Index(value = ["projectId"]), Index(value = ["uuid"])]
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
    // Sign-offs start empty; each is filled in by the person who signs.
    val preparedByName: String = "",
    val preparedByStatus: String = "Pending Review", // "Pending Review", "Reviewed", "Noted"
    val checkedByName: String = "",
    val checkedByStatus: String = "Pending Review",
    val notedByName: String = "",
    val notedByStatus: String = "Pending Review",
    val auditTrailJson: String = "", // Timestamps & approval notes
    val accomplishmentItemsJson: String = "[]", // List of AccomplishmentItem
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
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
