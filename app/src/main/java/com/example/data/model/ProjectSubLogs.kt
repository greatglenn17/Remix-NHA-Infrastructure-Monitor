package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

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
    indices = [Index(value = ["projectId"]), Index(value = ["uuid"])]
)
data class PendingDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val documentName: String,
    val status: String = "Pending", // "Pending", "Submitted", "Approved"
    val remarks: String = "",
    val isCoreChecklist: Boolean = true,
    val fileUrl: String = "",
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/** A developer's request for an order, tracked from the day NHA receives it until it is packaged. */
@Entity(
    tableName = "developer_requests",
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
data class DeveloperRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val requestType: String,
    val details: String = "",
    val dateRequested: String = "",
    val dateReceived: String = "",
    // Blank until NHA has packaged the request.
    val datePackaged: String = "",
    // The developer's request letter, scanned or uploaded; an attachment reference.
    val requestFileUrl: String = "",
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/** A letter NHA sent the contractor, what the contractor did about it, and the contractor's reply letter once it comes. */
@Entity(
    tableName = "contractor_letters",
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
data class ContractorLetter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val dateSent: String,
    val subject: String,
    val actionTaken: String = "",
    // Blank until the contractor replies; the reply letter is an attachment reference.
    val dateReplied: String = "",
    val replyFileUrl: String = "",
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    // Blank until the contractor receives the letter; NHA's receiving copy (signed or stamped) is an attachment reference.
    val dateReceived: String = "",
    val receivedFileUrl: String = ""
)

/**
 * An uploaded Abstract of Physical Accomplishment of the actual work: the project's actual accomplishment as of that
 * day. One per project and "As of" date; an upload of the same date replaces it (see util/ProjectAbstracts).
 */
@Entity(
    tableName = "project_abstracts",
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
data class ProjectAbstract(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val asOf: String, // yyyy-MM-dd
    val overallPct: Double,
    // Every row of the sheet down to the grand total, as ProjectAbstracts.rowsJson writes them.
    val rowsJson: String,
    val fileName: String = "",
    val uploadedBy: String = "",
    // The N of the sheet's "EQUIVALENT TO N UNITS" columns, 0 without them; a record saved without it reads as 0.
    @ColumnInfo(defaultValue = "0") val equivUnits: Double = 0.0,
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)
