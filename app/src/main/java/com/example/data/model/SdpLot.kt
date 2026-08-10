package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sdp_lots",
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
        )
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["sdpPlanId"])]
)
data class SdpLot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sdpPlanId: Long,
    val blockNumber: String,
    val lotNumber: String,
    val housingUnitNumber: String = "",
    val lotAreaSqM: Double = 0.0,
    val polygonNormalizedJson: String, // Array of normalized points [[x, y], ...]
    val isActive: Boolean = true,
    val description: String = "",
    val createdBy: String = "",
    val createdDate: String = "",
    val lastModifiedBy: String = "",
    val lastModifiedDate: String = ""
)
