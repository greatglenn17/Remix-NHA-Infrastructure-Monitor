package com.example

import com.example.data.files.PhotoDetail
import com.example.data.files.PhotoDetails
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: PhotoDetails keeps its JSON with org.json.
@RunWith(RobolectricTestRunner::class)
class PhotoDetailsTest {

    @Test fun detailsSurviveJsonRoundTrip() {
        val details = mapOf(
            "nhafile:p/a.jpg" to PhotoDetail("Footing, Block 3", "Oct 26, 2026 9:15 AM", 14.5995, 120.9842),
            "nhafile:p/b.jpg" to PhotoDetail(title = "No location")
        )
        assertEquals(details, PhotoDetails.parse(PhotoDetails.toJson(details)))
        assertEquals(emptyMap<String, PhotoDetail>(), PhotoDetails.parse("not json"))
    }

    @Test fun stampHasTimeAndGpsWhenKnown() {
        assertEquals(
            listOf("Oct 26, 2026 9:15 AM", "GPS 14.599500, 120.984200"),
            PhotoDetail(takenAt = "Oct 26, 2026 9:15 AM", lat = 14.5995, lng = 120.9842).stamp
        )
        assertEquals(emptyList<String>(), PhotoDetail(title = "x").stamp)
    }
}
