package com.example

import com.example.data.remote.RemoteVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteVersionTest {

    @Test
    fun newerRemoteRevision_isApplied() {
        assertTrue(RemoteVersion.isNewer(localRevision = 2, localUpdatedAt = 500, remoteRevision = 3, remoteUpdatedAt = 100))
    }

    @Test
    fun olderRemoteRevision_isIgnored() {
        assertFalse(RemoteVersion.isNewer(localRevision = 3, localUpdatedAt = 100, remoteRevision = 2, remoteUpdatedAt = 900))
    }

    @Test
    fun ownUploadEchoedBack_isIgnored() {
        assertFalse(RemoteVersion.isNewer(localRevision = 2, localUpdatedAt = 700, remoteRevision = 2, remoteUpdatedAt = 700))
    }

    @Test
    fun sameRevisionFromAnotherPhone_isApplied() {
        // Both phones edited revision 1 offline; the cloud now holds the other phone's revision 2.
        assertTrue(RemoteVersion.isNewer(localRevision = 2, localUpdatedAt = 700, remoteRevision = 2, remoteUpdatedAt = 650))
    }
}
