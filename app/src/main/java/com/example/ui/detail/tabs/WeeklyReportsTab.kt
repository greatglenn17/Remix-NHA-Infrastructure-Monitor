package com.example.ui.detail.tabs

import com.example.ui.theme.LocalNHAColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.theme.*
import org.json.JSONArray

@Composable
fun WeeklyReportsTab(
    weeklyReports: List<WeeklyReport>,
    currentUserRole: UserRole,
    onAddWeeklyReportClick: () -> Unit,
    onEditWeeklyReportClick: (WeeklyReport) -> Unit,
    onDeleteWeeklyReportClick: (WeeklyReport) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (weeklyReports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Weekly Engineer Reports Submitted Yet",
                        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onAddWeeklyReportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier.testTag("add_weekly_report_empty_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add New Weekly Report", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "NHA WEEKLY PROJECT ENGINEER'S REPORT",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                items(weeklyReports, key = { it.id }) { report ->
                    WeeklyReportCard(report = report, currentUserRole = currentUserRole, onEditClick = { onEditWeeklyReportClick(report) }, onDeleteClick = { onDeleteWeeklyReportClick(report) })
                }
            }
            if (currentUserRole.hasPermission(Permission.SUBMIT_REPORT)) {
                ExtendedFloatingActionButton(
                    onClick = onAddWeeklyReportClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add weekly report") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("add_weekly_report_top_button")
                )
            }
        }
    }
}

@Composable
private fun WeeklyReportCard(
    report: WeeklyReport,
    currentUserRole: UserRole,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val manpowerList = remember(report.manpowerJson) { parseManpowerList(report.manpowerJson) }
    val equipmentList = remember(report.equipmentJson) { parseEquipmentList(report.equipmentJson) }
    val issuesList = remember(report.issuesJson) { parseIssuesList(report.issuesJson) }
    val grandTotalManpower = remember(manpowerList) { manpowerList.sumOf { it.count } }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = report.reportingWeek,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentUserRole.hasPermission(Permission.EDIT_REPORT)) {
                                IconButton(onClick = onEditClick, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                }
                            }
                            if (currentUserRole.hasPermission(Permission.DELETE_REPORT)) {
                                var showDeleteConfirm by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LocalNHAColors.current.behind, modifier = Modifier.size(24.dp))
                                }
                                
                                if (showDeleteConfirm) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteConfirm = false },
                                        title = { Text("Delete Report", color = MaterialTheme.colorScheme.onSurface) },
                                        text = { Text("Are you sure you want to delete this report?", color = MaterialTheme.colorScheme.onSurface) },
                                        confirmButton = {
                                            TextButton(onClick = { showDeleteConfirm = false; onDeleteClick() }) { Text("Delete", color = LocalNHAColors.current.behind) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        },
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Submitted by: ${report.submittedByStaff}  |  Elapsed: ${report.daysElapsed}d, Remaining: ${report.remainingDays}d",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                Surface(
                    color = if (report.actualAccomplishmentPct >= report.targetAccomplishmentPct) LocalNHAColors.current.onTrackContainer else LocalNHAColors.current.behindContainer,
                    shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(1.dp, if (report.actualAccomplishmentPct >= report.targetAccomplishmentPct) LocalNHAColors.current.onTrack.copy(alpha = 0.4f) else LocalNHAColors.current.behind.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Actual: ${"%.1f".format(report.actualAccomplishmentPct)}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (report.actualAccomplishmentPct >= report.targetAccomplishmentPct) LocalNHAColors.current.onTrack else LocalNHAColors.current.behind
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 12.dp))

                    val parsedAccItems = remember(report.accomplishmentItemsJson) { parseAccomplishmentItemsList(report.accomplishmentItemsJson) }

                    // 1. Weekly Accomplishment Status (Scope of Works)
                    Text(
                        text = "1. Weekly Accomplishment Status",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (parsedAccItems.isNotEmpty()) {
                                parsedAccItems.forEach { acc ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(acc.itemDescription + " (" + acc.weightPct + "%)", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
                                        Text("T: ${"%.1f".format(acc.targetPct)}%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Text("A: ${"%.1f".format(acc.actualPct)}%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Text("V: ${"%.1f".format(acc.variancePct)}%", color = if (acc.variancePct >= 0) WeatherFairGreen else LocalNHAColors.current.behind, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.8f))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("GRAND TOTAL", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                                Text("T: ${"%.2f".format(report.targetAccomplishmentPct)}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface))
                                Text("A: ${"%.2f".format(report.actualAccomplishmentPct)}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                                val varPct = report.actualAccomplishmentPct - report.targetAccomplishmentPct
                                Text(
                                    "V: ${if (varPct >= 0) "+${"%.2f".format(varPct)}" else "%.2f".format(varPct)}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (varPct >= 0) LocalNHAColors.current.onTrack else LocalNHAColors.current.behind
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Activities of the Week
                    val parsedActivities = remember(report.activitiesJson) { parseActivitiesList(report.activitiesJson) }

                    Text(
                        text = "2. Activities of the Week",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (parsedActivities.isNotEmpty()) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("DAY OF WEEK", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1.1f))
                                    Text("ACTIVITIES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(2f))
                                    Text("REMARKS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1f))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                parsedActivities.forEachIndexed { index, act ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(act.day, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1.1f))
                                        Text(act.description, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(2f))
                                        Text(act.remarks.ifEmpty { "-" }, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1f))
                                    }
                                    if (index < parsedActivities.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = report.activitiesJson,
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Manpower Summary Table
                    Text(
                        text = "3. Manpower Summary Table",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("DESIGNATION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())
                                Text("NO. OF PERSONNEL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Rows
                            if (manpowerList.isEmpty()) {
                                Text("No manpower logged.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.padding(12.dp))
                            } else {
                                manpowerList.forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.designation, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())
                                        Text("${item.count}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                    }
                                    if (idx < manpowerList.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }

                            // Grand Total Row
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.5.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GRAND TOTAL WEEKLY", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())
                                Text("$grandTotalManpower Personnel", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Equipment Summary Table
                    Text(
                        text = "4. Equipment Summary Table",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("NAME OF EQUIPMENT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())
                                Text("NO.", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(0.7f).padding(horizontal = 12.dp))
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())
                                Text("REMARKS / STATUS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1.3f).padding(horizontal = 12.dp))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            if (equipmentList.isEmpty()) {
                                Text("No equipment logged.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.padding(12.dp))
                            } else {
                                equipmentList.forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.description, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())
                                        Text("${item.count}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(0.7f).padding(horizontal = 12.dp))
                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())

                                        val (bgColor, textColor, borderColor) = when (item.status.trim()) {
                                            "Operational" -> Triple(LocalNHAColors.current.onTrackContainer, LocalNHAColors.current.onTrack, LocalNHAColors.current.onTrack.copy(alpha = 0.4f))
                                            "Idle" -> Triple(LocalNHAColors.current.watchContainer, LocalNHAColors.current.watch, LocalNHAColors.current.watch.copy(alpha = 0.4f))
                                            "For Repair" -> Triple(LocalNHAColors.current.behindContainer, LocalNHAColors.current.behind, LocalNHAColors.current.behind.copy(alpha = 0.4f))
                                            else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.outlineVariant)
                                        }

                                        Box(
                                            modifier = Modifier.weight(1.3f).padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Surface(
                                                color = bgColor,
                                                shape = RoundedCornerShape(50.dp),
                                                border = BorderStroke(1.dp, borderColor)
                                            ) {
                                                Text(
                                                    text = item.status,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = textColor),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (idx < equipmentList.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Issues Encountered / Action Taken Table
                    Text(
                        text = "5. Issues Encountered / Action Taken",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ISSUES ENCOUNTERED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())
                                Text("ACTION TAKEN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            if (issuesList.isEmpty()) {
                                Text("No issues encountered for this reporting period.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.padding(12.dp))
                            } else {
                                issuesList.forEachIndexed { idx, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min)
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.description, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight())
                                        Text(item.actionTaken, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                    }
                                    if (idx < issuesList.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }

                    val photos = com.example.data.files.Attachments.parseList(report.attachedPhotoUrlsJson)
                    if (photos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (photos.size > 1) "Attached Site Photos (${photos.size}, all in the Photos tab):" else "Attached Site Photo:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        )
                        run {
                            AsyncImage(
                                model = photos.first(),
                                contentDescription = "Site Photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helpers to parse JSON safely
internal fun parseManpowerList(jsonStr: String): List<ManpowerItem> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<ManpowerItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                ManpowerItem(
                    designation = obj.optString("designation", "Personnel"),
                    count = obj.optInt("count", 1),
                    remarks = obj.optString("remarks", "")
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun parseEquipmentList(jsonStr: String): List<EquipmentItem> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<EquipmentItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                EquipmentItem(
                    description = obj.optString("description", "Equipment"),
                    count = obj.optInt("count", 1),
                    status = obj.optString("status", obj.optString("remarks", "Operational")),
                    remarks = obj.optString("remarks", "")
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun parseIssuesList(jsonStr: String): List<WeeklyIssue> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<WeeklyIssue>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                WeeklyIssue(
                    description = obj.optString("description", ""),
                    actionTaken = obj.optString("actionTaken", ""),
                    remarks = obj.optString("remarks", "")
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun parseActivitiesList(jsonStr: String): List<DailyActivity> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<DailyActivity>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                DailyActivity(
                    day = obj.optString("day", obj.optString("dayOfWeek", "")),
                    description = obj.optString("description", obj.optString("activity", "")),
                    remarks = obj.optString("remarks", "")
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}


private fun parseAccomplishmentItemsList(jsonStr: String): List<AccomplishmentItem> {
    if (jsonStr.isBlank()) return emptyList()
    val list = mutableListOf<AccomplishmentItem>()
    try {
        val arr = JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(AccomplishmentItem(
                itemDescription = obj.getString("itemDescription"),
                weightPct = obj.optDouble("weightPct", 0.0),
                targetPct = obj.getDouble("targetPct"),
                actualPct = obj.getDouble("actualPct")
            ))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}