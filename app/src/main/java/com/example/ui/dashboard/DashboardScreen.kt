package com.example.ui.dashboard

import com.example.ui.theme.LocalNHAColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Permission
import com.example.data.model.Project
import com.example.data.model.UserRole
import com.example.data.model.hasPermission
import com.example.ui.components.TopAppBarHeader
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProjectViewModel

@Composable
fun DashboardScreen(
    viewModel: ProjectViewModel,
    onProjectClick: (Project) -> Unit,
    onCreateProjectClick: () -> Unit,
    // Two panes (840dp and up): the list keeps 380dp and this fills the rest under one top bar.
    detailPane: (@Composable () -> Unit)? = null
) {
    val currentUser by viewModel.currentUserAccount.collectAsState()
    val registeredUsers by viewModel.registeredUsers.collectAsState()
    val registeredUsersLoaded by viewModel.registeredUsersLoaded.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val projects by viewModel.filteredProjects.collectAsState()
    val scopedProjects by viewModel.userScopedProjects.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val portfolio by viewModel.portfolioState.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showManageUsers by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<Long?>(null) }
    var projectToEdit by remember { mutableStateOf<Project?>(null) }

    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val isSuperAdmin = currentUser.role == UserRole.SUPER_ADMIN
    val emptyMessage = when {
        portfolio.totalProjects == 0 && isSuperAdmin ->
            "Database is Empty" to "No projects exist yet. Create a new project or sync from cloud."
        portfolio.totalProjects == 0 ->
            "No projects assigned to you yet" to "Ask the Super Admin to assign you a project, or create a new one."
        isSuperAdmin -> "No projects match the selected filters" to "Try clearing search or changing status/type filters"
        else -> "No assigned projects found for ${currentUser.name}" to
            "Contact your Principal Engineer / Super Admin to assign a project to your account"
    }

    val newProjectFab: @Composable () -> Unit = {
        if (!currentUser.isDemoAccount && currentUser.role.hasPermission(Permission.CREATE_PROJECT)) {
            ExtendedFloatingActionButton(
                onClick = onCreateProjectClick,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add") },
                text = { Text("New Project") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.testTag("create_project_fab")
            )
        }
    }
    val portfolioList: @Composable (Modifier) -> Unit = { listModifier ->
        Column(modifier = listModifier) {
            if (currentUser.role == UserRole.FIELD_ENGINEER) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PersonPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Assigned View: Showing projects managed by ${currentUser.name}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            PortfolioContent(
                state = portfolio,
                officeName = currentUser.office,
                searchQuery = searchQuery,
                sortOrder = sortOrder,
                statusFilter = statusFilter,
                typeFilter = typeFilter,
                typeOptions = listOf("All") + scopedProjects.map { it.projectType }.distinct().sorted(),
                emptyMessage = emptyMessage,
                onFilter = { viewModel.setPortfolioFilter(it) },
                onSearch = { viewModel.setSearchQuery(it) },
                onSort = { viewModel.setSortOrder(it) },
                onStatus = { viewModel.setStatusFilter(it) },
                onType = { viewModel.setTypeFilter(it) },
                onOpen = { project ->
                    viewModel.selectProject(project.id)
                    onProjectClick(project)
                },
                onEdit = if (currentUser.role.hasPermission(Permission.EDIT_PROJECT)) { project -> projectToEdit = project } else null,
                onDelete = if (currentUser.role.hasPermission(Permission.DELETE_PROJECT)) { project -> projectToDelete = project.id } else null,
                modifier = Modifier.weight(1f),
                emptyActions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isSuperAdmin || currentUser.role == UserRole.ENGINEER_ADMIN) {
                            Button(onClick = onCreateProjectClick, shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Project", fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(onClick = { viewModel.importSampleData() }, shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync from Cloud", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBarHeader(
                currentUser = currentUser,
                onSwitchUser = { viewModel.switchUserAccount(it) },
                isSyncing = isSyncing,
                onEditProfileClick = { showEditProfileDialog = true },
                onSignOut = { viewModel.signOutUser() },
                unreadNotificationCount = unreadCount,
                onOpenNotifications = { showNotificationsDialog = true },
                appThemeMode = appThemeMode,
                onSetThemeMode = { viewModel.setAppThemeMode(it) },
                onManageUsers = {
                    viewModel.refreshRegisteredUsers()
                    showManageUsers = true
                }
            )
        },
        floatingActionButton = { if (detailPane == null) newProjectFab() },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (detailPane == null) {
            portfolioList(Modifier.fillMaxSize().padding(paddingValues))
        } else {
            TwoPanes(
                list = { portfolioList(Modifier.fillMaxSize()) },
                fab = newProjectFab,
                detail = detailPane,
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            )
        }
    }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Are you sure you want to delete this project? This action cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                TextButton(onClick = {
                    projectToDelete?.let { viewModel.deleteProject(it) }
                    projectToDelete = null
                }) {
                    Text("Delete", color = LocalNHAColors.current.behind)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (projectToEdit != null) {
        LaunchedEffect(Unit) {
            if (currentUser.role == com.example.data.model.UserRole.SUPER_ADMIN && !registeredUsersLoaded) viewModel.refreshRegisteredUsers()
        }
        com.example.ui.dialogs.EditProjectDialog(
            project = projectToEdit!!,
            canAssign = currentUser.role == com.example.data.model.UserRole.SUPER_ADMIN,
            registeredUsers = registeredUsers,
            registeredUsersLoaded = registeredUsersLoaded,
            onDismiss = { projectToEdit = null },
            onEdit = { name, location, implementingOffice, contractor, scopeOfWork, projectType, landArea, numberOfUnits, cost, duration, dateStarted, completionDateOriginal, assignedStaff, assignedUsers, projectedBeneficiaries, boardResolution, noticeOfAward, noticeToProceed ->
                val updated = projectToEdit!!.copy(
                    name = name,
                    location = location,
                    implementingOffice = implementingOffice,
                    contractor = contractor,
                    scopeOfWork = scopeOfWork,
                    projectType = projectType,
                    landArea = landArea,
                    numberOfUnits = numberOfUnits,
                    projectedBeneficiaries = projectedBeneficiaries,
                    boardResolution = boardResolution,
                    noticeOfAward = noticeOfAward,
                    noticeToProceed = noticeToProceed,
                    contractCostOriginal = cost,
                    contractDurationDays = duration,
                    dateStarted = dateStarted,
                    completionDateOriginal = completionDateOriginal,
                    assignedStaff = assignedStaff
                )
                viewModel.updateProjectMaster(updated, assignedUsers)
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

    if (showManageUsers) {
        com.example.ui.dialogs.ManageUsersDialog(
            users = registeredUsers,
            loaded = registeredUsersLoaded,
            onSetRole = { user, role, onDone -> viewModel.setUserRole(user, role, onDone) },
            onDismiss = { showManageUsers = false }
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

/** Portfolio list (380dp, with its "New Project" button) beside the project pane. */
@Composable
internal fun TwoPanes(
    list: @Composable () -> Unit,
    fab: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Box(modifier = Modifier.width(380.dp).fillMaxHeight()) {
            list()
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { fab() }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { detail() }
    }
}
