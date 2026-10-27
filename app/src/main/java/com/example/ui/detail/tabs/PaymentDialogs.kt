package com.example.ui.detail.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ProjectPayment
import com.example.ui.components.DateField
import com.example.ui.components.rememberDiscardGuard
import com.example.ui.theme.LocalNHAColors
import com.example.util.FormValidation

// Add-billing and scanned-DV dialogs, used by the Money section.
@Composable
fun AddPaymentDialog(
    baseCost: Double,
    currentTotalPaid: Double,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Double, String) -> Unit,
    today: java.time.LocalDate = java.time.LocalDate.now()
) {
    var name by remember { mutableStateOf("") }
    var dvNo by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today.toString()) }
    var periodCovered by remember { mutableStateOf(currentMonthPeriod(today)) }
    var amountStr by remember { mutableStateOf("") }
    var fileUrl by remember { mutableStateOf("") }

    val filePickerLauncher = com.example.ui.components.rememberAttachmentPicker(com.example.data.files.Attachments.Kind.SCAN) { ref ->
        fileUrl = ref
    }

    val nha = LocalNHAColors.current
    val amountErr = FormValidation.amountError(amountStr, true)
    val snapshot = { listOf(name, dvNo, date, periodCovered, amountStr, fileUrl) }
    val initial = remember { snapshot() }
    val close = rememberDiscardGuard(snapshot() != initial, onDismiss)

    AlertDialog(
        onDismissRequest = close,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Billing / Disbursement Voucher")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Billing Name") },
                    placeholder = { Text("e.g. 3rd Progress Billing") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dvNo, onValueChange = { dvNo = it },
                    label = { Text("DV No. (Disbursement Voucher)") },
                    placeholder = { Text("e.g. DV-2026-08-0199") },
                    modifier = Modifier.fillMaxWidth()
                )
                DateField(label = "Date", value = date, onValueChange = { date = it })
                OutlinedTextField(
                    value = periodCovered, onValueChange = { periodCovered = it },
                    label = { Text("Period Covered") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr, onValueChange = { amountStr = it },
                    label = { Text("Gross Amount (PHP)") },
                    placeholder = { Text("e.g. 15000000") },
                    isError = amountErr != null,
                    supportingText = amountErr?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Scanned Copy Attachment Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "SCANNED COPY OF DV",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (fileUrl.isNotBlank()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = nha.onTrack, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scanned Copy Attached", style = MaterialTheme.typography.labelSmall.copy(color = nha.onTrack, fontWeight = FontWeight.Bold))
                                }
                                TextButton(onClick = { fileUrl = "" }) {
                                    Text("Remove", color = nha.behind, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            AsyncImage(
                                model = fileUrl,
                                contentDescription = "DV Copy Preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Button(
                            onClick = { filePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Scanned DV (Image / File)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val gross = FormValidation.parseAmount(amountStr) ?: 0.0
                    onSubmit(name, dvNo, date, periodCovered, gross, fileUrl)
                },
                enabled = amountErr == null,
                shape = RoundedCornerShape(50.dp)
            ) {
                Text("Save Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = close) { Text("Cancel") }
        }
    )
}

@Composable
fun ViewDvScanDialog(
    payment: ProjectPayment,
    onDismiss: () -> Unit,
    onReplaceScan: () -> Unit
) {
    val phpFormat = remember { java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "PH")) }
    val cs = MaterialTheme.colorScheme
    val nha = LocalNHAColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Scanned Disbursement Voucher", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("${payment.dvNo.ifBlank { "N/A" }} - ${payment.name}", style = MaterialTheme.typography.labelMedium.copy(color = cs.primary))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = cs.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (payment.fileUrl.isNotBlank()) {
                    AsyncImage(
                        model = payment.fileUrl,
                        contentDescription = "Scanned DV Copy",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cs.surfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(cs.surfaceVariant, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No scanned copy attached yet.", style = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurfaceVariant))
                    }
                }

                Surface(
                    color = cs.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Disbursement Voucher No:", style = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant))
                            Text(payment.dvNo.ifBlank { "N/A" }, style = MaterialTheme.typography.bodySmall.copy(color = cs.primary, fontWeight = FontWeight.Bold))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Date:", style = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant))
                            Text(payment.date, style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurface))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Period Covered:", style = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant))
                            Text(payment.periodCovered, style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurface))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gross Amount Paid:", style = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant))
                            Text(phpFormat.format(payment.grossAmount), style = MaterialTheme.typography.bodySmall.copy(color = nha.onTrack, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onReplaceScan,
                shape = RoundedCornerShape(50.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (payment.fileUrl.isNotBlank()) "Change Scan" else "Upload Scan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/** This month as a payment period, e.g. "October 1 - 31, 2026". */
private fun currentMonthPeriod(today: java.time.LocalDate): String {
    val month = today.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
    return "$month 1 - ${today.lengthOfMonth()}, ${today.year}"
}
