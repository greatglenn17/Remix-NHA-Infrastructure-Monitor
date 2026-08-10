package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sdp_lot_progress",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SdpPlan::class,
            parentColumns = ["id"],
            childColumns = ["sdpPlanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SdpLot::class,
            parentColumns = ["id"],
            childColumns = ["sdpLotId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["sdpPlanId"]),
        Index(value = ["sdpLotId"], unique = true)
    ]
)
data class SdpLotProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sdpPlanId: Long,
    val sdpLotId: Long,
    val physicalProgress: Int = 0, // 0 to 100
    val constructionStatus: String = "Not Started", // "Not Started", "Layout / Excavation Started", "Under Construction", "Substantially Completed", "Completed"
    val currentActivity: String = "",
    val startDate: String = "",
    val targetCompletionDate: String = "",
    val contractor: String = "",
    val remarks: String = "",
    // Developer Billing Foundation
    val billingStatus: String = "NOT BILLED", // "NOT BILLED", "BILLED"
    val billingDate: String = "",
    val billedBy: String = "",
    val billingReference: String = "",
    val billingRemarks: String = "",
    // Audit fields
    val createdBy: String = "",
    val createdDate: String = "",
    val lastModifiedBy: String = "",
    val lastModifiedDate: String = ""
)
