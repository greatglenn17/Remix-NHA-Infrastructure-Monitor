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
import com.example.ui.detail.SectionChips
import com.example.ui.theme.LocalNHAColors
import com.example.util.ContractorLetters
import com.example.util.ProjectSchedule

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
    onAddContinuance: (() -> Unit)? = null,
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
                Text(com.example.util.ProjectSchedule.orderLabel(doc.documentName), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
                onAddContinuance?.let { add ->
                    OutlinedButton(onClick = { onDismiss(); add() }, modifier = Modifier.fillMaxWidth().testTag("add_continuance_button")) {
                        Text("Add Continuance of Work Suspension")
                    }
                }
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
            !doc.documentName.contains("Work Resumption", ignoreCase = true) &&
            !ContractorLetters.isLetter(doc)
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

/** The status dialog for [order] and, from a suspension, the dialog that adds its next continuance. */
@Composable
fun OrderDialogs(
    order: PendingDocument?,
    documents: List<PendingDocument>,
    currentUserRole: UserRole,
    onUpdateStatus: (PendingDocument, String, String) -> Unit,
    onDelete: (Long) -> Unit,
    onAddContinuance: (name: String, remarks: String) -> Unit,
    onClose: () -> Unit
) {
    var continueFrom by remember { mutableStateOf<PendingDocument?>(null) }
    order?.let {
        OrderStatusDialog(
            it, currentUserRole, onUpdateStatus, onDelete,
            onAddContinuance = { continueFrom = it }.takeIf { _ -> ProjectSchedule.isSuspension(it) },
            onDismiss = onClose
        )
    }
    continueFrom?.let { ws ->
        val startsOn = remember(ws, documents) { ProjectSchedule.nextContinuance(ws, documents).second }
        ContinuanceDialog(
            nameFor = { type -> ProjectSchedule.nextContinuance(ws, documents, type).first },
            initialType = ProjectSchedule.suspensionType(ws), startsOn = startsOn, onDismiss = { continueFrom = null }
        ) { name, days, effectivity ->
            onAddContinuance(name, ProjectSchedule.suspensionRemarks(days, effectivity))
            continueFrom = null
        }
    }
}

/**
 * Documents › one kind of order per chip ([SectionChips.ORDER_CHIPS]): suspensions with their continuances and the
 * resumptions, time extensions, variation orders, and letters to the contractor. Each has its own Add button.
 */
@Composable
fun OrdersList(
    chip: String,
    documents: List<PendingDocument>,
    currentUserRole: UserRole,
    onAddOrder: (type: String) -> Unit,
    onAddDocument: (name: String, remarks: String) -> Unit,
    onUpdateStatus: (PendingDocument, String, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val canEdit = currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT)
    var openOrder by remember { mutableStateOf<PendingDocument?>(null) }
    var openLetter by remember { mutableStateOf<PendingDocument?>(null) }
    var newLetter by remember { mutableStateOf(false) }
    val open: ((PendingDocument) -> Unit)? = if (canEdit) { doc -> openOrder = doc } else null
    fun add(type: String): (() -> Unit)? = if (canEdit) { { onAddOrder(type) } } else null
    fun named(text: String) = documents.filter { it.documentName.contains(text, ignoreCase = true) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        when (chip) {
            SectionChips.SUSPENSIONS -> {
                item {
                    val suspensions = documents.filter(ProjectSchedule::isSuspension)
                    val chains = suspensions.associateWith { ProjectSchedule.continuances(it, documents) }
                    // A continuance whose suspension was deleted still shows, at the end.
                    val orphans = named("Work Suspension") - suspensions.toSet() - chains.values.flatten().toSet()
                    OrdersCard("Work suspension orders", Icons.Default.PauseCircle, suspensions.isEmpty() && orphans.isEmpty(), "No work suspension orders.", add("Work Suspension Order")) {
                        suspensions.forEach { ws ->
                            OrderEntry(ws, open)
                            chains.getValue(ws).forEach { Box(Modifier.padding(start = 24.dp)) { OrderEntry(it, open) } }
                        }
                        orphans.forEach { OrderEntry(it, open) }
                    }
                }
                item {
                    val resumptions = named("Work Resumption")
                    OrdersCard("Work resumption orders", Icons.Default.PlayCircle, resumptions.isEmpty(), "No work resumption orders.", add("Work Resumption Order")) {
                        resumptions.forEach { OrderEntry(it, open) }
                    }
                }
            }
            SectionChips.TIME_EXTENSIONS -> item {
                val extensions = named("Time Extension")
                OrdersCard("Time extensions", Icons.Default.MoreTime, extensions.isEmpty(), "No time extensions.", add("Time Extension")) {
                    extensions.forEach { OrderEntry(it, open) }
                }
            }
            SectionChips.VARIATION_ORDERS -> item {
                VariationOrdersLog(documents, canEdit, { onAddOrder("Variation Order") }, currentUserRole, onUpdateStatus, onDelete)
            }
            SectionChips.LETTERS -> item {
                val letters = documents.filter(ContractorLetters::isLetter)
                OrdersCard("Letters to contractor", Icons.Default.Mail, letters.isEmpty(), "No letters to the contractor.", { newLetter = true }.takeIf { canEdit }) {
                    letters.forEach { letter ->
                        val received = ContractorLetters.received(letter)
                        LogEntry(
                            letter.documentName,
                            MaterialTheme.colorScheme.onSurface,
                            listOf(
                                "Reason: ${ContractorLetters.reason(letter).ifBlank { "-" }}",
                                if (received.isBlank()) "Not yet received by the contractor" else "Received by the contractor: $received"
                            ),
                            onClick = { openLetter = letter }.takeIf { canEdit }
                        )
                    }
                }
            }
        }
    }

    OrderDialogs(openOrder, documents, currentUserRole, onUpdateStatus, onDelete, onAddDocument) { openOrder = null }
    if (newLetter || openLetter != null) {
        LetterDialog(
            existing = openLetter,
            nextNo = ContractorLetters.nextNo(documents),
            onDismiss = { newLetter = false; openLetter = null },
            onSave = { name, remarks ->
                openLetter?.let { onUpdateStatus(it, it.status, remarks) } ?: onAddDocument(name, remarks)
                newLetter = false; openLetter = null
            },
            onDelete = { onDelete(it.id); openLetter = null }
        )
    }
}

@Composable
private fun OrdersCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    empty: Boolean,
    emptyText: String,
    onAdd: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionCard(
        title, icon,
        action = onAdd?.let { add ->
            {
                TextButton(onClick = add, modifier = Modifier.testTag("add_${title.replace(' ', '_')}")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }
    ) {
        if (empty) EmptyLine(emptyText)
        content()
    }
}

@Composable
private fun OrderEntry(doc: PendingDocument, onOpen: ((PendingDocument) -> Unit)?) {
    LogEntry(ProjectSchedule.orderLabel(doc.documentName), MaterialTheme.colorScheme.onSurface, listOf("Status: ${doc.status}", doc.remarks), onClick = onOpen?.let { open -> { open(doc) } })
}

/** Adds or edits a letter to the contractor: its reason and the day the contractor received it. */
@Composable
private fun LetterDialog(
    existing: PendingDocument?,
    nextNo: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, remarks: String) -> Unit,
    onDelete: (PendingDocument) -> Unit
) {
    var no by remember { mutableStateOf(existing?.documentName?.substringAfter("No. ", "") ?: nextNo.toString()) }
    var reason by remember { mutableStateOf(existing?.let(ContractorLetters::reason).orEmpty()) }
    var received by remember { mutableStateOf(existing?.let(ContractorLetters::received).orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New letter to contractor" else existing.documentName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing == null) OutlinedTextField(value = no, onValueChange = { no = it.filter(Char::isDigit) }, label = { Text("Letter No.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth().testTag("letter_reason"))
                com.example.ui.components.DateField(label = "Date received by the contractor", value = received, onValueChange = { received = it }, clearable = true)
                Text("Shows on the weekly report for the week the contractor received it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("Delete letter", color = LocalNHAColors.current.behind) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(ContractorLetters.name(no), ContractorLetters.remarks(reason, received)) },
                enabled = no.isNotBlank(),
                modifier = Modifier.testTag("save_letter_button")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Documents › Variation orders: every order, pending too, so a new one shows; only approved ones change the cost. */
@Composable
fun VariationOrdersLog(
    documents: List<PendingDocument>,
    canAddVariationOrder: Boolean,
    onAddVariationOrder: () -> Unit,
    currentUserRole: UserRole,
    onUpdateStatus: (PendingDocument, String, String) -> Unit,
    onDeleteDocument: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var openOrder by remember { mutableStateOf<PendingDocument?>(null) }
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
                listOf(if (type.isNotEmpty()) "Type: $type" else "", "Status: ${vo.status}", "Date Approved: ${dateApproved ?: "-"}", displayText),
                onClick = if (currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT)) { { openOrder = vo } } else null
            )
        }
    }
    openOrder?.let { OrderStatusDialog(it, currentUserRole, onUpdateStatus, onDeleteDocument) { openOrder = null } }
}

/** A Continuance of Work Suspension Order: more days on a logged suspension, starting where it ends. */
@Composable
private fun ContinuanceDialog(
    nameFor: (type: String) -> String,
    initialType: String,
    startsOn: String,
    onDismiss: () -> Unit,
    onSave: (name: String, days: Int, effectivity: String) -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    val name = nameFor(type)
    var days by remember { mutableStateOf("") }
    var effectivity by remember { mutableStateOf(startsOn) }
    val dayCount = days.trim().toIntOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Continuance of Work Suspension") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                com.example.ui.dialogs.FullOrPartialPicker("Type of Suspension", type) { type = it }
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter(Char::isDigit) }, label = { Text("No. of Days") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                com.example.ui.components.DateField(label = "Effectivity Date", value = effectivity, onValueChange = { effectivity = it })
                Text(
                    "Saved as Pending. Like the suspension, it adds no days to the contract; an approved Time Extension does.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { dayCount?.let { onSave(name, it, effectivity) } }, enabled = dayCount != null && effectivity.isNotBlank(), modifier = Modifier.testTag("save_continuance_button")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
