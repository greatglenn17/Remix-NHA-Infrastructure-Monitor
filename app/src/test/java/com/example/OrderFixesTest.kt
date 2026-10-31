package com.example

import com.example.data.model.Permission
import com.example.data.model.UserRole
import com.example.data.model.WeeklyReport
import com.example.data.model.hasPermission
import com.example.ui.detail.sections.keepOrderFields
import com.example.util.SitePhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrderFixesTest {

    @Test
    fun onlyAdminsApproveOrders() {
        assertTrue(UserRole.SUPER_ADMIN.hasPermission(Permission.APPROVE_ORDER))
        assertTrue(UserRole.ENGINEER_ADMIN.hasPermission(Permission.APPROVE_ORDER))
        assertFalse(UserRole.FIELD_ENGINEER.hasPermission(Permission.APPROVE_ORDER))
        assertFalse(UserRole.VIEWER.hasPermission(Permission.APPROVE_ORDER))
    }

    @Test
    fun keepOrderFields_putsBackDroppedScheduleFields() {
        val ws = "Duration: 10 Days | Effectivity: 2026-06-01 | End: 2026-06-11"
        assertEquals(ws, keepOrderFields(ws, ws))
        assertEquals("$ws | Typhoon", keepOrderFields(ws, "Typhoon"))
        // A changed value is kept as typed; only missing fields come back.
        assertEquals("Effectivity: 2026-06-01 | End: 2026-06-11 | Duration: 12 Days", keepOrderFields(ws, "Duration: 12 Days"))
        val vo = "Reason: Added culvert | Date Approved: 2026-06-02"
        assertEquals("Date Approved: 2026-06-02 | Reason: Bigger culvert", keepOrderFields(vo, "Reason: Bigger culvert"))
        assertEquals("Note", keepOrderFields("Reason: Rain", "Note"))
    }

    private fun report(at: Long, photos: String) = WeeklyReport(
        projectId = 1, reportingWeek = "W", daysElapsed = 0, remainingDays = 0,
        targetAccomplishmentPct = 0.0, actualAccomplishmentPct = 0.0, manpowerJson = "[]", equipmentJson = "[]",
        activitiesJson = "[]", issuesJson = "[]", accomplishmentItemsJson = "[]",
        documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = photos, submittedByStaff = "S",
        createdAtTimestamp = at
    )

    @Test
    fun latestSitePhoto_isNewestReportWithAPhoto() {
        val reports = listOf(report(100, "[\"old.jpg\"]"), report(300, "[]"), report(200, "[\"mid.jpg\",\"b.jpg\"]"))
        assertEquals("mid.jpg", SitePhotos.latest(reports))
        assertEquals("", SitePhotos.latest(listOf(report(1, "[]"))))
        assertEquals("", SitePhotos.latest(emptyList()))
    }

    @Test
    fun withoutPhoto_dropsThePhotoAndItsCaption() {
        val r = report(1, "[\"a.jpg\",\"b.jpg\"]").copy(photoDetailsJson = "{\"a.jpg\":{\"title\":\"Footing\"},\"b.jpg\":{\"title\":\"Roof\"}}")
        val out = SitePhotos.withoutPhoto(r, "a.jpg")
        assertEquals(listOf("b.jpg"), com.example.data.files.Attachments.parseList(out.attachedPhotoUrlsJson))
        assertEquals(setOf("b.jpg"), com.example.data.files.PhotoDetails.parse(out.photoDetailsJson).keys)
        assertEquals(r.id, out.id)
    }
}
