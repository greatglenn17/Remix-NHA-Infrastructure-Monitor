package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

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
    indices = [Index(value = ["projectId"]), Index(value = ["sdpPlanId"]), Index(value = ["uuid"])]
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
    // One of LOT_USES; blank on lots saved before uses were kept (see [use]).
    val lotUse: String = "",
    val createdBy: String = "",
    val createdDate: String = "",
    val lastModifiedBy: String = "",
    val lastModifiedDate: String = "",
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/** What a lot of the plan is for. Only saleable lots are housing units. */
val LOT_USES = listOf("Saleable", "Open Space", "Road Lot", "Breaker / Buffer", "Easement", "Facility")

/** Block "OS" areas were the open spaces before lot uses were kept. */
val SdpLot.use: String get() = lotUse.ifBlank { if (blockNumber == "OS") "Open Space" else "Saleable" }
val SdpLot.isSaleable: Boolean get() = use == "Saleable"
/** Road lots carry a drainage progress besides the road's own. */
val SdpLot.isRoadLot: Boolean get() = use == "Road Lot"

/** Found lots come in with no block or lot; the engineer names them (Update Progress, Renumber block). */
val SdpLot.isUnassigned: Boolean get() = blockNumber.isBlank() || lotNumber.isBlank()
/** "Block 3 Lot 12", or "Unassigned lot" until the lot is named. */
val SdpLot.label: String get() = if (isUnassigned) "Unassigned lot" else "Block $blockNumber Lot $lotNumber"
