package com.example.ui.detail.tabs

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
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
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Weekly Engineer Reports Submitted Yet",
                        style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onAddWeeklyReportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
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
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "NHA WEEKLY PROJECT ENGINEER'S REPORT",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        // Adding new weekly report button directly below title
                        if (currentUserRole.hasPermission(Permission.SUBMIT_REPORT)) {
                            Button(
                                onClick = onAddWeeklyReportClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                                shape = RoundedCornerShape(50.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_weekly_report_top_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add New Weekly Report", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                items(weeklyReports, key = { it.id }) { report ->
                    WeeklyReportCard(report = report, currentUserRole = currentUserRole, onEditClick = { onEditWeeklyReportClick(report) }, onDeleteClick = { onDeleteWeeklyReportClick(report) })
                }
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
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder),
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
                                color = DarkTextPrimary
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentUserRole.hasPermission(Permission.EDIT_REPORT)) {
                                IconButton(onClick = onEditClick, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DarkTextSecondary, modifier = Modifier.size(24.dp))
                                }
                            }
                            if (currentUserRole.hasPermission(Permission.DELETE_REPORT)) {
                                var showDeleteConfirm by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRedText, modifier = Modifier.size(24.dp))
                                }
                                
                                if (showDeleteConfirm) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteConfirm = false },
                                        title = { Text("Delete Report", color = Color.White) },
                                        text = { Text("Are you sure you want to delete this report?", color = DarkTextPrimary) },
                                        confirmButton = {
                                            TextButton(onClick = { showDeleteConfirm = false; onDeleteClick() }) { Text("Delete", color = StatusRedText) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = DarkTextSecondary) }
                                        },
                                        containerColor = DarkSurface
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Submitted by: ${report.submittedByStaff}  |  Elapsed: ${report.daysElapsed}d, Remaining: ${report.remainingDays}d",
                        style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                    )
                }

                Surface(
                    color = if (report.actualAccomplishmentPct >= report.targetAccomplishmentPct) StatusGreenBg else StatusRedBg,
                    shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(1.dp, if (report.actualAccomplishmentPct >= report.targetAccomplishmentPct) StatusGreenBorder else StatusRedBorder)
                ) {
                    Text(
                        text = "Actual: ${"%.1f".format(report.actualAccomplishmentPct)}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (report.actualAccomplishmentPct >= report.targetAccomplishmentPct) StatusGreenText else StatusRedText
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = DarkTextPrimary
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(bottom = 12.dp))

                    val parsedAccItems = remember(report.accomplishmentItemsJson) { parseAccomplishmentItemsList(report.accomplishmentItemsJson) }

                    // 1. Weekly Accomplishment Status (Scope of Works)
                    Text(
                        text = "1. Weekly Accomplishment Status",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (parsedAccItems.isNotEmpty()) {
                                parsedAccItems.forEach { acc ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(acc.itemDescription + " (" + acc.weightPct + "%)", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
                                        Text("T: ${"%.1f".format(acc.targetPct)}%", color = DarkTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Text("A: ${"%.1f".format(acc.actualPct)}%", color = DarkTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Text("V: ${"%.1f".format(acc.variancePct)}%", color = if (acc.variancePct >= 0) WeatherFairGreen else StatusRedText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.8f))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("GRAND TOTAL", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                Text("T: ${"%.2f".format(report.targetAccomplishmentPct)}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = DarkTextPrimary))
                                Text("A: ${"%.2f".format(report.actualAccomplishmentPct)}%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary))
                                val varPct = report.actualAccomplishmentPct - report.targetAccomplishmentPct
                                Text(
                                    "V: ${if (varPct >= 0) "+${"%.2f".format(varPct)}" else "%.2f".format(varPct)}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (varPct >= 0) StatusGreenText else StatusRedText
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
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (parsedActivities.isNotEmpty()) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkSurfaceVariant)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("DAY OF WEEK", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(1.1f))
                                    Text("ACTIVITIES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(2f))
                                    Text("REMARKS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(1f))
                                }
                                HorizontalDivider(color = DarkBorder)

                                parsedActivities.forEachIndexed { index, act ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(act.day, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White), modifier = Modifier.weight(1.1f))
                                        Text(act.description, style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary), modifier = Modifier.weight(2f))
                                        Text(act.remarks.ifEmpty { "-" }, style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary), modifier = Modifier.weight(1f))
                                    }
                                    if (index < parsedActivities.size - 1) {
                                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = report.activitiesJson,
                                style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextPrimary),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Manpower Summary Table
                    Text(
                        text = "3. Manpower Summary Table",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(DarkSurfaceVariant)
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("DESIGNATION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text("NO. OF PERSONNEL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            }
                            HorizontalDivider(color = DarkBorder)

                            // Rows
                            if (manpowerList.isEmpty()) {
                                Text("No manpower logged.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary), modifier = Modifier.padding(12.dp))
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
                                        Text(item.designation, style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextPrimary), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                        Text("${item.count}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                    }
                                    if (idx < manpowerList.lastIndex) {
                                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }

                            // Grand Total Row
                            HorizontalDivider(color = DarkBorder, thickness = 1.5.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(DarkSurfaceVariant)
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GRAND TOTAL WEEKLY", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text("$grandTotalManpower Personnel", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8)), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Equipment Summary Table
                    Text(
                        text = "4. Equipment Summary Table",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(DarkSurfaceVariant)
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("NAME OF EQUIPMENT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text("NO.", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(0.7f).padding(horizontal = 12.dp))
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text("REMARKS / STATUS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(1.3f).padding(horizontal = 12.dp))
                            }
                            HorizontalDivider(color = DarkBorder)

                            if (equipmentList.isEmpty()) {
                                Text("No equipment logged.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary), modifier = Modifier.padding(12.dp))
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
                                        Text(item.description, style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextPrimary), modifier = Modifier.weight(2f).padding(horizontal = 12.dp))
                                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                        Text("${item.count}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary), modifier = Modifier.weight(0.7f).padding(horizontal = 12.dp))
                                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())

                                        val (bgColor, textColor, borderColor) = when (item.status.trim()) {
                                            "Operational" -> Triple(StatusGreenBg, StatusGreenText, StatusGreenBorder)
                                            "Idle" -> Triple(StatusOrangeBg, StatusOrangeText, StatusOrangeBorder)
                                            "For Repair" -> Triple(StatusRedBg, StatusRedText, StatusRedBorder)
                                            else -> Triple(DarkSurfaceVariant, DarkTextPrimary, DarkBorder)
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
                                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Issues Encountered / Action Taken Table
                    Text(
                        text = "5. Issues Encountered / Action Taken",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(DarkSurfaceVariant)
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ISSUES ENCOUNTERED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text("ACTION TAKEN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            }
                            HorizontalDivider(color = DarkBorder)

                            if (issuesList.isEmpty()) {
                                Text("No issues encountered for this reporting period.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary), modifier = Modifier.padding(12.dp))
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
                                        Text(item.description, style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                        Text(item.actionTaken, style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                                    }
                                    if (idx < issuesList.lastIndex) {
                                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }

                    if (report.attachedPhotoUrlsJson.isNotBlank() && report.attachedPhotoUrlsJson != "[]") {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Attached Site Photo:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                        )
                        val cleanedUrl = report.attachedPhotoUrlsJson.replace("[", "").replace("]", "").replace("\"", "")
                        if (cleanedUrl.isNotBlank()) {
                            AsyncImage(
                                model = cleanedUrl,
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
private fun parseManpowerList(jsonStr: String): List<ManpowerItem> {
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

private fun parseEquipmentList(jsonStr: String): List<EquipmentItem> {
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

private fun parseIssuesList(jsonStr: String): List<WeeklyIssue> {
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

private fun parseActivitiesList(jsonStr: String): List<DailyActivity> {
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