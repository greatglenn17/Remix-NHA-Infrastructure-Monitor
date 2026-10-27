package com.example.ui.detail.sections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PendingDocument
import com.example.data.model.Permission
import com.example.data.model.UserRole
import com.example.data.model.hasPermission
import com.example.ui.dashboard.Badge
import com.example.ui.detail.tabs.AddRelatedDocumentDialog
import com.example.ui.detail.tabs.ViewScannedCopyDialog
import com.example.ui.theme.LocalNHAColors

/** One log entry: a title and its detail lines (blank lines skipped). With [onClick] it opens that entry's status. */
@Composable
fun LogEntry(title: String, titleColor: Color, lines: List<String>, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    val body: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = titleColor)
                lines.filter { it.isNotBlank() }.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            if (onClick != null) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
    if (onClick != null) Surface(onClick = onClick, color = scheme.surfaceVariant, shape = shape, modifier = modifier) { body() }
    else Surface(color = scheme.surfaceVariant, shape = shape, modifier = modifier) { body() }
}

/** Status and remarks of a time extension, suspension, resumption or variation order; only Approved ones change dates and cost. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderStatusDialog(
    doc: PendingDocument,
    currentUserRole: UserRole,
    onUpdateStatus: (PendingDocument, String, String) -> Unit,
    onDeleteDocument: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var remarks by remember(doc.id) { mutableStateOf(doc.remarks) }
    val canApprove = currentUserRole.hasPermission(Permission.APPROVE_ORDER)
    // Only approvers set or undo Approved; others may update an order still in progress.
    val canEdit = canApprove || (currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT) && doc.status != "Approved")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(doc.documentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Pending", "Submitted", "Signed", "Notarized", "Approved").forEach { status ->
                        FilterChip(
                            selected = doc.status == status,
                            onClick = { onUpdateStatus(doc, status, keepOrderFields(doc.remarks, remarks)); onDismiss() },
                            enabled = canEdit && (canApprove || status != "Approved"),
                            label = { Text(status) },
                            modifier = Modifier.testTag("order_status_$status")
                        )
                    }
                }
                if (!canApprove) Text("Only an Engineer Admin can approve.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, enabled = canEdit, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onUpdateStatus(doc, doc.status, keepOrderFields(doc.remarks, remarks)); onDismiss() }, enabled = canEdit) { Text("Save remarks") }
        },
        dismissButton = {
            Row {
                if (canApprove) {
                    TextButton(onClick = { onDeleteDocument(doc.id); onDismiss() }) { Text("Delete", color = LocalNHAColors.current.behind) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/**
 * [edited] remarks with any "Duration/Effectivity/End/Date Approved: …" field of [original] put back
 * when the edit dropped it, so revised dates and the VO log keep reading them. A field whose value
 * was changed (its key still present) is left as edited.
 */
internal fun keepOrderFields(original: String, edited: String): String {
    val dropped = Regex("\\b(Duration|Effectivity|End|Date Approved):[^|]*", RegexOption.IGNORE_CASE).findAll(original)
        .filter { field -> !Regex("\\b${field.groupValues[1]}:", RegexOption.IGNORE_CASE).containsMatchIn(edited) }
        .map { it.value.trim() }
        .toList()
    return (dropped + edited.trim()).filter { it.isNotBlank() }.joinToString(" | ")
}

/** Documents › Related Files: related project files that are not schedule or cost orders. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelatedFilesList(
    documents: List<PendingDocument>,
    currentUserRole: UserRole,
    onAddRelatedDocument: (documentName: String, status: String, remarks: String, fileUrl: String) -> Unit,
    onUpdateDocumentFileUrl: (doc: PendingDocument, fileUrl: String) -> Unit,
    onDeleteDocument: (Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalNHAColors.current
    var showAddDocDialog by remember { mutableStateOf(false) }
    var viewingDocForScan by remember { mutableStateOf<PendingDocument?>(null) }
    var docToDelete by remember { mutableStateOf<PendingDocument?>(null) }
    val canUpload = currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT)

    val relatedFiles = documents.filter { doc ->
        !doc.documentName.contains("Time Extension", ignoreCase = true) &&
            !doc.documentName.contains("Variation Order", ignoreCase = true) &&
            !doc.documentName.contains("Work Suspension", ignoreCase = true) &&
            !doc.documentName.contains("Work Resumption", ignoreCase = true)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            SectionCard(
                "Related Files (${relatedFiles.size})",
                Icons.Default.FolderOpen,
                action = if (canUpload) {
                    {
                        TextButton(onClick = { showAddDocDialog = true }, modifier = Modifier.testTag("add_related_file_button")) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add file")
                        }
                    }
                } else null
            ) {
                if (relatedFiles.isEmpty()) EmptyLine("No related project files or scanned documents uploaded yet.")
                relatedFiles.forEachIndexed { index, doc ->
                    if (index > 0) HorizontalDivider(color = scheme.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { if (canUpload) docToDelete = doc }
                            )
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                doc.documentName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            val statusColor = when (doc.status) {
                                "Approved" -> colors.onTrack
                                "Notarized", "Signed" -> scheme.primary
                                "Submitted" -> colors.watch
                                else -> colors.behind
                            }
                            val statusContainer = when (doc.status) {
                                "Approved" -> colors.onTrackContainer
                                "Notarized", "Signed" -> scheme.primaryContainer
                                "Submitted" -> colors.watchContainer
                                else -> colors.behindContainer
                            }
                            Badge(doc.status, statusColor, statusContainer)
                            Text(
                                doc.remarks.ifBlank { "N/A" },
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        IconButton(onClick = { viewingDocForScan = doc }) {
                            if (doc.fileUrl.isNotBlank()) {
                                Icon(Icons.Default.AttachFile, contentDescription = "View Attached Copy", tint = scheme.primary)
                            } else {
                                Icon(Icons.Default.UploadFile, contentDescription = "Upload Copy", tint = colors.watch)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDocDialog) {
        AddRelatedDocumentDialog(
            onDismiss = { showAddDocDialog = false },
            onAddDocument = { name, status, remarks, fileUrl ->
                onAddRelatedDocument(name, status, remarks, fileUrl)
            }
        )
    }

    if (viewingDocForScan != null) {
        ViewScannedCopyDialog(
            doc = viewingDocForScan!!,
            onDismiss = { viewingDocForScan = null },
            onUpdateFileUrl = { doc, url ->
                onUpdateDocumentFileUrl(doc, url)
                viewingDocForScan = doc.copy(fileUrl = url)
            }
        )
    }

    if (docToDelete != null) {
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.behind) },
            title = { Text("Delete Document?") },
            text = { Text("Are you sure you want to delete \"${docToDelete?.documentName}\"? This document entry will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        docToDelete?.let { onDeleteDocument(it.id) }
                        docToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.behind, contentColor = scheme.surface)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
