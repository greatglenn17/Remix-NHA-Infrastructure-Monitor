package com.example

import com.example.data.remote.ProjectReconciler
import com.example.data.remote.ProjectReconciler.LocalRef
import com.example.data.remote.ProjectReconciler.RemoteRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectReconcilerTest {

    private fun local(id: Long, uuid: String, name: String = "Bulakan Housing", location: String = "Bulakan, Bulacan") =
        LocalRef(id, uuid, ProjectReconciler.key(name, location))

    private fun remote(uuid: String, createdAt: Long = 1L, deleted: Boolean = false, name: String = "Bulakan Housing", location: String = "Bulakan, Bulacan") =
        RemoteRef(uuid, ProjectReconciler.key(name, location), createdAt, deleted)

    @Test
    fun key_ignoresCaseAndExtraWhitespace() {
        assertEquals(
            ProjectReconciler.key("Bulakan  Housing ", "bulakan, Bulacan"),
            ProjectReconciler.key("bulakan housing", "Bulakan, Bulacan")
        )
    }

    @Test
    fun sameProjectWithDifferentUuid_adoptsRemoteUuidInsteadOfInserting() {
        val plan = ProjectReconciler.plan(
            local = listOf(local(7, "local-random")),
            remote = listOf(remote("cloud-uuid"))
        )
        assertEquals(mapOf(7L to "cloud-uuid"), plan.adoptUuid)
        assertTrue(plan.insertRemote.isEmpty())
        assertTrue(plan.uploadLocal.isEmpty())
    }

    @Test
    fun unknownRemoteProject_isInserted() {
        val plan = ProjectReconciler.plan(local = emptyList(), remote = listOf(remote("cloud-uuid")))
        assertEquals(setOf("cloud-uuid"), plan.insertRemote)
    }

    @Test
    fun localProjectMissingFromCloud_isUploaded() {
        val plan = ProjectReconciler.plan(local = listOf(local(3, "only-here")), remote = emptyList())
        assertEquals(setOf("only-here"), plan.uploadLocal)
    }

    @Test
    fun duplicateCloudDocs_collapseToOldestSoEveryPhoneAgrees() {
        val plan = ProjectReconciler.plan(
            local = listOf(local(1, "phone-a")),
            remote = listOf(remote("phone-b", createdAt = 200), remote("phone-a", createdAt = 100))
        )
        // phone-a is the oldest cloud doc and already matches: nothing to do
        assertTrue(plan.adoptUuid.isEmpty())
        assertTrue(plan.insertRemote.isEmpty())
        assertTrue(plan.uploadLocal.isEmpty())

        val other = ProjectReconciler.plan(
            local = listOf(local(9, "phone-b")),
            remote = listOf(remote("phone-b", createdAt = 200), remote("phone-a", createdAt = 100))
        )
        assertEquals(mapOf(9L to "phone-a"), other.adoptUuid)
        assertTrue(other.insertRemote.isEmpty())
    }

    @Test
    fun tombstone_deletesOnlyTheMatchingUuid() {
        val plan = ProjectReconciler.plan(
            local = listOf(local(4, "gone"), local(5, "other", name = "Other Site")),
            remote = listOf(remote("gone", deleted = true), remote("other", name = "Other Site"))
        )
        assertEquals(setOf(4L), plan.deleteLocal)
    }

    @Test
    fun tombstone_neverDeletesAProjectByNameMatch() {
        val plan = ProjectReconciler.plan(
            local = listOf(local(4, "mine")),
            remote = listOf(remote("someone-elses", deleted = true))
        )
        assertTrue(plan.deleteLocal.isEmpty())
        // the tombstone is not a live copy, so the local project still uploads
        assertEquals(setOf("mine"), plan.uploadLocal)
    }

    @Test
    fun localDuplicates_areMergedIntoLowestId() {
        val merges = ProjectReconciler.localDuplicates(
            listOf(local(10, "a"), local(4, "b"), local(12, "c", name = "Different"))
        )
        assertEquals(mapOf(10L to 4L), merges)
    }
}
