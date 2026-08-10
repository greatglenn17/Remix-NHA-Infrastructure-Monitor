package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "project_inspections",
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
data class ProjectInspection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val inspectorName: String,
    val inspectionDate: String,
    val findings: String,
    val status: String = "Passed",
    val remarks: String = ""
)

@Entity(
    tableName = "project_images",
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
data class ProjectImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val imageUrl: String,
    val caption: String = "",
    val category: String = "Progress",
    val uploadedDate: String = "2026-08-01"
)
