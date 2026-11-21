package com.example.ui.components

import com.example.data.model.SyncOutboxEvent
import com.example.data.model.syncErrorReading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the header chip says about this phone's changes reaching the cloud. */
sealed class SyncChip {
    /** Signed in with the offline fallback: nothing leaves this phone. */
    object PhoneOnly : SyncChip()
    /** Every change this phone made is in the cloud. */
    object Synced : SyncChip()
    /** Changes queued and not tried yet; the upload starts as soon as they are queued. */
    data class Uploading(val count: Int) : SyncChip()
    /** An upload failed; they are retried on the next start or sync. [reasons] says why ([failureLines]). */
    data class Waiting(val count: Int, val reasons: List<String> = emptyList()) : SyncChip()

    companion object {
        fun of(cloudSignedIn: Boolean, pending: Int, failed: Int, reasons: List<String> = emptyList()): SyncChip = when {
            !cloudSignedIn -> PhoneOnly
            failed > 0 -> Waiting(pending + failed, reasons)
            pending > 0 -> Uploading(pending)
            else -> Synced
        }
    }
}

/** The chip's words, e.g. "3 CHANGES WAITING". */
val SyncChip.label: String get() = when (this) {
    SyncChip.PhoneOnly -> "THIS PHONE ONLY"
    SyncChip.Synced -> "SYNCED"
    is SyncChip.Uploading -> "UPLOADING $count"
    is SyncChip.Waiting -> if (count == 1) "1 CHANGE WAITING" else "$count CHANGES WAITING"
}

/** What the state means, in plain words: the chip's window and the dashboard's "What these mean". */
val SyncChip.meaning: String get() = when (this) {
    SyncChip.PhoneOnly -> "Signed in offline: changes stay on this phone and are not sent to the cloud."
    SyncChip.Synced -> "Every change made on this phone is in the cloud."
    is SyncChip.Uploading -> "Changes are being sent to the cloud now."
    is SyncChip.Waiting -> "Some changes could not be sent. They are sent again the next time the app starts or syncs."
}

/** The sync window's list of failed uploads, one line per kind and reading: "2 records: No signal". */
fun failureLines(failed: List<SyncOutboxEvent>): List<String> =
    failed.groupingBy { kindOf(it.entityType) to syncErrorReading(it.lastError) }.eachCount()
        .map { (key, n) -> "$n ${if (n == 1) key.first.first else key.first.second}: ${key.second}" }

private fun kindOf(entityType: String): Pair<String, String> = when (entityType) {
    "project" -> "project" to "projects"
    "file" -> "photo or file" to "photos or files"
    else -> "record" to "records"
}

private val SYNCED_AT = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US)

/** "Last synced Nov 1, 2026, 10:42 AM", or a plain line when this phone has never had everything sent. */
fun lastSyncedText(at: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
    at?.let { "Last synced " + Instant.ofEpochMilli(it).atZone(zone).format(SYNCED_AT) } ?: "Not fully synced on this phone yet"
