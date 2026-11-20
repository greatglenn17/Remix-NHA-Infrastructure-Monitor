package com.example

import com.example.data.model.Project
import com.example.data.remote.changedProjectFields
import com.example.data.remote.fieldsToSend
import com.example.data.remote.projectEditPayload
import com.example.data.remote.withRemote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A project's cloud document both ways: what a newer cloud copy changes here, and what a phone's edit sends. */
@RunWith(RobolectricTestRunner::class)
class ProjectDocumentTest {
    private val local = Project(
        name = "P", location = "L", implementingOffice = "O", contractor = "C", scopeOfWork = "S",
        contractCostOriginal = 10_000_000.0, contractCostRevised = 10_000_000.0, contractDurationDays = 300,
        dateStarted = "2026-08-01", completionDateOriginal = "2027-05-28", completionDateRevised = "2027-05-28",
        actualAccomplishment = 40.0
    )

    @Test
    fun newerCloudCopy_changesCostAndDates_andKeepsWhatItLacks() {
        val merged = local.withRemote(mapOf(
            "name" to "P", "contractCostOriginal" to 12_000_000L, "contractDurationDays" to 360L,
            "completionDateOriginal" to "2027-07-27", "actualAccomplishment" to 55.5
        ))
        assertEquals(12_000_000.0, merged.contractCostOriginal, 0.0)
        assertEquals(360, merged.contractDurationDays)
        assertEquals("2027-07-27", merged.completionDateOriginal)
        assertEquals(55.5, merged.actualAccomplishment, 0.0)
        assertEquals("not in the document, so kept", "C", merged.contractor)
    }

    @Test
    fun anEdit_sendsOnlyWhatItChanged() {
        val signed = local.copy(checkedByName = "Engr. B")
        val payload = projectEditPayload(local.projectUuid, changedProjectFields(local, signed))
        assertEquals(setOf("checkedByName"), fieldsToSend("UPDATE", payload))
        assertNull("a new project sends everything", fieldsToSend("CREATE", payload))
        assertNull("an event from before fields sends everything", fieldsToSend("UPDATE", "{\"projectUuid\":\"x\"}"))
    }
}
