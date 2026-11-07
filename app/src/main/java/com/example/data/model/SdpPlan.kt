package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

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
    indices = [Index(value = ["projectId"]), Index(value = ["uuid"])]
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
    val description: String = "",
    // The plan's printed scale 1:N (e.g. 1000); 0 when not set. Lot areas are worked out from it.
    val scaleDenominator: Int = 0,
    // The plan area of the page as "minX,minY,maxX,maxY" (0..1); blank: the whole page.
    val frame: String = "",
    // The site of the contract this plan is for ("Phase 2"); its revisions share it. Blank: the first site, unnamed.
    val siteName: String = "",
    // Cross-device sync identity and version; see RecordSyncSchema.
    val uuid: String = UUID.randomUUID().toString(),
    val revision: Long = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

/** One contract can cover more than one site, each with its own plan and revisions. */
object SdpSites {
    fun label(site: String) = site.ifBlank { "Main site" }

    /**
     * Each site's plan in use, sites in the order they were added: its active revision, else its latest.
     * An app before sites switches off every plan of the project when it uploads or picks a version.
     */
    fun current(plans: List<SdpPlan>): List<SdpPlan> =
        plans.sortedBy { it.id }.groupBy { it.siteName }.values.map { site ->
            site.filter { it.isActive }.maxByOrNull { it.version } ?: site.maxBy { it.version }
        }
}
