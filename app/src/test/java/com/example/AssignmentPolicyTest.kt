package com.example

import com.example.data.remote.AssignmentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AssignmentPolicyTest {

    private fun remove(
        isSuperAdmin: Boolean = false,
        localAssignedUids: String = "[\"x\"]",
        inServerSnapshot: Boolean = false,
        hasPendingUpload: Boolean = false,
        createdByMe: Boolean = false
    ) = AssignmentPolicy.shouldRemoveLocally(isSuperAdmin, "u", localAssignedUids, inServerSnapshot, hasPendingUpload, createdByMe)

    @Test
    fun unassignedProjectMissingFromServer_isRemoved() {
        assertTrue(remove())
    }

    @Test
    fun keepsProjectsWithLocalWorkOrStillAssigned() {
        assertFalse(remove(hasPendingUpload = true))
        assertFalse(remove(createdByMe = true, localAssignedUids = ""))
        assertFalse(remove(isSuperAdmin = true))
        assertFalse(remove(inServerSnapshot = true))
        assertFalse(remove(localAssignedUids = "[\"u\"]"))
    }

    @Test
    fun documentWithoutList_isUnknown_soTheProjectIsKept() {
        // handleMissingProject treats a null list as Probe.Unknown and returns without removing.
        assertNull(AssignmentPolicy.storedList(hasField = false, raw = null))
        assertNull(AssignmentPolicy.storedList(hasField = true, raw = "not a list"))
        assertEquals("[]", AssignmentPolicy.storedList(hasField = true, raw = emptyList<String>()))
        assertEquals("[\"a\"]", AssignmentPolicy.storedList(hasField = true, raw = listOf("a")))
    }

    @Test
    fun recordUpload_waitsForUnknownList_butSendsExplicitEmptyList() {
        assertNull(AssignmentPolicy.recordUploadList(""))
        assertEquals(emptyList<String>(), AssignmentPolicy.recordUploadList("[]"))
        assertEquals(listOf("a"), AssignmentPolicy.recordUploadList("[\"a\"]"))
    }

    @Test
    fun onlyViewersCannotWrite() {
        assertFalse(AssignmentPolicy.canWrite("VIEWER"))
        assertTrue(AssignmentPolicy.canWrite("FIELD_ENGINEER"))
        assertTrue(AssignmentPolicy.canWrite(null))
    }

    @Test
    fun projectUploadFields_followRole() {
        assertNull(AssignmentPolicy.projectUploadFields(isSuperAdmin = false, exists = true, myUid = "u", localAssignedUids = "[\"u\"]"))
        assertEquals(listOf("u"), AssignmentPolicy.projectUploadFields(isSuperAdmin = false, exists = false, myUid = "u", localAssignedUids = ""))
        assertEquals(listOf("b"), AssignmentPolicy.projectUploadFields(isSuperAdmin = true, exists = true, myUid = "a", localAssignedUids = "[\"b\"]"))
        // A list this phone never received is left out, so it can't wipe the cloud's.
        assertNull(AssignmentPolicy.projectUploadFields(isSuperAdmin = true, exists = true, myUid = "a", localAssignedUids = ""))
    }
}
