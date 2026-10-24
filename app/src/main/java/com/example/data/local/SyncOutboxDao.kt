package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SyncOutboxEvent
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE entityUuid = :entityUuid AND state IN ('PENDING', 'FAILED'))")
    suspend fun hasPendingFor(entityUuid: String): Boolean
}
