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

@Composable
fun PaymentsTab(
    project: Project,
    payments: List<ProjectPayment>,
    onAddPayment: (String, String, String, String, Double, Double, Double, Double, String) -> Unit,
    onUpdatePayment: ((ProjectPayment) -> Unit)? = null,
    onDeletePayment: (ProjectPayment) -> Unit
) {
    val phpFormat = remember { java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "PH")) }
    var showAddDialog by remember { mutableStateOf(false) }
    var viewingDvPayment by remember { mutableStateOf<ProjectPayment?>(null) }
    var paymentToAttachScan by remember { mutableStateOf<ProjectPayment?>(null) }

    // Launcher for uploading scanned copy directly to an existing payment
    val singleScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && paymentToAttachScan != null && onUpdatePayment != null) {
            val updated = paymentToAttachScan!!.copy(fileUrl = uri.toString())
            onUpdatePayment(updated)
            // If viewing the dialog, update the viewed object as well
            if (viewingDvPayment?.id == updated.id) {
                viewingDvPayment = updated
            }
            paymentToAttachScan = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(
                        "PROJECT BILLINGS / PAYMENTS",
                        style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Disbursement Vouchers (DV) & Progress Payments",
                        style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Payment", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Billing", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (payments.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No payment records found.", style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextSecondary))
                    }
                }
            } else {
                item {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .widthIn(min = 820.dp)
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "NAME",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary),
                                    modifier = Modifier.weight(1.2f).padding(horizontal = 6.dp, vertical = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text(
                                    "DV NO.",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary),
                                    modifier = Modifier.weight(1.2f).padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text(
                                    "DATE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary),
                                    modifier = Modifier.weight(0.9f).padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text(
                                    "PERIOD",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary),
                                    modifier = Modifier.weight(1.1f).padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Column(
                                    modifier = Modifier.weight(1.3f).padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text("PAYMENTS MADE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary))
                                    Text("(GROSS / %)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary))
                                }
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Column(
                                    modifier = Modifier.weight(1.3f).padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text("BALANCE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary))
                                    Text("(AMT / %)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary))
                                }
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Box(
                                    modifier = Modifier.width(36.dp).padding(horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AttachFile, contentDescription = "Scanned DV Column", tint = DarkTextSecondary, modifier = Modifier.size(16.dp))
                                }
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Box(modifier = Modifier.width(32.dp)) // For delete icon
                            }
                            HorizontalDivider(modifier = Modifier.widthIn(min = 820.dp).padding(vertical = 4.dp), color = DarkBorder)
                            
                            var totalGross = 0.0
                            payments.forEach { payment ->
                                totalGross += payment.grossAmount
                                Row(
                                    modifier = Modifier
                                        .widthIn(min = 820.dp)
                                        .height(IntrinsicSize.Min)
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        payment.name,
                                        style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary, fontWeight = FontWeight.Medium),
                                        modifier = Modifier.weight(1.2f).padding(horizontal = 6.dp, vertical = 4.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                    Text(
                                        if (payment.dvNo.isNotBlank()) payment.dvNo else "N/A",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1.2f).padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                    Text(
                                        payment.date,
                                        style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary),
                                        modifier = Modifier.weight(0.9f).padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                    Text(
                                        payment.periodCovered,
                                        style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary),
                                        modifier = Modifier.weight(1.1f).padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                    Column(
                                        modifier = Modifier.weight(1.3f).padding(horizontal = 6.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(com.example.utils.CurrencyFormatter.formatPhp(payment.grossAmount), style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary))
                                        Text("%.2f%%".format(payment.percentage), style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                                    }
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                    Column(
                                        modifier = Modifier.weight(1.3f).padding(horizontal = 6.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(com.example.utils.CurrencyFormatter.formatPhp(payment.balanceAmount), style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary))
                                        Text("%.2f%%".format(payment.balancePercentage), style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                                    }
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                    
                                    // Scanned DV Column (Icon Only - Fixed Width)
                                    Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                                        if (payment.fileUrl.isNotBlank()) {
                                            IconButton(
                                                onClick = { viewingDvPayment = payment },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Visibility,
                                                            contentDescription = "View Scanned DV",
                                                            tint = Color(0xFF38BDF8),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    paymentToAttachScan = payment
                                                    singleScanLauncher.launch("image/*")
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.UploadFile,
                                                            contentDescription = "Upload Scanned DV",
                                                            tint = Color(0xFFF59E0B),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())

                                    IconButton(onClick = { onDeletePayment(payment) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRedText, modifier = Modifier.size(16.dp))
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.widthIn(min = 820.dp), color = DarkBorder)
                            }

                            val totalPct = if (project.contractCostRevised > 0) (totalGross / project.contractCostRevised) * 100.0 else 0.0
                            val finalBalAmt = project.contractCostRevised - totalGross
                            val finalBalPct = if (project.contractCostRevised > 0) (finalBalAmt / project.contractCostRevised) * 100.0 else 0.0

                            Row(
                                modifier = Modifier
                                    .widthIn(min = 820.dp)
                                    .height(IntrinsicSize.Min)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "GRAND TOTAL",
                                    style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(4.4f).padding(horizontal = 6.dp)
                                )
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Column(
                                    modifier = Modifier.weight(1.3f).padding(horizontal = 6.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(phpFormat.format(totalGross), style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold))
                                    Text("%.2f%%".format(totalPct), style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary, fontWeight = FontWeight.Bold))
                                }
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Column(
                                    modifier = Modifier.weight(1.3f).padding(horizontal = 6.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(phpFormat.format(finalBalAmt), style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold))
                                    Text("%.2f%%".format(finalBalPct), style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary, fontWeight = FontWeight.Bold))
                                }
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Spacer(modifier = Modifier.width(36.dp))
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Spacer(modifier = Modifier.width(32.dp))
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Payment", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Billing", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = Color(0xFF38BDF8),
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Payment")
        }
    }

    if (showAddDialog) {
        AddPaymentDialog(
            baseCost = project.contractCostRevised,
            currentTotalPaid = payments.sumOf { it.grossAmount },
            onDismiss = { showAddDialog = false },
            onSubmit = { name, dvNo, date, period, gross, fileUrl ->
                val pct = if (project.contractCostRevised > 0) (gross / project.contractCostRevised) * 100 else 0.0
                val newBal = project.contractCostRevised - (payments.sumOf { it.grossAmount } + gross)
                val balPct = if (project.contractCostRevised > 0) (newBal / project.contractCostRevised) * 100 else 0.0
                onAddPayment(name, dvNo, date, period, gross, pct, newBal, balPct, fileUrl)
                showAddDialog = false
            }
        )
    }

    if (viewingDvPayment != null) {
        ViewDvScanDialog(
            payment = viewingDvPayment!!,
            onDismiss = { viewingDvPayment = null },
            onReplaceScan = {
                paymentToAttachScan = viewingDvPayment
                singleScanLauncher.launch("image/*")
            }
        )
    }
}

@Composable
fun AddPaymentDialog(
    baseCost: Double,
    currentTotalPaid: Double,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Double, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dvNo by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("2026-08-31") }
    var periodCovered by remember { mutableStateOf("August 1 - 31, 2026") }
    var amountStr by remember { mutableStateOf("") }
    var fileUrl by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            fileUrl = uri.toString()
        }
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

    val sampleDvScans = listOf(
        "Sample DV #1" to "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=800&q=80",
        "Sample DV #2" to "https://images.unsplash.com/photo-1450133064473-71024230f91b?auto=format&fit=crop&w=800&q=80"
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

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Or select reference scanned voucher copy:", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary, fontSize = 10.sp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            sampleDvScans.forEach { (label, url) ->
                                FilterChip(
                                    selected = (fileUrl == url),
                                    onClick = { fileUrl = url },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFF38BDF8)
                                    )
                                )
                            }
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
