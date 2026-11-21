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

/** A failed event's [SyncOutboxEvent.lastError] in plain words: "No signal", "Not allowed", or "Failed: <short message>". */
fun syncErrorReading(error: String): String {
    val e = error.lowercase()
    return when {
        listOf("unavailable", "deadline", "offline", "network", "resolve host", "timeout", "timed out", "retry limit", "connect")
            .any { it in e } -> "No signal"
        "permission" in e -> "Not allowed"
        error.isBlank() -> "Failed"
        else -> "Failed: " + error.lineSequence().first().trim().let { if (it.length > 80) it.take(79) + "…" else it }
    }
}
