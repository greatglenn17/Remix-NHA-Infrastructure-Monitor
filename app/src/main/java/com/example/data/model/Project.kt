package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProjectType(val label: String) {
    HOUSING_PROJECT("Housing Project"),
    COMMUNITY_FACILITY("Community Facility"),
    CUSTOM("Custom")
}

enum class ProjectStatus(val label: String) {
    ONGOING("On-going"),
    SUSPENDED("Suspended"),
    BEHIND_SCHEDULE("Behind Schedule")
}

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val location: String,
    val implementingOffice: String,
    val contractor: String,
    val scopeOfWork: String,
    val projectType: String = ProjectType.HOUSING_PROJECT.label,
    val landArea: String = "4.5 Hectares",
    val numberOfUnits: String = "120 Housing Units",
    val contractCostOriginal: Double,
    val contractCostRevised: Double,
    val contractDurationDays: Int,
    val dateStarted: String, // YYYY-MM-DD
    val completionDateOriginal: String, // YYYY-MM-DD
    val completionDateRevised: String, // YYYY-MM-DD
    val status: String = ProjectStatus.ONGOING.label,
    val targetAccomplishment: Double = 0.0,
    val actualAccomplishment: Double = 0.0,
    val latestUpdatePhotoUrl: String = "",
    val assignedStaff: String = "Juan Dela Cruz",
    val lastUpdatedDate: String = "2026-08-01"
) {
    val variance: Double
        get() = actualAccomplishment - targetAccomplishment
}
