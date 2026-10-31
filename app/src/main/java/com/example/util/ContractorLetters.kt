package com.example.util

import com.example.data.model.PendingDocument

/** Letters to the contractor, kept as documents named "Letter to Contractor No. N" with "Reason: … | Date Received: yyyy-MM-dd". */
object ContractorLetters {
    private const val PREFIX = "Letter to Contractor"

    fun isLetter(doc: PendingDocument) = doc.documentName.startsWith(PREFIX, ignoreCase = true)

    fun name(no: String) = "$PREFIX No. ${no.trim()}"

    fun nextNo(documents: List<PendingDocument>) =
        (documents.filter(::isLetter).mapNotNull { it.documentName.substringAfter("No. ").trim().toIntOrNull() }.maxOrNull() ?: 0) + 1

    fun remarks(reason: String, received: String) =
        listOfNotNull("Reason: ${reason.trim()}", received.trim().takeIf { it.isNotEmpty() }?.let { "Date Received: $it" }).joinToString(" | ")

    fun reason(doc: PendingDocument) = field(doc.remarks, "Reason")

    /** The date the contractor received the letter, or blank when not yet received. */
    fun received(doc: PendingDocument) = field(doc.remarks, "Date Received")

    private fun field(remarks: String, key: String) = Regex("$key: ([^|]*)").find(remarks)?.groupValues?.get(1)?.trim().orEmpty()
}
