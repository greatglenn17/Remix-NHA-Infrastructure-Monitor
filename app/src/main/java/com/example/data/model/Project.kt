package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

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
    /** Immutable cross-device identity.  Room's numeric [id] remains local only. */
    val projectUuid: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdByUid: String = "",
    val updatedByUid: String = "",
    val revision: Long = 1,
    val isDeleted: Boolean = false,
    val name: String,
    val location: String,
    val implementingOffice: String,
    val contractor: String,
    val scopeOfWork: String,
    val projectType: String = ProjectType.HOUSING_PROJECT.label,
    val landArea: String = "",
    val numberOfUnits: String = "",
    // Project Profile fields, free text as written on the NHA profile sheet.
    val projectedBeneficiaries: String = "",
    val boardResolution: String = "",
    val noticeOfAward: String = "",
    val noticeToProceed: String = "",
    // Signatories printed on the weekly and monthly Blue Book reports.
    val preparedByName: String = "",
    val preparedByPosition: String = "",
    val checkedByName: String = "",
    val checkedByPosition: String = "",
    val notedByName: String = "",
    val notedByPosition: String = "",
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
    val assignedStaff: String = "",
    val assignedUids: String = "", // JSON array of Firebase uids allowed to open this project
    val lastUpdatedDate: String = "2026-08-01"
) {
    val variance: Double
        get() = actualAccomplishment - targetAccomplishment
}
