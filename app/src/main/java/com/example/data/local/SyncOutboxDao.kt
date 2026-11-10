package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SyncOutboxEvent
import kotlinx.coroutines.flow.Flow

/**
 * Payload of the project event a bar chart save queues. Only that event uploads barChartJson, so another
 * phone's older copy of the project (often without the chart) never replaces the chart in the cloud.
 */
fun barChartPayload(projectUuid: String) = "{\"projectUuid\":\"$projectUuid\",\"barChart\":true}"

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(event: SyncOutboxEvent): Long

    @Query("SELECT * FROM sync_outbox WHERE state IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    fun pendingEvents(): Flow<List<SyncOutboxEvent>>

    @Query("SELECT * FROM sync_outbox WHERE state IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    suspend fun pendingEventsList(): List<SyncOutboxEvent>

    @Query("UPDATE sync_outbox SET state = :state, attemptCount = attemptCount + 1, lastError = :error WHERE eventId = :eventId")
    suspend fun updateAttempt(eventId: String, state: String, error: String = "")

    @Query("UPDATE sync_outbox SET state = 'SYNCED', lastError = '' WHERE eventId = :eventId")
    suspend fun markSynced(eventId: String)

    /** New, never-attempted events only; FAILED ones are retried on startup and sync events. */
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state = 'PENDING'")
    fun newPendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE state = 'FAILED'")
    fun failedCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE entityUuid = :entityUuid AND state IN ('PENDING', 'FAILED'))")
    suspend fun hasPendingFor(entityUuid: String): Boolean

    /** True while this phone has a bar chart change for the project not yet in the cloud (see [barChartPayload]). */
    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE entityType = 'project' AND entityUuid = :projectUuid AND state IN ('PENDING', 'FAILED') AND payloadJson LIKE '%\"barChart\":true%')")
    suspend fun hasPendingBarChartFor(projectUuid: String): Boolean

    /** True for a project this phone queued as new whose upload the cloud has not confirmed yet. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE entityType = 'project' AND entityUuid = :projectUuid AND operation = 'CREATE') " +
            "AND NOT EXISTS(SELECT 1 FROM sync_outbox WHERE entityType = 'project' AND entityUuid = :projectUuid AND state = 'SYNCED')"
    )
    suspend fun isUnconfirmedCreate(projectUuid: String): Boolean

    /** The `entityUuid` this phone queued when it added the named attachment file (carries the project), or null. */
    @Query("SELECT entityUuid FROM sync_outbox WHERE entityType = 'file' AND operation = 'UPSERT' AND (entityUuid = :name OR entityUuid LIKE '%/' || :name) LIMIT 1")
    suspend fun fileEntityFor(name: String): String?
}
