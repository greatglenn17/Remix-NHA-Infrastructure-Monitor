package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

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
        Index(value = ["sdpLotId"], unique = true),
        Index(value = ["uuid"])
    ]
)
data class SdpLotProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val sdpPlanId: Long,
    val sdpLotId: Long,
    val physicalProgress: Int = 0, // 0 to 100
    val drainageProgress: Int = 0, // 0 to 100; road lots only
    val constructionStatus: String = "Not Started", // "Not Started", "Layout / Excavation Started", "Under Construction", "Substantially Completed", "Completed"
    val currentActivity: String = "",
    val startDate: String = "",
    val targetCompletionDate: String = "",
    val contractor: String = "",
    val remarks: String = "",
    // Developer billing. A lot is billed twice: M1 (80%) in these fields, which were the
    // single "billed" mark before, and M2 (20%) in the m2 fields below.
    val billingStatus: String = "NOT BILLED", // "NOT BILLED", "BILLED"
    val billingDate: String = "",
    val billedBy: String = "",
    val billingReference: String = "",
    val billingRemarks: String = "",
    val m2BillingStatus: String = "NOT BILLED", // "NOT BILLED", "BILLED"
    val m2BillingDate: String = "",
    val m2BilledBy: String = "",
    val m2BillingReference: String = "",
    val m2BillingRemarks: String = "",
    // Site photos of this lot (attachment refs, JSON array); each is stamped with its block and lot.
    val photosJson: String = "[]",
    // Audit fields
    val createdBy: String = "",
    val createdDate: String = "",
    val lastModifiedBy: String = "",
    val lastModifiedDate: String = "",
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/** M1 is the first billing of a lot, 80% of it. */
val SdpLotProgress?.m1Billed: Boolean get() = this?.billingStatus == "BILLED"
/** M2 is the second billing of a lot, the last 20%. */
val SdpLotProgress?.m2Billed: Boolean get() = this?.m2BillingStatus == "BILLED"
val SdpLotProgress?.anyBilled: Boolean get() = m1Billed || m2Billed

/** How much of the lot is billed, as the SDP shows it: "NOT BILLED", "M1 BILLED (80%)", "M1 + M2 BILLED (100%)". */
val SdpLotProgress?.billingLabel: String get() = when {
    m1Billed && m2Billed -> "M1 + M2 BILLED (100%)"
    m1Billed -> "M1 BILLED (80%)"
    m2Billed -> "M2 BILLED (20%)"
    else -> "NOT BILLED"
}
