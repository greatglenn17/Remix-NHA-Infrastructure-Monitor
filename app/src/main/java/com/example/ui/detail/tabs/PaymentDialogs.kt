package com.example.ui.detail.tabs

import com.example.ui.components.zoomable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.HOUSING_BILLING_TYPES
import com.example.data.model.ProjectPayment
import com.example.data.model.billingUnitName
import com.example.ui.components.DateField
import com.example.ui.components.rememberDiscardGuard
import com.example.ui.theme.LocalNHAColors
import com.example.ui.detail.sections.Field
import com.example.util.FormValidation
import com.example.utils.CurrencyFormatter

// Add-billing and scanned-DV dialogs, used by the Money section.
/** Adds a billing, or with [existing] edits every field of that one. Balances are left to the caller. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddPaymentDialog(
    baseCost: Double,
    currentTotalPaid: Double,
    onDismiss: () -> Unit,
    onSubmit: (ProjectPayment) -> Unit,
    today: java.time.LocalDate = java.time.LocalDate.now(),
    existing: ProjectPayment? = null,
    // Housing projects bill land per lot and M1/M2 per housing unit.
    isHousing: Boolean = false,
    // Earlier billings; the latest of a type prefills its amount per lot or unit.
    previous: List<ProjectPayment> = emptyList()
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dvNo by remember { mutableStateOf(existing?.dvNo ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: today.toString()) }
    var periodCovered by remember { mutableStateOf(existing?.periodCovered ?: currentMonthPeriod(today)) }
    var amountStr by remember { mutableStateOf(existing?.let { "%.2f".format(java.util.Locale.US, it.grossAmount) } ?: "") }
    var fileUrl by remember { mutableStateOf(existing?.fileUrl ?: "") }
    var type by remember { mutableStateOf(existing?.billingType ?: "") }
    var unitsStr by remember { mutableStateOf(existing?.units?.takeIf { it > 0 }?.toString() ?: "") }
    var priceStr by remember { mutableStateOf(existing?.unitPrice?.takeIf { it > 0 }?.let { "%.2f".format(java.util.Locale.US, it) } ?: "") }
    var retention by remember { mutableStateOf(existing?.retentionPct ?: 0.0) }

    val attachDv = rememberDvAttacher { ref -> fileUrl = ref }

    val nha = LocalNHAColors.current
    val byUnit = type.isNotBlank()
    val units = unitsStr.trim().toIntOrNull()?.takeIf { it > 0 }
    val amountErr = if (byUnit) null else FormValidation.amountError(amountStr, true)
    val unitsErr = if (!byUnit) null else if (unitsStr.isBlank()) "Required" else if (units == null) "Enter a whole number" else null
    val priceErr = if (byUnit) FormValidation.amountError(priceStr, true) else null
    val gross = if (byUnit) (units ?: 0) * (FormValidation.parseAmount(priceStr) ?: 0.0) else FormValidation.parseAmount(amountStr) ?: 0.0
    val snapshot = { listOf(name, dvNo, date, periodCovered, amountStr, fileUrl, type, unitsStr, priceStr, retention.toString()) }
    val initial = remember { snapshot() }
    val close = rememberDiscardGuard(snapshot() != initial, onDismiss)

    AlertDialog(
        onDismissRequest = close,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (existing == null) "Add Billing / Disbursement Voucher" else "Edit Billing")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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
                if (isHousing || byUnit) {
                    Text("Billed for", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (HOUSING_BILLING_TYPES + "").forEach { t ->
                            FilterChip(
                                selected = t == type,
                                onClick = {
                                    if (t != type) {
                                        type = t
                                        priceStr = previous.filter { it.billingType == t && it.unitPrice > 0 && it.id != existing?.id }
                                            .maxByOrNull { it.id }?.let { "%.2f".format(java.util.Locale.US, it.unitPrice) } ?: ""
                                    }
                                },
                                label = { Text(t.ifBlank { "Other" }) },
                                modifier = Modifier.testTag("billing_type_${t.ifBlank { "Other" }}")
                            )
                        }
                    }
                }
                if (byUnit) {
                    val unit = billingUnitName(type)
                    OutlinedTextField(
                        value = unitsStr, onValueChange = { unitsStr = it },
                        label = { Text("Number of ${unit}s") },
                        isError = unitsErr != null,
                        supportingText = unitsErr?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = priceStr, onValueChange = { priceStr = it },
                        label = { Text("Amount per $unit (PHP)") },
                        isError = priceErr != null,
                        supportingText = priceErr?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
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
                }
                Text("Retention", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.0, 5.0, 10.0).forEach { r ->
                        FilterChip(
                            selected = r == retention,
                            onClick = { retention = r },
                            label = { Text(if (r == 0.0) "None" else "${r.toInt()}%") },
                            modifier = Modifier.testTag("retention_${r.toInt()}")
                        )
                    }
                }
                if (gross > 0) Column {
                    Field("Gross amount", CurrencyFormatter.formatPhp(gross))
                    if (retention > 0) Field("Retention (${retention.toInt()}%)", CurrencyFormatter.formatPhp(gross * retention / 100))
                    Field("Net amount", CurrencyFormatter.formatPhp(gross * (1 - retention / 100)))
                }

                // Scanned Copy Attachment Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp))
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
                                    Text("Remove", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
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
                        DvSourceButtons(attachDv)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = existing ?: ProjectPayment(projectId = 0, name = "", date = "", periodCovered = "", grossAmount = 0.0, percentage = 0.0, balanceAmount = 0.0, balancePercentage = 0.0)
                    onSubmit(base.copy(
                        name = name.ifBlank { type }, dvNo = dvNo, date = date, periodCovered = periodCovered, grossAmount = gross, fileUrl = fileUrl,
                        billingType = type, units = if (byUnit) units ?: 0 else 0,
                        unitPrice = if (byUnit) FormValidation.parseAmount(priceStr) ?: 0.0 else 0.0, retentionPct = retention
                    ))
                },
                enabled = amountErr == null && unitsErr == null && priceErr == null,
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(if (existing == null) "Save Payment" else "Save changes", fontWeight = FontWeight.Bold)
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
    // Null for roles that cannot manage payments.
    onReplaceScan: ((DvSource) -> Unit)?
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.surfaceVariant)
                            .zoomable(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(cs.surfaceVariant, shape = RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No scanned copy attached yet.", style = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurfaceVariant))
                    }
                }

                Surface(
                    color = cs.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
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
                onReplaceScan?.let { replace ->
                    Text(if (payment.fileUrl.isNotBlank()) "Change the DV copy" else "Attach the DV copy", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    DvSourceButtons(replace)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** This month as a payment period, e.g. "October 1 - 31, 2026". */
private fun currentMonthPeriod(today: java.time.LocalDate): String {
    val month = today.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
    return "$month 1 - ${today.lengthOfMonth()}, ${today.year}"
}

/** Where a DV copy comes from: Google's document scanner, a camera photo, or a file already on the phone. */
enum class DvSource { SCAN, PHOTO, FILE }

/** One launcher for the three [DvSource]s; each hands back the attached file's reference. */
@Composable
fun rememberDvAttacher(onAttached: (ref: String) -> Unit): (DvSource) -> Unit {
    val scan = com.example.ui.components.rememberDocumentScanner(onAttached)
    val photo = com.example.ui.components.rememberAttachmentCamera(com.example.data.files.Attachments.Kind.SCAN, onAttached = onAttached)
    val file = com.example.ui.components.rememberAttachmentPicker(com.example.data.files.Attachments.Kind.SCAN, onAttached = onAttached)
    return { source ->
        when (source) {
            DvSource.SCAN -> scan()
            DvSource.PHOTO -> photo()
            DvSource.FILE -> file.launch("image/*")
        }
    }
}

@Composable
fun DvSourceButtons(onPick: (DvSource) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple(DvSource.SCAN, "Scan DV", Icons.Default.DocumentScanner),
            Triple(DvSource.PHOTO, "Take photo", Icons.Default.CameraAlt),
            Triple(DvSource.FILE, "Upload", Icons.Default.UploadFile)
        ).forEach { (source, label, icon) ->
            OutlinedButton(shape = MaterialTheme.shapes.small, 
                onClick = { onPick(source) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}
