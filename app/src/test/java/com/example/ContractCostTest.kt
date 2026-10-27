package com.example

import com.example.data.model.PendingDocument
import com.example.util.ContractCost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: org.json is only a stub on the plain JVM.
@RunWith(RobolectricTestRunner::class)
class ContractCostTest {

    private val scope = """[{"amount": 600000}, {"amount": 400000.5}]"""

    private fun doc(name: String, status: String = "Approved") =
        PendingDocument(projectId = 1, documentName = name, status = status)

    @Test
    fun original_sumsScopeOfWorkAmounts() {
        assertEquals(1_000_000.5, ContractCost.original(scope, 5.0), 0.001)
    }

    @Test
    fun original_fallsBackWhenScopeIsEmptyOrInvalid() {
        assertEquals(5.0, ContractCost.original("[]", 5.0), 0.001)
        assertEquals(5.0, ContractCost.original("Road works", 5.0), 0.001)
    }

    @Test
    fun revised_isNullWithoutApprovedVariationOrder() {
        val docs = listOf(doc("Variation Order No. 1 PHP 50,000", status = "Pending"), doc("Time Extension No. 1"))
        assertNull(ContractCost.revised(scope, 5.0, docs))
    }

    @Test
    fun revised_addsApprovedVariationOrderAmounts() {
        val docs = listOf(
            doc("Variation Order No. 1 - PHP 50,000.00"),
            doc("variation order no. 2 php 20000"),
            doc("Variation Order No. 3 (no amount)")
        )
        assertEquals(1_070_000.5, ContractCost.revised(scope, 5.0, docs)!!, 0.001)
    }

    @Test
    fun revised_subtractsDeductiveVariationOrders() {
        val docs = listOf(
            doc("Variation Order No. 1 - PHP 50,000"),
            doc("Variation Order No. 2 - PHP -20,000"),
            doc("Variation Order No. 3 - PHP − 5,000.50"),
            doc("Variation Order No. 4 - PHP (10,000)")
        )
        assertEquals(1_015_000.0, ContractCost.revised(scope, 5.0, docs)!!, 0.001)
    }

    @Test
    fun revised_usesAdditiveDeductiveTypeFromDialog() {
        val docs = listOf(
            doc("Variation Order No. 1 (Additive) - PHP 50,000"),
            doc("Variation Order No. 2 (Deductive) - PHP 20,000"),
            // A minus typed as well must not flip it back to additive.
            doc("Variation Order No. 3 (Deductive) - PHP -5,000")
        )
        assertEquals(1_025_000.5, ContractCost.revised(scope, 5.0, docs)!!, 0.001)
    }

    @Test
    fun current_prefersRevisedOverOriginal() {
        assertEquals(1_000_000.5, ContractCost.current(scope, 5.0, emptyList()), 0.001)
        assertEquals(1_050_000.5, ContractCost.current(scope, 5.0, listOf(doc("Variation Order PHP 50,000"))), 0.001)
    }

    @Test
    fun voName_withSpacedAmount_readsBackWhole() {
        val name = com.example.ui.dialogs.voDocumentName("1", "Additive", "15 000 000")
        assertEquals(1_000_000.5 + 15_000_000.0, ContractCost.revised(scope, 5.0, listOf(doc(name)))!!, 0.001)
    }
}
