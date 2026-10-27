package com.example.ui.detail.tabs

import com.example.ui.theme.LocalNHAColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PendingDocument
import com.example.data.model.Permission
import com.example.data.model.UserRole
import com.example.data.model.hasPermission
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingDocumentsTab(
    pendingDocs: List<PendingDocument>,
    currentUserRole: UserRole,
    onAddDocumentClick: () -> Unit,
    onUpdateStatus: (PendingDocument, String, String) -> Unit,
    onUpdateFileUrl: (PendingDocument, String) -> Unit,
    onDeleteDocument: (Long) -> Unit
) {
    val pendingCount = pendingDocs.count { it.status == "Pending" }
    val submittedCount = pendingDocs.count { it.status == "Submitted" }
    val approvedCount = pendingDocs.count { it.status == "Approved" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Stats Summary
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusBadge("PENDING", pendingCount, LocalNHAColors.current.behind, LocalNHAColors.current.behindContainer)
                StatusBadge("SUBMITTED", submittedCount, LocalNHAColors.current.watch, LocalNHAColors.current.watchContainer)
                StatusBadge("APPROVED", approvedCount, LocalNHAColors.current.onTrack, LocalNHAColors.current.onTrackContainer)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "STATUS OF DOCUMENTARY REQUIREMENTS",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT)) {
            Button(
                onClick = onAddDocumentClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth().testTag("add_custom_document_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Doc Requirement", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (pendingDocs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No pending documentary requirements.", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(pendingDocs, key = { it.id }) { doc ->
                    PendingDocCard(
                        doc = doc,
                        currentUserRole = currentUserRole,
                        onUpdateStatus = onUpdateStatus,
                        onUpdateFileUrl = onUpdateFileUrl,
                        onDeleteDocument = onDeleteDocument
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatusBadge(label: String, count: Int, textColor: Color, bgColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor))
            Text("$count", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = textColor))
        }
    }
}

@Composable
private fun PendingDocCard(
    doc: PendingDocument,
    currentUserRole: UserRole,
    onUpdateStatus: (PendingDocument, String, String) -> Unit,
    onUpdateFileUrl: (PendingDocument, String) -> Unit,
    onDeleteDocument: (Long) -> Unit
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var editingRemarks by remember { mutableStateOf(doc.remarks) }
    val launcher = com.example.ui.components.rememberAttachmentPicker(com.example.data.files.Attachments.Kind.SCAN) { ref ->
        onUpdateFileUrl(doc, ref)
    }
    val openAttachment = com.example.ui.components.rememberAttachmentOpener()

    val (badgeBg, badgeText) = when (doc.status) {
        "Approved" -> Pair(LocalNHAColors.current.onTrackContainer, LocalNHAColors.current.onTrack)
        "Submitted" -> Pair(LocalNHAColors.current.watchContainer, LocalNHAColors.current.watch)
        else -> Pair(LocalNHAColors.current.behindContainer, LocalNHAColors.current.behind)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (doc.isCoreChecklist) {
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                Text("CORE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = doc.documentName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = if (doc.fileUrl.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.clickable(enabled = doc.fileUrl.isNotBlank()) {
                                openAttachment(doc.fileUrl)
                            }
                        )
                    }
                    if (doc.remarks.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remarks: ${doc.remarks}",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    // Interactive Status Pill
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showStatusDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = doc.status.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = badgeText)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Edit, contentDescription = "Edit Status", tint = badgeText, modifier = Modifier.size(12.dp))
                        }
                    }
                    
                    if (doc.status.equals("Approved", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (doc.fileUrl.isBlank()) {
                            OutlinedButton(
                                onClick = { launcher.launch("*/*") },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = "Upload", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Button(
                                onClick = { 
                                    openAttachment(doc.fileUrl)
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = "View", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    // Status Update Dialog
    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text("Update Document Status") },
            text = {
                Column {
                    Text(doc.documentName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Select Status:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Pending", "Submitted", "Signed", "Notarized", "Approved").chunked(3).forEach { rowList ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowList.forEach { st ->
                                    FilterChip(
                                        selected = doc.status == st,
                                        onClick = {
                                            onUpdateStatus(doc, st, editingRemarks)
                                            showStatusDialog = false
                                        },
                                        label = { Text(st, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editingRemarks,
                        onValueChange = { editingRemarks = it },
                        label = { Text("Remarks") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateStatus(doc, doc.status, editingRemarks)
                        showStatusDialog = false
                    }
                ) {
                    Text("Save Remarks")
                }
            },
            dismissButton = {
                if (!doc.isCoreChecklist && currentUserRole == UserRole.ENGINEER_ADMIN) {
                    TextButton(
                        onClick = {
                            onDeleteDocument(doc.id)
                            showStatusDialog = false
                        }
                    ) {
                        Text("Delete", color = LocalNHAColors.current.behind)
                    }
                } else {
                    TextButton(onClick = { showStatusDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
