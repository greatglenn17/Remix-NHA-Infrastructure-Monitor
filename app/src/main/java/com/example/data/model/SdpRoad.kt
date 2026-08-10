package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sdp_roads",
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
data class SdpRoad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sdpPlanId: Long,
    val roadName: String,
    val roadType: String = "Main Road", // "Main Road", "Secondary Road", "Alley"
    val polylineNormalizedJson: String, // Array of normalized points [[x, y], ...]
    val isActive: Boolean = true,
    val createdBy: String = "",
    val createdDate: String = "",
    val lastModifiedBy: String = "",
    val lastModifiedDate: String = ""
)
