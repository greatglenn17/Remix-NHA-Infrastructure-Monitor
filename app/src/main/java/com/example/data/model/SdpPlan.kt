package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sdp_plans",
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
data class SdpPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val planName: String,
    val pdfFileUrl: String,
    val version: Int = 1,
    val isActive: Boolean = true,
    val uploadedDate: String, // YYYY-MM-DD
    val uploadedBy: String,
    val description: String = ""
)
