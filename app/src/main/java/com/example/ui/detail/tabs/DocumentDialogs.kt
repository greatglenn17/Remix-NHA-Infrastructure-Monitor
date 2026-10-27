package com.example.ui.detail.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

// Add and view dialogs for related documents, used by Documents › Files; they move to the theme in v1.9.0.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRelatedDocumentDialog(
    onDismiss: () -> Unit,
    onAddDocument: (title: String, status: String, remarks: String, fileUrl: String) -> Unit
) {
    val presetDocumentTypes = listOf(
        "Notice of Award",
        "Notice to Proceed",
        "Contract Agreement",
        "Memorandum of Agreement",
        "RFA / CAF",
        "NHA Board Resolution",
        "Other / Specific Custom Document..."
    )

    var selectedPreset by remember { mutableStateOf(presetDocumentTypes.first()) }
    var showPresetDropdown by remember { mutableStateOf(false) }
    var customDocName by remember { mutableStateOf("") }
    var categoryRef by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Pending") }
    var remarksText by remember { mutableStateOf("") }
    var fileUrl by remember { mutableStateOf("") }
    var showStatusDropdown by remember { mutableStateOf(false) }

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = DarkTextPrimary,
        unfocusedTextColor = DarkTextPrimary,
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = DarkBorder,
        focusedContainerColor = DarkSurfaceVariant,
        unfocusedContainerColor = DarkSurfaceVariant,
        focusedLabelColor = Color(0xFF38BDF8),
        unfocusedLabelColor = DarkTextSecondary
    )

    val filePickerLauncher = com.example.ui.components.rememberAttachmentPicker(com.example.data.files.Attachments.Kind.SCAN) { ref ->
        fileUrl = ref
    }

    val finalDocName = if (selectedPreset == "Other / Specific Custom Document...") {
        customDocName
    } else {
        selectedPreset
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Related Project Document", style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Fixed Document Types Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedPreset,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Document Type") },
                        trailingIcon = {
                            IconButton(onClick = { showPresetDropdown = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF38BDF8))
                            }
                        },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth().clickable { showPresetDropdown = true }.testTag("dropdown_doc_type")
                    )
                    DropdownMenu(
                        expanded = showPresetDropdown,
                        onDismissRequest = { showPresetDropdown = false },
                        modifier = Modifier.background(DarkSurfaceVariant)
                    ) {
                        presetDocumentTypes.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option,
                                        color = if (option == selectedPreset) Color(0xFF38BDF8) else DarkTextPrimary,
                                        fontWeight = if (option == selectedPreset) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedPreset = option
                                    showPresetDropdown = false
                                }
                            )
                        }
                    }
                }

                // If "Other..." selected, show Custom Document Title
                if (selectedPreset == "Other / Specific Custom Document...") {
                    OutlinedTextField(
                        value = customDocName,
                        onValueChange = { customDocName = it },
                        label = { Text("Specific Document Title") },
                        placeholder = { Text("e.g. Environmental Compliance Certificate") },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth().testTag("input_related_doc_title"),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = categoryRef,
                    onValueChange = { categoryRef = it },
                    label = { Text("Reference / Serial No. / Date") },
                    placeholder = { Text("e.g. Ref: NHA-2026-001 | Date: 2026-01-15") },
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Status Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = status,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Document Status") },
                        trailingIcon = {
                            IconButton(onClick = { showStatusDropdown = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DarkTextSecondary)
                            }
                        },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth().clickable { showStatusDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showStatusDropdown,
                        onDismissRequest = { showStatusDropdown = false },
                        modifier = Modifier.background(DarkSurfaceVariant)
                    ) {
                        listOf("Approved", "Notarized", "Signed", "Submitted", "Pending").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = DarkTextPrimary) },
                                onClick = {
                                    status = option
                                    showStatusDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = remarksText,
                    onValueChange = { remarksText = it },
                    label = { Text("Remarks / Description") },
                    placeholder = { Text("Additional notes or specifications") },
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )

                // Upload Scanned Copy Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Scanned Copy Attachment",
                        style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (fileUrl.isNotBlank()) {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
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
                                        Text("Remove", color = StatusRedText, fontSize = 10.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                AsyncImage(
                                    model = fileUrl,
                                    contentDescription = "Scanned Copy Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Scanned File / Photo", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fullRemarks = buildString {
                        if (categoryRef.isNotBlank()) append(categoryRef)
                        if (remarksText.isNotBlank()) {
                            if (isNotEmpty()) append(" | ")
                            append(remarksText)
                        }
                    }
                    onAddDocument(finalDocName.ifBlank { "Project Related Document" }, status, fullRemarks, fileUrl)
                    onDismiss()
                },
                enabled = finalDocName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
            ) {
                Text("Save Document", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkTextSecondary)
            }
        }
    )
}

@Composable
fun ViewScannedCopyDialog(
    doc: PendingDocument,
    onDismiss: () -> Unit,
    onUpdateFileUrl: (PendingDocument, String) -> Unit
) {
    val filePickerLauncher = com.example.ui.components.rememberAttachmentPicker(com.example.data.files.Attachments.Kind.SCAN) { ref ->
        onUpdateFileUrl(doc, ref)
    }
    val openAttachment = com.example.ui.components.rememberAttachmentOpener()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.documentName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                        )
                        Text(
                            text = "Status: ${doc.status} | ${doc.remarks.ifBlank { "No remarks" }}",
                            style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "SCANNED COPY PREVIEW",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (doc.fileUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(DarkBackground, RoundedCornerShape(8.dp))
                            .clickable { openAttachment(doc.fileUrl) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (com.example.data.files.Attachments.isPdf(doc.fileUrl)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("PDF attached. Tap to open.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                            }
                        } else {
                            AsyncImage(
                                model = doc.fileUrl,
                                contentDescription = "Scanned Copy (tap to open)",
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(DarkBackground, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("No scanned copy attached yet.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Attach or replace scan:", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary, fontSize = 10.sp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload File from Device", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
