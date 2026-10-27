package com.example.ui.detail.sections

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.model.PendingDocument

/** Money › Variation orders: every order, pending too, so a new one shows; only approved ones change the cost. */
@Composable
fun VariationOrdersLog(
    documents: List<PendingDocument>,
    canAddVariationOrder: Boolean,
    onAddVariationOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voLogs = documents.filter { it.documentName.contains("Variation Order", ignoreCase = true) }
    SectionCard(
        "Variation orders (${voLogs.size})",
        Icons.Default.ReceiptLong,
        modifier,
        action = if (canAddVariationOrder) {
            {
                IconButton(onClick = onAddVariationOrder, modifier = Modifier.testTag("add_variation_button")) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Add variation order", tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else null
    ) {
        if (voLogs.isEmpty()) EmptyLine("No variation orders recorded.")
        voLogs.forEach { vo ->
            val dateApproved = Regex("\\|\\s*Date Approved:\\s*([^|]+)").find(vo.remarks)?.groupValues?.get(1)?.trim()
            val cleanRemarks = vo.remarks.replace("^Remarks:\\s*".toRegex(RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\|\\s*Date Approved:[^|]*"), "").trim()
            val displayText = if (cleanRemarks.startsWith("Reason:", ignoreCase = true)) {
                cleanRemarks
            } else if (cleanRemarks.isNotBlank()) {
                "Reason: $cleanRemarks"
            } else ""
            val type = when {
                vo.documentName.contains("Additive", ignoreCase = true) -> "Additive"
                vo.documentName.contains("Deductive", ignoreCase = true) -> "Deductive"
                else -> ""
            }
            LogEntry(
                vo.documentName,
                MaterialTheme.colorScheme.onSurface,
                listOf(if (type.isNotEmpty()) "Type: $type" else "", "Status: ${vo.status}", "Date Approved: ${dateApproved ?: "-"}", displayText)
            )
        }
    }
}
