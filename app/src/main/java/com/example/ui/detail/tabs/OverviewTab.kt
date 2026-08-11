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

@Composable
fun OverviewTab(
    project: Project,
    weeklyReports: List<WeeklyReport>,
    pendingDocuments: List<PendingDocument>,
    currentUserRole: UserRole,
    onEditProjectClick: () -> Unit = {},
    onAddRelatedDocument: (documentName: String, status: String, remarks: String, fileUrl: String) -> Unit = { _, _, _, _ -> },
    onUpdateDocumentFileUrl: (doc: PendingDocument, fileUrl: String) -> Unit = { _, _ -> },
    onDeleteDocument: (Long) -> Unit = {}
) {
    val phpFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    var showAddDocDialog by remember { mutableStateOf(false) }
    var viewingDocForScan by remember { mutableStateOf<PendingDocument?>(null) }
    var docToDelete by remember { mutableStateOf<PendingDocument?>(null) }

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
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = StatusRedText)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Document?", style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold))
                }
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${docToDelete?.documentName}\"? This document entry will be permanently deleted.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextSecondary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        docToDelete?.let { onDeleteDocument(it.id) }
                        docToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRedText, contentColor = Color.White)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Details Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TIER 1 — PROJECT MASTER PROFILE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        if (currentUserRole.hasPermission(Permission.EDIT_PROJECT)) {
                            IconButton(onClick = onEditProjectClick, modifier = Modifier.size(44.dp).testTag("edit_project_button")) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Project", tint = Color(0xFF94A3B8))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailItem("Implementing Office", project.implementingOffice)
                    DetailItem("Contractor", project.contractor)
                    
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(text = "Scope of Works", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))

                        val parsedScopeItems = remember(project.scopeOfWork) {
                            val list = mutableListOf<Triple<String, Double, Double>>()
                            try {
                                val items = org.json.JSONArray(project.scopeOfWork)
                                for (i in 0 until items.length()) {
                                    val obj = items.getJSONObject(i)
                                    val name = obj.optString("name", "")
                                    val weight = obj.optDouble("weightPct", 0.0)
                                    val amt = obj.optDouble("amount", 0.0)
                                    list.add(Triple(name, amt, weight))
                                }
                            } catch (e: Exception) {
                                list.clear()
                                list.add(Triple(project.scopeOfWork, 0.0, 100.0))
                            }
                            list
                        }
                        parsedScopeItems.filter { it.first.isNotBlank() }.forEachIndexed { index, (name, amt, weight) ->
                            Surface(
                                color = Color(0xFF1A2332),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${index + 1}. $name",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.SemiBold)
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = "Amount: ${com.example.utils.CurrencyFormatter.formatPhp(amt)}",
                                            style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                                        )
                                        Text(
                                            text = "Weight: $weight%",
                                            style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DetailItem("Project Type", project.projectType)

                    // Indication of Land Area & No. of Units for Housing Projects
                    if (project.projectType.contains("Housing", ignoreCase = true)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                DetailItem("Land Area", project.landArea.ifBlank { "4.5 Hectares" })
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                DetailItem("No. of Units", project.numberOfUnits.ifBlank { "120 Housing Units" })
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = DarkBorder)

                    // Auto-computed: sum of all scope of work amounts
                    val computedOriginalCost = remember(project.scopeOfWork) {
                        try {
                            val items = org.json.JSONArray(project.scopeOfWork)
                            var total = 0.0
                            for (i in 0 until items.length()) {
                                total += items.getJSONObject(i).optDouble("amount", 0.0)
                            }
                            if (total > 0.0) total else project.contractCostOriginal
                        } catch (e: Exception) {
                            project.contractCostOriginal
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            DetailItem("Original Contract Cost", com.example.utils.CurrencyFormatter.formatPhp(computedOriginalCost))
                            DetailItem("Original Completion Date", project.completionDateOriginal)
                            DetailItem("Contract Duration", "${com.example.utils.CurrencyFormatter.formatNumber(project.contractDurationDays)} Days")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            // 1. Approved Variation Orders computation for Revised Contract Cost
                            val approvedVODocs = remember(pendingDocuments) {
                                pendingDocuments.filter { 
                                    it.documentName.contains("Variation Order", ignoreCase = true) && 
                                    it.status.equals("Approved", ignoreCase = true) 
                                }
                            }
                            val voCostSum = remember(approvedVODocs) {
                                approvedVODocs.sumOf { vo ->
                                    val match = Regex("PHP\\s*([0-9,\\.]+)", RegexOption.IGNORE_CASE).find(vo.documentName)
                                    val amtString = match?.groupValues?.get(1)?.replace(",", "")
                                    amtString?.toDoubleOrNull() ?: 0.0
                                }
                            }
                            
                            val revisedContractCostText = remember(approvedVODocs, voCostSum, computedOriginalCost) {
                                if (approvedVODocs.isNotEmpty()) {
                                    val totalCost = computedOriginalCost + voCostSum
                                    com.example.utils.CurrencyFormatter.formatPhp(totalCost)
                                } else {
                                    "-"
                                }
                            }
                            DetailItem("Revised Contract Cost", revisedContractCostText)

                            // 2. Approved Time Extensions & Work Suspensions computation for Revised Completion Date & Revised Duration
                            val approvedTEDocs = remember(pendingDocuments) {
                                pendingDocuments.filter { 
                                    it.documentName.contains("Time Extension", ignoreCase = true) && 
                                    it.status.equals("Approved", ignoreCase = true) 
                                }
                            }
                            val approvedSuspDocs = remember(pendingDocuments) {
                                pendingDocuments.filter { 
                                    it.documentName.contains("Work Suspension", ignoreCase = true) && 
                                    it.status.equals("Approved", ignoreCase = true) 
                                }
                            }
                            
                            val totalApprovedExtDays = remember(approvedTEDocs) {
                                approvedTEDocs.sumOf { ext ->
                                    Regex(".* - (\\d+) Days", RegexOption.IGNORE_CASE).find(ext.documentName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                }
                            }
                            val totalApprovedSuspDays = remember(approvedSuspDocs) {
                                approvedSuspDocs.sumOf { ext ->
                                    Regex("Duration: (\\d+) Days", RegexOption.IGNORE_CASE).find(ext.remarks)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                }
                            }

                            val hasApprovedExtension = approvedTEDocs.isNotEmpty() || approvedSuspDocs.isNotEmpty()
                            val totalAddedDays = totalApprovedExtDays + totalApprovedSuspDays
                            
                            val revisedCompletionDateText = remember(hasApprovedExtension, totalAddedDays, project.dateStarted, project.contractDurationDays) {
                                if (hasApprovedExtension && totalAddedDays > 0) {
                                    try {
                                        val totalDays = project.contractDurationDays + totalAddedDays
                                        val start = java.time.LocalDate.parse(project.dateStarted)
                                        start.plusDays(totalDays.toLong()).toString()
                                    } catch (e: Exception) {
                                        "-"
                                    }
                                } else {
                                    "-"
                                }
                            }

                            val revisedDurationText = remember(hasApprovedExtension, totalAddedDays, project.contractDurationDays) {
                                if (hasApprovedExtension && totalAddedDays > 0) {
                                    "${project.contractDurationDays + totalAddedDays} Days"
                                } else {
                                    "-"
                                }
                            }

                            DetailItem("Revised Completion Date", revisedCompletionDateText)
                            DetailItem("Revised Duration", revisedDurationText)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            DetailItem("Date Started", project.dateStarted)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            // empty spacing
                        }
                    }
                }
            }
        }

        // Related Files & Project Documents Table Card (Inserted Above Time Extension Logs)
        item {
            val relatedFiles = pendingDocuments.filter { doc ->
                !doc.documentName.contains("Time Extension", ignoreCase = true) &&
                !doc.documentName.contains("Variation Order", ignoreCase = true) &&
                !doc.documentName.contains("Work Suspension", ignoreCase = true) &&
                !doc.documentName.contains("Work Resumption", ignoreCase = true)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Related Files & Documents (${relatedFiles.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                            )
                        }

                        if (currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT)) {
                            Button(
                                onClick = { showAddDocDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("add_related_file_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Add File", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (relatedFiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No related project files or scanned documents uploaded yet.",
                                style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                            )
                        }
                    } else {
                        // Table Form
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkBackground),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Table Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .background(DarkSurfaceVariant)
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Document Title",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 11.sp),
                                        modifier = Modifier.weight(1.8f).padding(horizontal = 8.dp)
                                    )
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                    Text(
                                        text = "Status",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 11.sp),
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                    Text(
                                        text = "Details",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8), fontSize = 11.sp),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(2.2f).padding(horizontal = 8.dp)
                                    )
                                    VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                    Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                    }
                                }

                                HorizontalDivider(color = DarkBorder)

                                // Table Rows
                                relatedFiles.forEachIndexed { index, doc ->
                                    @OptIn(ExperimentalFoundationApi::class)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .background(if (index % 2 == 0) DarkSurface else DarkSurfaceVariant.copy(alpha = 0.5f))
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                    if (currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT)) {
                                                        docToDelete = doc
                                                    }
                                                }
                                            )
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Document Title
                                        Text(
                                            text = doc.documentName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = DarkTextPrimary, fontSize = 11.sp),
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1.8f).padding(horizontal = 8.dp)
                                        )
                                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())

                                        // Status
                                        val (statusBg, statusFg) = when (doc.status) {
                                            "Approved" -> Pair(StatusGreenBg, StatusGreenText)
                                            "Notarized" -> Pair(Color(0xFF0284C7).copy(alpha = 0.2f), Color(0xFF38BDF8))
                                            "Signed" -> Pair(Color(0xFF8B5CF6).copy(alpha = 0.2f), Color(0xFFA78BFA))
                                            "Submitted" -> Pair(StatusOrangeBg, StatusOrangeText)
                                            else -> Pair(StatusRedBg, StatusRedText)
                                        }
                                        Box(
                                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Surface(
                                                color = statusBg,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = doc.status,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusFg),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())

                                        // Details
                                        Text(
                                            text = doc.remarks.ifBlank { "N/A" },
                                            style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary, fontSize = 10.sp),
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(2.2f).padding(horizontal = 8.dp)
                                        )
                                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())

                                        // Scanned Copy Eye / Upload Action Icon
                                        Box(
                                            modifier = Modifier.width(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (doc.fileUrl.isNotBlank()) {
                                                IconButton(
                                                    onClick = { viewingDocForScan = doc },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Visibility,
                                                        contentDescription = "View Attached Copy",
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { viewingDocForScan = doc },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.UploadFile,
                                                        contentDescription = "Upload Copy",
                                                        tint = Color(0xFFF59E0B).copy(alpha = 0.8f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (index < relatedFiles.size - 1) {
                                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Time Extensions Log
        item {
            val teLogs = pendingDocuments.filter { it.documentName.contains("Time Extension", ignoreCase = true) && (it.status.equals("Approved", ignoreCase = true) || it.status.equals("Submitted", ignoreCase = true)) }
            LogCardHeader(
                title = "Time Extension Logs (${teLogs.size})",
                icon = Icons.Default.MoreTime,
                showAddButton = false,
                onAddClick = {},
                testTag = "add_extension_button"
            ) {
                if (teLogs.isEmpty()) {
                    Text("No time extensions recorded.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                } else {
                    teLogs.forEach { ext ->
                        val cleanRemarks = ext.remarks.replace("^Remarks:\\s*".toRegex(RegexOption.IGNORE_CASE), "").trim()
                        val displayText = if (cleanRemarks.startsWith("Reason:", ignoreCase = true)) {
                            cleanRemarks
                        } else if (cleanRemarks.isNotBlank()) {
                            "Reason: $cleanRemarks"
                        } else ""

                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = ext.documentName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                                )
                                Text("Status: ${ext.status}", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                                if (displayText.isNotBlank()) {
                                    Text(displayText, style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Variation Orders Log
        item {
            val voLogs = pendingDocuments.filter { it.documentName.contains("Variation Order", ignoreCase = true) && (it.status.equals("Approved", ignoreCase = true) || it.status.equals("Submitted", ignoreCase = true)) }
            LogCardHeader(
                title = "Variation Orders Log (${voLogs.size})",
                icon = Icons.Default.ReceiptLong,
                showAddButton = false,
                onAddClick = {},
                testTag = "add_variation_button"
            ) {
                if (voLogs.isEmpty()) {
                    Text("No variation orders recorded.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                } else {
                    voLogs.forEach { vo ->
                        val cleanRemarks = vo.remarks.replace("^Remarks:\\s*".toRegex(RegexOption.IGNORE_CASE), "").trim()
                        val displayText = if (cleanRemarks.startsWith("Reason:", ignoreCase = true)) {
                            cleanRemarks
                        } else if (cleanRemarks.isNotBlank()) {
                            "Reason: $cleanRemarks"
                        } else ""

                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = vo.documentName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                                )
                                Text("Status: ${vo.status}", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                                if (displayText.isNotBlank()) {
                                    Text(displayText, style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Work Suspension Orders Log
        item {
            val wsLogs = pendingDocuments.filter { it.documentName.contains("Work Suspension", ignoreCase = true) && (it.status.equals("Approved", ignoreCase = true) || it.status.equals("Submitted", ignoreCase = true)) }
            LogCardHeader(
                title = "Work Suspension Logs (${wsLogs.size})",
                icon = Icons.Default.PauseCircle,
                showAddButton = false,
                onAddClick = {},
                testTag = "add_suspension_button"
            ) {
                if (wsLogs.isEmpty()) {
                    Text("No work suspensions recorded.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                } else {
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

                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = so.documentName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = StatusOrangeText)
                                )
                                Text("Status: ${so.status}", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                                Text("Duration: $durMatch", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                                Text("Effectivity Date: $effMatch", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                                Text("End Date: $finalEndDate", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                            }
                        }
                    }
                }
            }
        }

        // Work Resumption Logs
        item {
            val wrLogs = pendingDocuments.filter { it.documentName.contains("Work Resumption", ignoreCase = true) && (it.status.equals("Approved", ignoreCase = true) || it.status.equals("Submitted", ignoreCase = true)) }
            LogCardHeader(
                title = "Work Resumption Logs (${wrLogs.size})",
                icon = Icons.Default.PlayCircle,
                showAddButton = false,
                onAddClick = {},
                testTag = "add_resumption_button"
            ) {
                if (wrLogs.isEmpty()) {
                    Text("No work resumptions recorded.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                } else {
                    wrLogs.forEach { log ->
                        val remarksText = log.remarks
                        val effMatch = Regex("Effectivity:\\s*([^|\\n]+)").find(remarksText)?.groupValues?.get(1)?.trim() ?: "2026-05-25"
                        var reasonMatch = Regex("(?:Remarks|Reason):\\s*([^|\\n]+)").find(remarksText.replace("Effectivity:\\s*([^|\\n]+)".toRegex(), ""))?.groupValues?.get(1)?.trim()
                        if (reasonMatch.isNullOrBlank()) {
                            reasonMatch = "Weather condition cleared"
                        }

                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = log.documentName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = StatusGreenText)
                                )
                                Text("Status: ${log.status}", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                                Text("Effectivity Date: $effMatch", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                                Text("Reason: $reasonMatch", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String, isHighlight: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isHighlight) com.example.ui.theme.StatusOrangeText else DarkTextSecondary,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isHighlight) com.example.ui.theme.StatusOrangeText else DarkTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LogCardHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    showAddButton: Boolean,
    onAddClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary))
                }

                if (showAddButton) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.testTag(testTag)
                    ) {
                        Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Add Log", tint = Color(0xFF38BDF8))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

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
    var status by remember { mutableStateOf("Approved") }
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

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            fileUrl = uri.toString()
        }
    }

    val presetScans = listOf(
        "Contract Scan" to "https://images.unsplash.com/photo-1568992687947-868a62a9f521?auto=format&fit=crop&w=800&q=80",
        "Blueprint Scan" to "https://images.unsplash.com/photo-1503387762-592deb58ef4e?auto=format&fit=crop&w=800&q=80",
        "Permit Scan" to "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=800&q=80"
    )

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

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Quick reference document scan:", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary, fontSize = 10.sp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            presetScans.forEach { (label, url) ->
                                FilterChip(
                                    selected = fileUrl == url,
                                    onClick = { fileUrl = url },
                                    label = { Text(label, fontSize = 9.sp) },
                                    modifier = Modifier.weight(1f)
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
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            onUpdateFileUrl(doc, uri.toString())
        }
    }

    val presetScans = listOf(
        "Contract Scan" to "https://images.unsplash.com/photo-1568992687947-868a62a9f521?auto=format&fit=crop&w=800&q=80",
        "Blueprint Scan" to "https://images.unsplash.com/photo-1503387762-592deb58ef4e?auto=format&fit=crop&w=800&q=80",
        "Permit Scan" to "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?auto=format&fit=crop&w=800&q=80"
    )

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
                            .background(DarkBackground, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = doc.fileUrl,
                            contentDescription = "Scanned Copy",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
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
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetScans.forEach { (label, url) ->
                        OutlinedButton(
                            onClick = { onUpdateFileUrl(doc, url) },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(32.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                        ) {
                            Text(label, fontSize = 9.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
