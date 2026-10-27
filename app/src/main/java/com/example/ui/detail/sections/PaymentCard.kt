package com.example.ui.detail.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ProjectPayment
import com.example.ui.theme.LocalNHAColors
import com.example.ui.theme.TabularNumbers
import com.example.utils.CurrencyFormatter

/** One billing (disbursement voucher); replaces a row of the old 820dp payments table. */
@Composable
fun PaymentCard(
    payment: ProjectPayment,
    onViewScan: () -> Unit,
    onAttachScan: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val hasScan = payment.fileUrl.isNotBlank()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.fillMaxWidth().testTag("payment_card_${payment.id}")
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(
                        payment.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "DV ${payment.dvNo.ifBlank { "N/A" }}",
                        style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                        color = scheme.onSurfaceVariant
                    )
                }
                if (hasScan) {
                    IconButton(onClick = onViewScan) {
                        Icon(Icons.Default.AttachFile, contentDescription = "View scanned DV", tint = scheme.primary)
                    }
                }
                PaymentMenu(hasScan, onAttachScan, onDelete)
            }
            Column(modifier = Modifier.padding(end = 16.dp)) {
                Text(
                    "${payment.date.ifBlank { "-" }} · ${payment.periodCovered.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Field("Gross amount", CurrencyFormatter.formatPhp(payment.grossAmount))
                Field("Share of contract", "%.2f%%".format(payment.percentage))
                Field("Balance after", CurrencyFormatter.formatPhp(payment.balanceAmount))
                Field("Balance share", "%.2f%%".format(payment.balancePercentage))
            }
        }
    }
}

@Composable
private fun PaymentMenu(hasScan: Boolean, onAttachScan: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (hasScan) "Replace scan" else "Attach scan") },
                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                onClick = { open = false; onAttachScan() }
            )
            val red = LocalNHAColors.current.behind
            DropdownMenuItem(
                text = { Text("Delete", color = red) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = red) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}
