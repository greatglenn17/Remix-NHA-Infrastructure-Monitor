package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "time_extensions",
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
data class TimeExtension(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val extensionNo: Int,
    val noOfDays: Int,
    val revisedDurationDays: Int,
    val periodConsidered: String, // e.g. "June 1 - June 30, 2026"
    val reason: String,
    val revisedCompletionDate: String, // YYYY-MM-DD
    val remarks: String
)

@Entity(
    tableName = "variation_orders",
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
data class VariationOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val voNo: Int,
    val description: String,
    val costDifference: Double,
    val revisedContractCost: Double,
    val approvalDate: String,
    val remarks: String
)

@Entity(
    tableName = "work_suspensions",
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
data class WorkSuspensionOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val effectivityDate: String,
    val durationDays: Int,
    val endDate: String,
    val reason: String,
    val remarks: String
)

@Entity(
    tableName = "work_resumptions",
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
data class WorkResumptionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val dateResumed: String,
    val reason: String,
    val remarks: String
)

@Entity(
    tableName = "pending_documents",
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
data class PendingDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val documentName: String,
    val status: String = "Pending", // "Pending", "Submitted", "Approved"
    val remarks: String = "",
    val isCoreChecklist: Boolean = true,
    val fileUrl: String = ""
)
