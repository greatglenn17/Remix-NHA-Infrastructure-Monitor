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

    private fun remote(
        uuid: String, createdAt: Long = 1L, deleted: Boolean = false, name: String = "Bulakan Housing", location: String = "Bulakan, Bulacan",
        serverCreatedAt: Long = Long.MAX_VALUE
    ) = RemoteRef(uuid, ProjectReconciler.key(name, location), createdAt, deleted, serverCreatedAt)

    /** A copy uploaded with createdAt = 0 is still the newer one by the server's own time. */
    @Test
    fun oldestIsByTheServersTime_notTheDateTheCreatorSent() {
        val docs = listOf(
            remote("real", createdAt = 1_700_000_000_000, serverCreatedAt = 100),
            remote("planted", createdAt = 0, serverCreatedAt = 200),
            remote("not-stamped-yet", createdAt = 0)
        )
        assertEquals(setOf("real"), ProjectReconciler.plan(local = emptyList(), remote = docs).insertRemote)
        assertEquals(mapOf(9L to "real"), ProjectReconciler.plan(local = listOf(local(9, "restored")), remote = docs).adoptUuid)
    }

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
    fun duplicateCloudDocs_aPhoneKeepsItsOwnLiveDocument() {
        val remote = listOf(remote("phone-b", createdAt = 200), remote("phone-a", createdAt = 100))
        for (uuid in listOf("phone-a", "phone-b")) {
            val plan = ProjectReconciler.plan(local = listOf(local(1, uuid)), remote = remote)
            // No second row for the other document either.
            assertEquals(ProjectReconciler.Plan(emptyMap(), emptySet(), emptySet(), emptySet()), plan)
        }
    }

    /** Anyone who may create a project can upload one with a victim project's name and createdAt = 0. */
    @Test
    fun olderDocWithTheSameName_neverTakesOverAProjectAlreadyInTheCloud() {
        val plan = ProjectReconciler.plan(
            local = listOf(local(7, "real")),
            remote = listOf(remote("real", createdAt = 1_700_000_000_000), remote("planted", createdAt = 0))
        )
        assertEquals(ProjectReconciler.Plan(emptyMap(), emptySet(), emptySet(), emptySet()), plan)
    }

    @Test
    fun rowWithoutACloudDoc_adoptsTheOldestOfItsName() {
        val plan = ProjectReconciler.plan(
            local = listOf(local(9, "restored")),
            remote = listOf(remote("phone-b", createdAt = 200), remote("phone-a", createdAt = 100))
        )
        assertEquals(mapOf(9L to "phone-a"), plan.adoptUuid)
        assertTrue(plan.insertRemote.isEmpty())
        assertTrue(plan.uploadLocal.isEmpty())
    }

    @Test
    fun rowWithoutACloudDoc_isLeftForTheMergeWhenAnotherRowHasThatProject() {
        val plan = ProjectReconciler.plan(
            local = listOf(local(1, "phone-b"), local(2, "restored")),
            remote = listOf(remote("phone-b", createdAt = 200), remote("phone-a", createdAt = 100))
        )
        assertEquals(ProjectReconciler.Plan(emptyMap(), emptySet(), emptySet(), emptySet()), plan)
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
            listOf(local(10, "a"), local(4, "b"), local(12, "c", name = "Different")),
            liveUuids = emptySet()
        )
        assertEquals(mapOf(10L to 4L), merges)
    }

    /** Anyone assigned to a project can rename it to another project's name and location. */
    @Test
    fun localDuplicates_twoProjectsInTheCloudAreNeverMerged() {
        val rows = listOf(local(4, "renamed"), local(10, "real"))
        assertTrue(ProjectReconciler.localDuplicates(rows, liveUuids = setOf("renamed", "real")).isEmpty())
    }

    @Test
    fun localDuplicates_aRowNotInTheCloudJoinsTheOneThatIs() {
        val rows = listOf(local(4, "restored"), local(10, "cloud"), local(11, "restored-too"))
        assertEquals(mapOf(4L to 10L, 11L to 10L), ProjectReconciler.localDuplicates(rows, liveUuids = setOf("cloud")))
    }
}
