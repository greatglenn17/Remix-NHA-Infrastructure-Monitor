package com.example

import com.example.data.model.SdpLot
import com.example.data.model.label
import org.junit.Assert.assertEquals
import org.junit.Test

// Road lots and easements are saved under blocks RL and EA and named by their one number.
// The add-lot form itself is checked by the form_add_lot_road screenshots: its dropdown hangs Robolectric's idle checks.
class SdpAddLotTest {
    private fun lot(block: String, no: String, use: String) =
        SdpLot(projectId = 1, sdpPlanId = 1, blockNumber = block, lotNumber = no, polygonNormalizedJson = "[]", lotUse = use)

    @Test fun labels_followTheUse() {
        assertEquals("Road Lot 4", lot("RL", "4", "Road Lot").label)
        assertEquals("Easement 2", lot("EA", "2", "Easement").label)
        assertEquals("Block 3 Lot 12", lot("3", "12", "Open Space").label)
        assertEquals("Block 3 Lot 12", lot("3", "12", "").label)
        // A lot moved to another use keeps its block name.
        assertEquals("Block RL Lot 4", lot("RL", "4", "Saleable").label)
    }
}
