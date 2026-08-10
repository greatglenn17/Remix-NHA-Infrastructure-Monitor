package com.example.ui.detail.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuditLog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AuditLogTab(
    auditLogs: List<AuditLog>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val filterOptions = listOf("All", "Project", "Report", "Issue", "Payment", "Document", "User", "Sync", "Backup/Restore/Migration")

    val filteredLogs = remember(auditLogs, searchQuery, selectedFilter) {
        auditLogs.filter { log ->
            val matchesQuery = searchQuery.isBlank() ||
                    log.actionType.contains(searchQuery, ignoreCase = true) ||
                    log.user.contains(searchQuery, ignoreCase = true) ||
                    log.details.contains(searchQuery, ignoreCase = true) ||
                    log.device.contains(searchQuery, ignoreCase = true) ||
                    log.oldValue.contains(searchQuery, ignoreCase = true) ||
                    log.newValue.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Project" -> log.actionType.contains("Project", ignoreCase = true)
                "Report" -> log.actionType.contains("Report", ignoreCase = true)
                "Issue" -> log.actionType.contains("Issue", ignoreCase = true)
                "Payment" -> log.actionType.contains("Payment", ignoreCase = true)
                "Document" -> log.actionType.contains("Document", ignoreCase = true)
                "User" -> log.actionType.contains("User", ignoreCase = true) || log.actionType.contains("Login", ignoreCase = true) || log.actionType.contains("Logout", ignoreCase = true)
                "Sync" -> log.actionType.contains("Sync", ignoreCase = true)
                "Backup/Restore/Migration" -> log.actionType.contains("Backup", ignoreCase = true) || log.actionType.contains("Restore", ignoreCase = true) || log.actionType.contains("Migration", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Search & Filter Row
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search audit logs (user, action, details, device)...", color = DarkTextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF38BDF8)) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = DarkTextSecondary)
                    }
                }
            } else null,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = DarkTextPrimary,
                unfocusedTextColor = DarkTextPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("audit_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter chips
        ScrollableTabRow(
            selectedTabIndex = filterOptions.indexOf(selectedFilter).coerceAtLeast(0),
            containerColor = Color.Transparent,
            contentColor = Color(0xFF38BDF8),
            edgePadding = 0.dp,
            divider = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            filterOptions.forEach { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = DarkSurface,
                        labelColor = DarkTextSecondary
                    ),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .testTag("audit_filter_$filter")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No audit log records found",
                        color = DarkTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("audit_logs_list")
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    AuditLogCard(log = log)
                }
            }
        }
    }
}

@Composable
fun AuditLogCard(log: AuditLog) {
    val formattedDate = remember(log.timestamp) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    val (badgeColor, icon) = when {
        log.actionType.contains("Creation", ignoreCase = true) -> Color(0xFF10B981) to Icons.Default.AddCircle
        log.actionType.contains("Edit", ignoreCase = true) || log.actionType.contains("Update", ignoreCase = true) -> Color(0xFF3B82F6) to Icons.Default.Edit
        log.actionType.contains("Deletion", ignoreCase = true) -> Color(0xFFEF4444) to Icons.Default.Delete
        log.actionType.contains("Login", ignoreCase = true) -> Color(0xFF8B5CF6) to Icons.Default.Login
        log.actionType.contains("Logout", ignoreCase = true) -> Color(0xFF6B7280) to Icons.Default.Logout
        log.actionType.contains("Sync", ignoreCase = true) -> Color(0xFF06B6D4) to Icons.Default.Sync
        log.actionType.contains("Backup", ignoreCase = true) -> Color(0xFFF59E0B) to Icons.Default.CloudUpload
        log.actionType.contains("Restore", ignoreCase = true) -> Color(0xFF14B8A6) to Icons.Default.CloudDownload
        log.actionType.contains("Migration", ignoreCase = true) -> Color(0xFFA855F7) to Icons.Default.Storage
        log.actionType.contains("Upload", ignoreCase = true) || log.actionType.contains("Document", ignoreCase = true) -> Color(0xFFEC4899) to Icons.Default.UploadFile
        else -> Color(0xFF38BDF8) to Icons.Default.Info
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .testTag("audit_log_item_${log.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Action badge & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = log.actionType,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = formattedDate,
                    color = DarkTextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // User & Device details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = log.user,
                    color = DarkTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = DarkTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = log.device.ifBlank { "Android Device" },
                    color = DarkTextSecondary,
                    fontSize = 12.sp
                )
            }

            if (log.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = log.details,
                    color = DarkTextPrimary,
                    fontSize = 13.sp
                )
            }

            // Old value & New value section
            if (log.oldValue.isNotBlank() || log.newValue.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .padding(10.dp)
                ) {
                    if (log.oldValue.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "Old Value: ",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = log.oldValue,
                                color = DarkTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (log.oldValue.isNotBlank() && log.newValue.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (log.newValue.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "New Value: ",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = log.newValue,
                                color = DarkTextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
