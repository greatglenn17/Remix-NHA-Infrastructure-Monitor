package com.example.data.remote

/** Decides whether a cloud copy should replace the local one. */
object RemoteVersion {

    /**
     * A higher revision always wins. At the same revision the cloud holds whichever phone
     * uploaded last, so a different edit time means another phone's change replaced ours
     * and every phone should take it; the same edit time is just our own upload coming back.
     */
    fun isNewer(localRevision: Long, localUpdatedAt: Long, remoteRevision: Long, remoteUpdatedAt: Long): Boolean =
        remoteRevision > localRevision || (remoteRevision == localRevision && remoteUpdatedAt != localUpdatedAt)
}
