package com.example.util

import com.example.data.model.PendingDocument

/** Contract cost figures shared by the Overview tab, Payments tab and dashboard billing bar. */
object ContractCost {

    // A "-", "−" or opening bracket after PHP marks a deductive Variation Order.
    private val phpAmount = Regex("PHP\\s*([-−(])?\\s*([0-9,\\.]+)", RegexOption.IGNORE_CASE)

    /** Sum of the scope-of-work amounts, or [fallback] when there are none or the JSON is unreadable. */
    fun original(scopeOfWorkJson: String, fallback: Double): Double = try {
        val items = org.json.JSONArray(scopeOfWorkJson)
        var total = 0.0
        for (i in 0 until items.length()) {
            total += items.getJSONObject(i).optDouble("amount", 0.0)
        }
        if (total > 0.0) total else fallback
    } catch (e: Exception) {
        fallback
    }

    /**
     * Original cost plus the "PHP …" amount in each approved Variation Order document's name
     * (negative when marked "(Deductive)" or signed), or null when no Variation Order is approved.
     */
    fun revised(scopeOfWorkJson: String, fallback: Double, documents: List<PendingDocument>): Double? {
        val approvedVOs = documents.filter {
            it.documentName.contains("Variation Order", ignoreCase = true) &&
                it.status.equals("Approved", ignoreCase = true)
        }
        if (approvedVOs.isEmpty()) return null
        val voSum = approvedVOs.sumOf { vo ->
            val match = phpAmount.find(vo.documentName) ?: return@sumOf 0.0
            val amount = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: 0.0
            val deductive = vo.documentName.contains("Deductive", ignoreCase = true) || match.groupValues[1].isNotEmpty()
            if (deductive) -amount else amount
        }
        return original(scopeOfWorkJson, fallback) + voSum
    }

    /** The cost billings are measured against: revised when a Variation Order is approved, else original. */
    fun current(scopeOfWorkJson: String, fallback: Double, documents: List<PendingDocument>): Double =
        revised(scopeOfWorkJson, fallback, documents) ?: original(scopeOfWorkJson, fallback)
}
