package com.example.ui.detail.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.Permission
import com.example.data.model.ProjectIssue
import com.example.data.model.UserRole
import com.example.data.model.hasPermission
import com.example.ui.theme.*

@Composable
fun IssuesTab(
    issues: List<ProjectIssue>,
    currentUserRole: UserRole,
    onAddClick: () -> Unit,
    onEditClick: (ProjectIssue) -> Unit,
    onDeleteClick: (ProjectIssue) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredIssues = remember(issues, searchQuery, selectedFilter) {
        issues.filter { issue ->
            val matchesQuery = searchQuery.isBlank() ||
                    issue.description.contains(searchQuery, ignoreCase = true) ||
                    issue.actionTaken.contains(searchQuery, ignoreCase = true) ||
                    issue.remarks.contains(searchQuery, ignoreCase = true) ||
                    issue.loggedBy.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Critical" -> issue.isCritical
                "Resolved" -> issue.status.contains("Resolved", ignoreCase = true) || issue.status.contains("Closed", ignoreCase = true)
                "Pending" -> issue.status.contains("Pending", ignoreCase = true) || issue.status.contains("Open", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search issues or actions taken...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DarkTextSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = DarkTextSecondary)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_issues_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = DarkTextPrimary,
                    unfocusedTextColor = DarkTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Critical", "Pending", "Resolved").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.25f),
                            selectedLabelColor = Color(0xFF38BDF8),
                            containerColor = DarkSurface,
                            labelColor = DarkTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == filter,
                            borderColor = DarkBorder,
                            selectedBorderColor = Color(0xFF38BDF8)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredIssues.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(56.dp), tint = DarkTextSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedFilter != "All") "No matching issues found" else "No Issues Logged",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredIssues, key = { it.id }) { issue ->
                        IssueCard(
                            issue = issue,
                            currentUserRole = currentUserRole,
                            onEditClick = { onEditClick(issue) },
                            onDeleteClick = { onDeleteClick(issue) }
                        )
                    }
                }
            }
        }

        if (currentUserRole.hasPermission(Permission.LOG_ISSUE)) {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = Color(0xFF38BDF8),
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_issue_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Issue")
            }
        }
    }
}

@Composable
fun IssueCard(
    issue: ProjectIssue,
    currentUserRole: UserRole,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val borderColor = if (issue.isCritical) StatusRedBorder else DarkBorder

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Header Row: Date & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = issue.date,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (issue.isCritical) {
                        Surface(
                            color = StatusRedBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, StatusRedBorder)
                        ) {
                            Text(
                                text = "CRITICAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = StatusRedText,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val (statusBg, statusText, statusBorder) = when {
                        issue.status.contains("Resolved", ignoreCase = true) -> Triple(StatusGreenBg, StatusGreenText, StatusGreenBorder)
                        issue.status.contains("Critical", ignoreCase = true) -> Triple(StatusRedBg, StatusRedText, StatusRedBorder)
                        else -> Triple(StatusOrangeBg, StatusOrangeText, StatusOrangeBorder)
                    }

                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, statusBorder)
                    ) {
                        Text(
                            text = issue.status.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = statusText,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Issue Description (Full multiline wrapping)
            Text(
                text = "ISSUE DESCRIPTION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkTextSecondary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = issue.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp
                ),
                softWrap = true,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth()
            )

            // Action Taken Section (Full multiline wrapping)
            if (issue.actionTaken.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "ACTION TAKEN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = issue.actionTaken,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = DarkTextPrimary,
                        lineHeight = 20.sp
                    ),
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Remarks / Notes Section
            if (issue.remarks.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "REMARKS / NOTES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkTextSecondary
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = issue.remarks,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = DarkTextSecondary,
                        lineHeight = 18.sp
                    ),
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Footer: Logged By & Action Buttons
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (issue.loggedBy.isNotBlank()) {
                    Text(
                        text = "Logged by: ${issue.loggedBy}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = DarkTextSecondary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        softWrap = true,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentUserRole.hasPermission(Permission.EDIT_ISSUE)) {
                        IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Issue", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                    }
                    if (currentUserRole.hasPermission(Permission.DELETE_ISSUE)) {
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Issue", tint = StatusRedText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Issue") },
            text = { Text("Are you sure you want to delete this issue entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDeleteClick() }) {
                    Text("Delete", color = StatusRedText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = DarkTextSecondary
        )
    }
}
