package com.example.ui.components

/** What the header chip says about this phone's changes reaching the cloud. */
sealed class SyncChip {
    /** Signed in with the offline fallback: nothing leaves this phone. */
    object PhoneOnly : SyncChip()
    /** Every change this phone made is in the cloud. */
    object Synced : SyncChip()
    /** Changes queued and not tried yet; the upload starts as soon as they are queued. */
    data class Uploading(val count: Int) : SyncChip()
    /** An upload failed (usually no signal); they are retried on the next start or sync. */
    data class Waiting(val count: Int) : SyncChip()

    companion object {
        fun of(cloudSignedIn: Boolean, pending: Int, failed: Int): SyncChip = when {
            !cloudSignedIn -> PhoneOnly
            failed > 0 -> Waiting(pending + failed)
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
