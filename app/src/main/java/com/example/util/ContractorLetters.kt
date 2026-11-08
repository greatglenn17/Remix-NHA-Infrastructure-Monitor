package com.example.util

import com.example.data.model.ContractorLetter
import com.example.data.model.PendingDocument

/**
 * Letters to the contractor. They are [ContractorLetter]s now; before v2.6 the Documents tab kept them as documents
 * named "Letter to Contractor No. N" with "Reason: … | Date Received: yyyy-MM-dd". Those still show, merged in,
 * until someone edits one (it then becomes a [ContractorLetter]) or deletes it.
 */
object ContractorLetters {
    private const val PREFIX = "Letter to Contractor"

    fun isLetter(doc: PendingDocument) = doc.documentName.startsWith(PREFIX, ignoreCase = true)

    fun reason(doc: PendingDocument) = field(doc.remarks, "Reason")

    /** The date the contractor received the letter, or blank when not yet received. */
    fun received(doc: PendingDocument) = field(doc.remarks, "Date Received")

    /**
     * An old document letter as a letter. It keeps the document's uuid and id 0, which is how saving or deleting it
     * finds the document to replace.
     */
    fun asLetter(doc: PendingDocument) = ContractorLetter(
        projectId = doc.projectId,
        dateSent = "",
        subject = doc.documentName + reason(doc).takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty(),
        dateReceived = received(doc),
        receivedFileUrl = doc.fileUrl,
        uuid = doc.uuid,
        // The document's own time, so the same document always gives the same letter.
        updatedAt = doc.updatedAt
    )

    /** [letters] and the old document letters among [documents], newest first. */
    fun merged(letters: List<ContractorLetter>, documents: List<PendingDocument>): List<ContractorLetter> =
        (letters + documents.filter(::isLetter).map(::asLetter)).sortedByDescending { it.dateSent.ifBlank { it.dateReceived } }

    private fun field(remarks: String, key: String) = Regex("$key: ([^|]*)").find(remarks)?.groupValues?.get(1)?.trim().orEmpty()
}
