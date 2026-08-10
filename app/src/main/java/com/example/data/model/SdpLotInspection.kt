package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sdp_lot_inspections",
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
        Index(value = ["sdpLotId"])
    ]
)
data class SdpLotInspection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sdpPlanId: Long,
    val sdpLotId: Long,
    val inspectionTimestamp: Long = System.currentTimeMillis(),
    val inspectionDate: String,
    val inspectedBy: String,
    val physicalProgress: Int,
    val constructionStatus: String,
    val currentActivity: String,
    val contractor: String,
    val remarks: String,
    val billingStatus: String = "NOT BILLED",
    val billingReference: String = "",
    val createdDate: String
)
