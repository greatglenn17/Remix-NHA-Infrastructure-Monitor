package com.example.ui.detail.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Project
import com.example.data.model.ProjectPayment
import com.example.ui.theme.*

// Add-billing and scanned-DV dialogs, used by the Money section; they move to the theme in v1.9.0.
@Composable
fun AddPaymentDialog(
    baseCost: Double,
    currentTotalPaid: Double,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Double, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dvNo by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var periodCovered by remember { mutableStateOf(currentMonthPeriod()) }
    var amountStr by remember { mutableStateOf("") }
    var fileUrl by remember { mutableStateOf("") }

    val filePickerLauncher = com.example.ui.components.rememberAttachmentPicker(com.example.data.files.Attachments.Kind.SCAN) { ref ->
        fileUrl = ref
    }
    
    val darkTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = DarkSurfaceVariant,
        unfocusedContainerColor = DarkSurfaceVariant,
        focusedLabelColor = Color(0xFF38BDF8),
        unfocusedLabelColor = Color(0xFF94A3B8),
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = DarkBorder,
        cursorColor = Color(0xFF38BDF8)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
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
                    colors = darkTextFieldColors, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dvNo, onValueChange = { dvNo = it },
                    label = { Text("DV No. (Disbursement Voucher)") },
                    placeholder = { Text("e.g. DV-2026-08-0199") },
                    colors = darkTextFieldColors, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date, onValueChange = { date = it },
                        label = { Text("Date") },
                        colors = darkTextFieldColors, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = periodCovered, onValueChange = { periodCovered = it },
                        label = { Text("Period Covered") },
                        colors = darkTextFieldColors, modifier = Modifier.weight(1.2f)
                    )
                }
                OutlinedTextField(
                    value = amountStr, onValueChange = { amountStr = it },
                    label = { Text("Gross Amount (PHP)") },
                    placeholder = { Text("e.g. 15000000") },
                    colors = darkTextFieldColors, modifier = Modifier.fillMaxWidth()
                )

                // Scanned Copy Attachment Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, shape = RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "SCANNED COPY OF DV",
                        style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
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
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreenText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scanned Copy Attached", style = MaterialTheme.typography.labelSmall.copy(color = StatusGreenText, fontWeight = FontWeight.Bold))
                                }
                                TextButton(onClick = { fileUrl = "" }) {
                                    Text("Remove", color = StatusRedText, fontSize = 11.sp)
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
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface, contentColor = Color(0xFF38BDF8)),
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
                    val gross = amountStr.toDoubleOrNull() ?: 0.0
                    onSubmit(name, dvNo, date, periodCovered, gross, fileUrl)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                shape = RoundedCornerShape(50.dp)
            ) {
                Text("Save Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Scanned Disbursement Voucher", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("${payment.dvNo.ifBlank { "N/A" }} - ${payment.name}", style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF38BDF8)))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
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
                            .background(DarkSurfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(DarkSurfaceVariant, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No scanned copy attached yet.", style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextSecondary))
                    }
                }

                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Disbursement Voucher No:", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                            Text(payment.dvNo.ifBlank { "N/A" }, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Date:", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                            Text(payment.date, style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Period Covered:", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                            Text(payment.periodCovered, style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gross Amount Paid:", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                            Text(phpFormat.format(payment.grossAmount), style = MaterialTheme.typography.bodySmall.copy(color = StatusGreenText, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onReplaceScan,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                shape = RoundedCornerShape(50.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (payment.fileUrl.isNotBlank()) "Change Scan" else "Upload Scan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        }
    )
}

/** This month as a payment period, e.g. "October 1 - 31, 2026". */
private fun currentMonthPeriod(): String {
    val today = java.time.LocalDate.now()
    val month = today.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
    return "$month 1 - ${today.lengthOfMonth()}, ${today.year}"
}
