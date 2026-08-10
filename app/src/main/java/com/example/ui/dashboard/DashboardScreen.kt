package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Permission
import com.example.data.model.Project
import com.example.data.model.ProjectStatus
import com.example.data.model.UserRole
import com.example.data.model.hasPermission
import com.example.ui.components.TopAppBarHeader
import com.example.ui.dialogs.GoogleDriveSyncDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProjectViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ProjectViewModel,
    onProjectClick: (Project) -> Unit,
    onCreateProjectClick: () -> Unit
) {
    val currentUser by viewModel.currentUserAccount.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val projects by viewModel.filteredProjects.collectAsState()
    val rawProjects by viewModel.rawProjects.collectAsState()
    val debugError by viewModel.debugError.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val allPendingDocs by viewModel.allPendingDocuments.collectAsState()
    val summaryStats by viewModel.dashboardSummaryStats.collectAsState()

    val isDriveSyncing by viewModel.isDriveSyncing.collectAsState()
    val lastDriveSyncTime by viewModel.lastDriveSyncTime.collectAsState()
    val isAutoSyncEnabled by viewModel.isAutoSyncEnabled.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
    val driveSyncStatusMessage by viewModel.driveSyncStatusMessage.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showGoogleDriveDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<Long?>(null) }
    var projectToEdit by remember { mutableStateOf<Project?>(null) }

    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val phpFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "PH")) }

    Scaffold(
        topBar = {
            TopAppBarHeader(
                currentUser = currentUser,
                onSwitchUser = { viewModel.switchUserAccount(it) },
                isSyncing = isSyncing,
                onOpenDriveSync = { showGoogleDriveDialog = true },
                onEditProfileClick = { showEditProfileDialog = true },
                onSignOut = { viewModel.signOutUser() },
                unreadNotificationCount = unreadCount,
                onOpenNotifications = { showNotificationsDialog = true },
                appThemeMode = appThemeMode,
                onSetThemeMode = { viewModel.setAppThemeMode(it) },
                onElevateRole = { viewModel.elevateUserRole(it) },
                onElevateRoleWithProfile = { role, position, office -> viewModel.elevateUserRole(role, position, office) }
            )
        },
        floatingActionButton = {
            if (!currentUser.isDemoAccount && currentUser.role.hasPermission(Permission.CREATE_PROJECT)) {
                ExtendedFloatingActionButton(
                    onClick = onCreateProjectClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                    text = { Text("New Project") },
                    containerColor = Color(0xFF38BDF8),
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier.testTag("create_project_fab")
                )
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Control Header Section: Stats, Search, Sort & Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Stats Summary Cards
                DashboardSummaryCard(stats = summaryStats)

                // 2. Search & Sort Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("search_project_input"),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start, color = Color.White, fontSize = 14.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
                        decorationBox = { innerTextField ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = DarkSurface,
                                border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) Color(0xFF38BDF8) else DarkBorder),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "Search project name, location...", fontSize = 13.5.sp, 
                                                color = DarkTextSecondary
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = DarkTextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showSortMenu = true },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkSurface,
                                contentColor = DarkTextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier
                                .testTag("sort_button")
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = DarkTextPrimary, modifier = Modifier.size(18.dp))
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            Text(
                                text = "SORT BY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            listOf(
                                "Variance (Worst First)",
                                "Variance (Best First)",
                                "Completion Date",
                                "Name"
                            ).forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = DarkTextPrimary) },
                                    onClick = {
                                        viewModel.setSortOrder(option)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOrder == option) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF38BDF8))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Filter Dropdown Lists Row: Status & Project Types
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Project Status Dropdown Selector
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showStatusMenu = true },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (statusFilter != "All") Color(0xFF38BDF8) else DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (statusFilter != "All") DarkSurfaceVariant else DarkSurface,
                                contentColor = DarkTextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("status_dropdown_button")
                        ) {
                            Text(
                                text = "Status: $statusFilter",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Status",
                                tint = DarkTextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = showStatusMenu,
                            onDismissRequest = { showStatusMenu = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            Text(
                                text = "FILTER BY PROJECT STATUS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            HorizontalDivider(color = DarkBorder)
                            listOf("All", "On-going", "Behind Schedule", "Suspended").forEach { statusOption ->
                                DropdownMenuItem(
                                    text = { Text(statusOption, color = DarkTextPrimary, fontWeight = if (statusFilter == statusOption) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        viewModel.setStatusFilter(statusOption)
                                        showStatusMenu = false
                                    },
                                    leadingIcon = {
                                        if (statusFilter == statusOption) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF38BDF8))
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Project Type Dropdown Selector
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showTypeMenu = true },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (typeFilter != "All") Color(0xFF38BDF8) else DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (typeFilter != "All") DarkSurfaceVariant else DarkSurface,
                                contentColor = DarkTextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("type_dropdown_button")
                        ) {
                            Text(
                                text = "Type: $typeFilter",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Type",
                                tint = DarkTextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = showTypeMenu,
                            onDismissRequest = { showTypeMenu = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            Text(
                                text = "FILTER BY PROJECT TYPE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            HorizontalDivider(color = DarkBorder)
                            val uniqueTypes = rawProjects.map { it.projectType }.distinct().sorted()
                            val typeOptions = listOf("All") + uniqueTypes
                            typeOptions.forEach { typeOption ->
                                DropdownMenuItem(
                                    text = { Text(typeOption, color = DarkTextPrimary, fontWeight = if (typeFilter == typeOption) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        viewModel.setTypeFilter(typeOption)
                                        showTypeMenu = false
                                    },
                                    leadingIcon = {
                                        if (typeFilter == typeOption) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF38BDF8))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Field Engineer Assigned Banner Indicator
            if (currentUser.role == UserRole.FIELD_ENGINEER) {
                Surface(
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonPin,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Assigned View: Showing projects managed by ${currentUser.name}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // Projects Tile List
            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (rawProjects.isEmpty()) {
                            Text(
                                text = "Database is Empty",
                                style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No projects exist yet. Create a new project or sync from cloud.",
                                style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary, textAlign = TextAlign.Center)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentUser.role == UserRole.ENGINEER_ADMIN) {
                                    Button(
                                        onClick = onCreateProjectClick,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("New Project", fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.importSampleData() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary),
                                    border = BorderStroke(1.dp, GoldAccent)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync from Cloud", fontWeight = FontWeight.Medium)
                                }
                            }
                        } else {
                            Text(
                                text = if (currentUser.role != UserRole.SUPER_ADMIN) "No assigned projects found for ${currentUser.name}" else "No projects match the selected filters",
                                style = MaterialTheme.typography.titleMedium.copy(color = Color.Gray, textAlign = TextAlign.Center)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (currentUser.role != UserRole.SUPER_ADMIN) "Contact your Principal Engineer / Super Admin to assign a project to your account" else "Try clearing search or changing status/type filters",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray, textAlign = TextAlign.Center)
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(projects, key = { it.id }) { project ->
                        val pendingCount = allPendingDocs.count { it.projectId == project.id && it.status == "Pending" }

                        ProjectTileCard(
                            project = project,
                            pendingDocsCount = pendingCount,
                            onClick = {
                                viewModel.selectProject(project.id)
                                onProjectClick(project)
                            },
                            onDeleteClick = if (currentUser.role.hasPermission(Permission.DELETE_PROJECT)) {
                                { projectToDelete = project.id }
                            } else null,
                            onEditClick = if (currentUser.role.hasPermission(Permission.EDIT_PROJECT)) {
                                { projectToEdit = project }
                            } else null
                        )
                    }
                }
            }
        }
    }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project", color = DarkTextPrimary) },
            text = { Text("Are you sure you want to delete this project? This action cannot be undone.", color = DarkTextSecondary) },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = {
                    projectToDelete?.let { viewModel.deleteProject(it) }
                    projectToDelete = null
                }) {
                    Text("Delete", color = StatusRedText)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel", color = DarkTextPrimary)
                }
            }
        )
    }

    if (projectToEdit != null) {
        com.example.ui.dialogs.EditProjectDialog(
            project = projectToEdit!!,
            onDismiss = { projectToEdit = null },
            onEdit = { name, location, implementingOffice, contractor, scopeOfWork, projectType, landArea, numberOfUnits, cost, duration, dateStarted, completionDateOriginal, assignedStaff ->
                val updated = projectToEdit!!.copy(
                    name = name,
                    location = location,
                    implementingOffice = implementingOffice,
                    contractor = contractor,
                    scopeOfWork = scopeOfWork,
                    projectType = projectType,
                    landArea = landArea,
                    numberOfUnits = numberOfUnits,
                    contractCostOriginal = cost,
                    contractDurationDays = duration,
                    dateStarted = dateStarted,
                    completionDateOriginal = completionDateOriginal,
                    assignedStaff = assignedStaff
                )
                viewModel.updateProjectMaster(updated)
                projectToEdit = null
            }
        )
    }

    if (showEditProfileDialog) {
        com.example.ui.dialogs.EditProfileDialog(
            currentUser = currentUser,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, position, office ->
                viewModel.updateUserProfile(name, position, office)
                showEditProfileDialog = false
            }
        )
    }

    if (showGoogleDriveDialog) {
        GoogleDriveSyncDialog(
            accountEmail = driveAccountEmail,
            lastSyncTime = lastDriveSyncTime,
            isSyncing = isDriveSyncing,
            isAutoSyncEnabled = isAutoSyncEnabled,
            statusMessage = driveSyncStatusMessage,
            onDismiss = { showGoogleDriveDialog = false },
            onBackupNow = { viewModel.backupToGoogleDrive() },
            onRestoreNow = { viewModel.restoreFromGoogleDrive() },
            onToggleAutoSync = { viewModel.toggleDriveAutoSync(it) }
        )
    }

    if (showNotificationsDialog) {
        com.example.ui.dialogs.NotificationsDialog(
            notifications = notifications,
            onDismiss = { showNotificationsDialog = false },
            onMarkAsRead = { viewModel.markNotificationAsRead(it) },
            onMarkAllAsRead = { viewModel.markAllNotificationsAsRead() },
            onClearAll = { viewModel.clearAllNotifications() },
            onSelectProject = { projId ->
                projects.find { it.id == projId }?.let { onProjectClick(it) }
            }
        )
    }
}

@Composable
fun RowScope.StatBox(title: String, value: String, valueColor: Color) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.weight(1f),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextSecondary
                ),
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = valueColor
                ),
                maxLines = 1
            )
        }
    }
}
