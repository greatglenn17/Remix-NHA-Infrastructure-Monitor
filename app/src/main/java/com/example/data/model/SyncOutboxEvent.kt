package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Durable local intent to synchronize one record.  It is deliberately kept
 * independent of a transport so no local change is falsely reported as synced.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["entityUuid"]), Index(value = ["state"])]
)
data class SyncOutboxEvent(
    @PrimaryKey val eventId: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityUuid: String,
    val operation: String,
    val revision: Long,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val state: String = STATE_PENDING,
    val lastError: String = ""
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_SYNCED = "SYNCED"
        const val STATE_FAILED = "FAILED"
    }
}
