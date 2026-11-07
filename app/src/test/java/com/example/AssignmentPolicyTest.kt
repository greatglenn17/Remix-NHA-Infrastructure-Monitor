package com.example

import com.example.data.model.forProject
import com.example.data.model.hasPermission
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

    @Test
    fun isVisible_superAdminSeesAll_othersOnlyAssigned() {
        assertTrue(AssignmentPolicy.isVisible(true, null, "", createdByUid = ""))
        assertTrue(AssignmentPolicy.isVisible(false, "u", "[\"u\"]", createdByUid = "x"))
        assertFalse(AssignmentPolicy.isVisible(false, "u", "[\"x\"]", createdByUid = "x"))
        assertFalse(AssignmentPolicy.isVisible(false, "u", "[]", createdByUid = "x"))
        assertFalse(AssignmentPolicy.isVisible(false, "u", "", createdByUid = "x"))
        assertFalse(AssignmentPolicy.isVisible(false, null, "[\"u\"]", createdByUid = "u"))
    }

    @Test
    fun isVisible_unknownList_showsTheCreatorTheirOwnProject() {
        // After the 22 -> 23 upgrade every list is "" until the cloud answers; offline the creator still sees their projects.
        assertTrue(AssignmentPolicy.isVisible(false, "u", "", createdByUid = "u"))
        // A known list decides, even for the creator (the Super Admin may have moved them off it).
        assertFalse(AssignmentPolicy.isVisible(false, "u", "[]", createdByUid = "u"))
        assertFalse(AssignmentPolicy.isVisible(false, "u", "[\"x\"]", createdByUid = "u"))
        // No uid never matches a project without a creator.
        assertFalse(AssignmentPolicy.isVisible(false, "", "", createdByUid = ""))
        assertFalse(AssignmentPolicy.isVisible(false, null, "", createdByUid = ""))
    }

    private fun project(uids: String, staff: String) =
        com.example.data.model.Project(name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "", contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 1, dateStarted = "2026-01-01", completionDateOriginal = "2026-01-02", completionDateRevised = "2026-01-02", assignedUids = uids, assignedStaff = staff)

    @Test
    fun mergeAssignment_withoutUsers_keepsCurrentRowAssignment() {
        val edited = project("[\"old\"]", "Old Name")
        val current = project("[\"a\",\"b\"]", "A, B")
        val merged = AssignmentPolicy.mergeAssignment(edited, current, null)
        assertEquals("[\"a\",\"b\"]", merged.assignedUids)
        assertEquals("A, B", merged.assignedStaff)
    }

    @Test
    fun mergeAssignment_unknownListStaysUnknown() {
        val merged = AssignmentPolicy.mergeAssignment(project("", "X"), project("", "X"), null)
        assertEquals("", merged.assignedUids)
        assertEquals("X", merged.assignedStaff)
    }

    @Test
    fun mergeAssignment_withUsers_replacesListAndNames() {
        val users = listOf(com.example.data.model.RegisteredUser("u1", "One"), com.example.data.model.RegisteredUser("u2", "Two"))
        val merged = AssignmentPolicy.mergeAssignment(project("[\"old\"]", "Old"), project("[\"old\"]", "Old"), users)
        assertEquals("[\"u1\",\"u2\"]", merged.assignedUids)
        assertEquals("One, Two", merged.assignedStaff)
    }

    @Test
    fun superViewerAndSeeAllSwitch_listenToEveryProject_butOnlyTheSuperViewerNeverWrites() {
        assertTrue(AssignmentPolicy.seesAll("SUPER_ADMIN", false))
        assertTrue(AssignmentPolicy.seesAll("SUPER_VIEWER", false))
        assertTrue(AssignmentPolicy.seesAll("FIELD_ENGINEER", true))
        assertFalse(AssignmentPolicy.seesAll("FIELD_ENGINEER", false))
        assertFalse(AssignmentPolicy.seesAll(null, false))
        assertFalse(AssignmentPolicy.canWrite("SUPER_VIEWER"))
        assertTrue(AssignmentPolicy.canWrite("FIELD_ENGINEER"))
        assertTrue(AssignmentPolicy.isVisible(false, "u", "[\"x\"]", "x", seesAll = true))
        assertFalse(AssignmentPolicy.isVisible(false, "u", "[\"x\"]", "x"))
    }

    @Test
    fun anotherTeamsProject_opensAsViewer_ownProjectKeepsTheRole() {
        val account = com.example.data.model.UserAccount("u", "U", "T", com.example.data.model.UserRole.FIELD_ENGINEER, seesAllProjects = true)
        val theirs = project("[\"x\"]", "X")
        val mine = project("[\"u\"]", "U")
        assertEquals(com.example.data.model.UserRole.VIEWER, account.forProject(theirs, "u").role)
        assertEquals(com.example.data.model.UserRole.FIELD_ENGINEER, account.forProject(mine, "u").role)
        val admin = account.copy(role = com.example.data.model.UserRole.SUPER_ADMIN)
        assertEquals(com.example.data.model.UserRole.SUPER_ADMIN, admin.forProject(theirs, "u").role)
        // Assigned to a Super Viewer still writes nothing: the role itself has no permissions.
        val boss = account.copy(role = com.example.data.model.UserRole.SUPER_VIEWER)
        assertFalse(com.example.data.model.Permission.entries.any { boss.forProject(mine, "u").role.hasPermission(it) })
    }
}
