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

/** Documents › Schedule orders: approved or submitted time extensions, suspensions and resumptions. */
@Composable
fun ScheduleOrdersList(documents: List<PendingDocument>) {
    fun logs(type: String) = documents.filter {
        it.documentName.contains(type, ignoreCase = true) &&
            (it.status.equals("Approved", ignoreCase = true) || it.status.equals("Submitted", ignoreCase = true))
    }
    val colors = LocalNHAColors.current
    val teLogs = logs("Time Extension")
    val wsLogs = logs("Work Suspension")
    val wrLogs = logs("Work Resumption")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item {
            SectionCard("Time extensions (${teLogs.size})", Icons.Default.MoreTime) {
                if (teLogs.isEmpty()) EmptyLine("No time extensions recorded.")
                teLogs.forEach { ext ->
                    val cleanRemarks = ext.remarks.replace("^Remarks:\\s*".toRegex(RegexOption.IGNORE_CASE), "").trim()
                    val displayText = if (cleanRemarks.startsWith("Reason:", ignoreCase = true)) {
                        cleanRemarks
                    } else if (cleanRemarks.isNotBlank()) {
                        "Reason: $cleanRemarks"
                    } else ""
                    LogEntry(ext.documentName, MaterialTheme.colorScheme.onSurface, listOf("Status: ${ext.status}", displayText))
                }
            }
        }
        item {
            SectionCard("Work suspensions (${wsLogs.size})", Icons.Default.PauseCircle) {
                if (wsLogs.isEmpty()) EmptyLine("No work suspensions recorded.")
                wsLogs.forEach { so ->
                    val remarksText = so.remarks
                    val durMatch = Regex("Duration:\\s*([^|\\n]+)").find(remarksText)?.groupValues?.get(1)?.trim() ?: "7 Days"
                    val effMatch = Regex("Effectivity:\\s*([^|\\n]+)").find(remarksText)?.groupValues?.get(1)?.trim() ?: "2026-05-18"
                    var endMatch = Regex("(?:End|End Date):\\s*([^|\\n]+)").find(remarksText)?.groupValues?.get(1)?.trim()
                    if (endMatch.isNullOrBlank()) {
                        try {
                            val days = Regex("\\d+").find(durMatch)?.value?.toIntOrNull() ?: 7
                            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            val parsedDate = format.parse(effMatch)
                            if (parsedDate != null) {
                                val cal = java.util.Calendar.getInstance()
                                cal.time = parsedDate
                                cal.add(java.util.Calendar.DAY_OF_YEAR, days)
                                endMatch = format.format(cal.time)
                            }
                        } catch (e: Exception) { }
                    }
                    val finalEndDate = endMatch ?: "2026-05-25"
                    LogEntry(
                        so.documentName, colors.watch,
                        listOf("Status: ${so.status}", "Duration: $durMatch", "Effectivity Date: $effMatch", "End Date: $finalEndDate")
                    )
                }
            }
        }
        item {
            SectionCard("Work resumptions (${wrLogs.size})", Icons.Default.PlayCircle) {
                if (wrLogs.isEmpty()) EmptyLine("No work resumptions recorded.")
                wrLogs.forEach { log ->
                    val remarksText = log.remarks
                    val effMatch = Regex("Effectivity:\\s*([^|\\n]+)").find(remarksText)?.groupValues?.get(1)?.trim() ?: "2026-05-25"
                    var reasonMatch = Regex("(?:Remarks|Reason):\\s*([^|\\n]+)").find(remarksText.replace("Effectivity:\\s*([^|\\n]+)".toRegex(), ""))?.groupValues?.get(1)?.trim()
                    if (reasonMatch.isNullOrBlank()) {
                        reasonMatch = "Weather condition cleared"
                    }
                    LogEntry(log.documentName, colors.onTrack, listOf("Status: ${log.status}", "Effectivity Date: $effMatch", "Reason: $reasonMatch"))
                }
            }
        }
    }
}

/** One log entry: a title and its detail lines (blank lines skipped). */
@Composable
fun LogEntry(title: String, titleColor: Color, lines: List<String>) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = titleColor)
            lines.filter { it.isNotBlank() }.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Documents › Files: related project files that are not schedule or cost orders. */
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
                "Related files (${relatedFiles.size})",
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
                    if (index > 0) HorizontalDivider(color = scheme.outline)
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
