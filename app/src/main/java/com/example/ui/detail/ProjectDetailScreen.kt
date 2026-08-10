package com.example.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.example.data.model.Permission
import com.example.data.model.Project
import com.example.data.model.ProjectStatus
import com.example.data.model.hasPermission
import com.example.ui.components.TopAppBarHeader
import com.example.ui.dialogs.GoogleDriveSyncDialog
import com.example.ui.detail.tabs.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectViewModel,
    onBackClick: () -> Unit,
    onAddWeeklyReportClick: () -> Unit,
    onAddMonthlyReportClick: () -> Unit,
    onAddTimeExtensionClick: () -> Unit,
    onAddVariationOrderClick: () -> Unit,
    onAddWorkSuspensionClick: () -> Unit,
    onAddWorkResumptionClick: () -> Unit,
    onAddDocumentClick: () -> Unit,
    onAddIssueClick: () -> Unit
) {
    val currentUser by viewModel.currentUserAccount.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val project by viewModel.selectedProject.collectAsState()
    val timeExtensions by viewModel.timeExtensions.collectAsState()
    val variationOrders by viewModel.variationOrders.collectAsState()
    val workSuspensionOrders by viewModel.workSuspensionOrders.collectAsState()
    val workResumptionLogs by viewModel.workResumptionLogs.collectAsState()
    val pendingDocuments by viewModel.pendingDocuments.collectAsState()
    val weeklyReports by viewModel.weeklyReports.collectAsState()
    val monthlyReports by viewModel.monthlyReports.collectAsState()
    val projectIssues by viewModel.projectIssues.collectAsState()
    val projectPayments by viewModel.projectPayments.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val dailyWeatherLogs by viewModel.dailyWeatherLogs.collectAsState()

    val isDriveSyncing by viewModel.isDriveSyncing.collectAsState()
    val lastDriveSyncTime by viewModel.lastDriveSyncTime.collectAsState()
    val isAutoSyncEnabled by viewModel.isAutoSyncEnabled.collectAsState()
    val driveAccountEmail by viewModel.driveAccountEmail.collectAsState()
    val driveSyncStatusMessage by viewModel.driveSyncStatusMessage.collectAsState()

    val sdpPlans by viewModel.sdpPlans.collectAsState()
    val activeSdpPlan by viewModel.activeSdpPlan.collectAsState()
    val activeSdpLots by viewModel.activeSdpLots.collectAsState()
    val activeSdpRoads by viewModel.activeSdpRoads.collectAsState()
    val activeSdpLotProgressMap by viewModel.activeSdpLotProgressMap.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var projectToEdit by remember { mutableStateOf<Project?>(null) }
    var showGoogleDriveDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    val canViewAuditLogs = currentUser.role.hasPermission(com.example.data.model.Permission.VIEW_AUDIT_LOGS)

    val isHousingProject = remember(project) {
        project?.projectType?.equals(com.example.data.model.ProjectType.HOUSING_PROJECT.label, ignoreCase = true) == true ||
        project?.projectType?.equals("Housing Project", ignoreCase = true) == true
    }

    val tabTitles = remember(canViewAuditLogs, isHousingProject) {
        val list = mutableListOf("Overview")
        if (isHousingProject) {
            list.add("Subdivision Plan")
        }
        list.addAll(listOf("Weekly", "Monthly", "Issues", "Gallery", "Pending Docs", "Weather 24h", "Payments"))
        if (canViewAuditLogs) {
            list.add("Audit Log")
        }
        list.toList()
    }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF38BDF8))
        }
        return
    }

    val currentProject = project!!

    LaunchedEffect(currentProject.id) {
        viewModel.selectProject(currentProject.id)
    }

    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationCount.collectAsState()
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    var showNotificationsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBarHeader(
                currentUser = currentUser,
                onSwitchUser = { viewModel.switchUserAccount(it) },
                onBackClick = onBackClick,
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
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Project Header Banner
            Surface(
                color = DarkSurface,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = when (currentProject.status) {
                                ProjectStatus.ONGOING.label -> if (currentProject.variance >= 0) StatusGreenBorder else StatusRedBorder
                                ProjectStatus.SUSPENDED.label -> StatusGrayBorder
                                else -> StatusOrangeBorder
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = currentProject.status.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        val varianceStr = if (currentProject.variance >= 0) "+%.1f%%".format(currentProject.variance) else "%.1f%%".format(currentProject.variance)
                        Surface(
                            color = if (currentProject.variance >= 0) StatusGreenBg else StatusRedBg,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Variance: $varianceStr",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentProject.variance >= 0) StatusGreenText else StatusRedText
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentProject.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentProject.location,
                            style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextSecondary)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target: ${"%.1f".format(currentProject.targetAccomplishment)}%  |  Actual: ${"%.1f".format(currentProject.actualAccomplishment)}%",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val approvedTEDocsHeader = remember(pendingDocuments) {
                            pendingDocuments.filter { 
                                it.documentName.contains("Time Extension", ignoreCase = true) && 
                                it.status.equals("Approved", ignoreCase = true) 
                            }
                        }
                        val approvedSuspDocsHeader = remember(pendingDocuments) {
                            pendingDocuments.filter { 
                                it.documentName.contains("Work Suspension", ignoreCase = true) && 
                                it.status.equals("Approved", ignoreCase = true) 
                            }
                        }
                        val totalApprovedExtDaysHeader = remember(approvedTEDocsHeader, approvedSuspDocsHeader) {
                            approvedTEDocsHeader.sumOf { ext ->
                                Regex(".* - (\\d+) Days", RegexOption.IGNORE_CASE).find(ext.documentName)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            } + approvedSuspDocsHeader.sumOf { ext ->
                                Regex("Duration: (\\d+) Days", RegexOption.IGNORE_CASE).find(ext.remarks)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            }
                        }
                        val hasApprovedExtensionHeader = approvedTEDocsHeader.isNotEmpty() || approvedSuspDocsHeader.isNotEmpty()
                        val headerRevisedEndText = remember(hasApprovedExtensionHeader, totalApprovedExtDaysHeader, currentProject.dateStarted, currentProject.contractDurationDays) {
                            if (hasApprovedExtensionHeader && totalApprovedExtDaysHeader > 0) {
                                try {
                                    val totalDays = currentProject.contractDurationDays + totalApprovedExtDaysHeader
                                    val start = java.time.LocalDate.parse(currentProject.dateStarted)
                                    start.plusDays(totalDays.toLong()).toString()
                                } catch (e: Exception) {
                                    "-"
                                }
                            } else {
                                "-"
                            }
                        }
                        Text(
                            text = "Revised End: $headerRevisedEndText",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Scrollable Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = DarkSurfaceVariant,
                contentColor = Color(0xFF38BDF8),
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == index) Color(0xFF38BDF8) else DarkTextSecondary
                                )
                            )
                        },
                        modifier = Modifier.testTag("detail_tab_$index")
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                val currentTabTitle = tabTitles.getOrNull(selectedTabIndex) ?: "Overview"
                when (currentTabTitle) {
                    "Overview" -> OverviewTab(
                        project = currentProject,
                        weeklyReports = weeklyReports,
                        pendingDocuments = pendingDocuments,
                        currentUserRole = currentUser.role,
                        onEditProjectClick = { projectToEdit = currentProject },
                        onAddRelatedDocument = { name, status, remarks, fileUrl ->
                            viewModel.addRelatedDocument(currentProject.id, name, status, remarks, fileUrl)
                        },
                        onUpdateDocumentFileUrl = { doc, fileUrl ->
                            viewModel.updatePendingDocumentFileUrl(doc, fileUrl)
                        },
                        onDeleteDocument = { id ->
                            viewModel.deletePendingDocument(id)
                        }
                    )
                    "Subdivision Plan" -> com.example.ui.detail.tabs.SubdivisionPlanTab(
                        project = currentProject,
                        sdpPlans = sdpPlans,
                        activeSdpPlan = activeSdpPlan,
                        activeLots = activeSdpLots,
                        activeRoads = activeSdpRoads,
                        lotProgressMap = activeSdpLotProgressMap,
                        currentUserRole = currentUser.role,
                        onUploadSdpPlan = { planName: String, pdfUrl: String, desc: String ->
                            viewModel.addSdpPlan(currentProject.id, planName, pdfUrl, desc)
                        },
                        onSelectActiveVersion = { planId: Long ->
                            viewModel.setActiveSdpPlan(currentProject.id, planId)
                        },
                        onAddLot = { block: String, lot: String, unit: String, area: Double, json: String, desc: String ->
                            activeSdpPlan?.let { plan ->
                                viewModel.addSdpLot(currentProject.id, plan.id, block, lot, unit, area, json, desc)
                            }
                        },
                        onUpdateLot = { lot: com.example.data.model.SdpLot ->
                            viewModel.updateSdpLot(lot)
                        },
                        onDeactivateLot = { lot: com.example.data.model.SdpLot ->
                            viewModel.deactivateSdpLot(lot)
                        },
                        onAddRoad = { name: String, type: String, json: String ->
                            activeSdpPlan?.let { plan ->
                                viewModel.addSdpRoad(currentProject.id, plan.id, name, type, json)
                            }
                        },
                        onUpdateRoad = { road: com.example.data.model.SdpRoad ->
                            viewModel.updateSdpRoad(road)
                        },
                        onDeactivateRoad = { road: com.example.data.model.SdpRoad ->
                            viewModel.deactivateSdpRoad(road)
                        },
                        onUpdateLotProgress = { lotId: Long, progress: Int, status: String, activity: String, startDate: String, targetDate: String, contractor: String, remarks: String ->
                            activeSdpPlan?.let { plan ->
                                viewModel.updateLotProgress(
                                    projectId = currentProject.id,
                                    sdpPlanId = plan.id,
                                    sdpLotId = lotId,
                                    physicalProgress = progress,
                                    constructionStatus = status,
                                    currentActivity = activity,
                                    startDate = startDate,
                                    targetCompletionDate = targetDate,
                                    contractor = contractor,
                                    remarks = remarks
                                )
                            }
                        },
                        onUpdateLotBillingStatus = { lotId: Long, isBilled: Boolean, reference: String, remarks: String ->
                            activeSdpPlan?.let { plan ->
                                viewModel.updateLotBillingStatus(
                                    projectId = currentProject.id,
                                    sdpPlanId = plan.id,
                                    sdpLotId = lotId,
                                    isBilled = isBilled,
                                    billingReference = reference,
                                    billingRemarks = remarks
                                )
                            }
                        },
                        onRecordInspection = { lotId: Long, progress: Int, status: String, activity: String, contractor: String, remarks: String, billingStatus: String, billingRef: String ->
                            activeSdpPlan?.let { plan ->
                                viewModel.recordLotInspection(
                                    projectId = currentProject.id,
                                    sdpPlanId = plan.id,
                                    sdpLotId = lotId,
                                    physicalProgress = progress,
                                    constructionStatus = status,
                                    currentActivity = activity,
                                    contractor = contractor,
                                    remarks = remarks,
                                    billingStatus = billingStatus,
                                    billingReference = billingRef
                                )
                            }
                        },
                        onGetInspectionsForLot = { lotId: Long ->
                            viewModel.getInspectionsForLot(lotId)
                        },
                        auditLogs = auditLogs
                    )
                    "Weekly" -> WeeklyReportsTab(
                        weeklyReports = weeklyReports,
                        currentUserRole = currentUser.role,
                        onAddWeeklyReportClick = onAddWeeklyReportClick,
                        onEditWeeklyReportClick = { viewModel.weeklyReportToEdit.value = it; onAddWeeklyReportClick() },
                        onDeleteWeeklyReportClick = { viewModel.deleteWeeklyReport(it) }
                    )
                    "Monthly" -> MonthlyReportsTab(project = currentProject, 
                        monthlyReports = monthlyReports,
                        weeklyReports = weeklyReports,
                        currentUser = currentUser,
                        onAddMonthlyReportClick = onAddMonthlyReportClick,
                        onEditMonthlyReportClick = { viewModel.monthlyReportToEdit.value = it; onAddMonthlyReportClick() },
                        onDeleteMonthlyReportClick = { viewModel.deleteMonthlyReport(it) },
                        onSignOffClick = { report, roleType, status, notes ->
                            viewModel.updateMonthlySignoff(report, roleType, status, notes)
                        }
                    )
                    "Issues" -> IssuesTab(
                        issues = projectIssues,
                        currentUserRole = currentUser.role,
                        onAddClick = onAddIssueClick,
                        onEditClick = { issue -> viewModel.projectIssueToEdit.value = issue; onAddIssueClick() },
                        onDeleteClick = { issue -> viewModel.deleteProjectIssue(issue) }
                    )
                    "Gallery" -> PhotoGalleryTab(weeklyReports = weeklyReports)
                    "Pending Docs" -> PendingDocumentsTab(
                        pendingDocs = pendingDocuments,
                        currentUserRole = currentUser.role,
                        onAddDocumentClick = onAddDocumentClick,
                        onUpdateStatus = { doc, status, remarks ->
                            viewModel.updatePendingDocumentStatus(doc, status, remarks)
                        },
                        onUpdateFileUrl = { doc, url ->
                            viewModel.updatePendingDocumentFileUrl(doc, url)
                        },
                        onDeleteDocument = { id ->
                            viewModel.deletePendingDocument(id)
                        }
                    )
                    "Weather 24h" -> WeatherChartTab(
                        dailyWeatherLogs = dailyWeatherLogs,
                        weeklyReports = weeklyReports
                    )
                    "Payments" -> PaymentsTab(
                        project = currentProject,
                        payments = projectPayments,
                        onAddPayment = { name, dvNo, date, period, gross, pct, balAmt, balPct, fileUrl -> 
                            viewModel.addProjectPayment(currentProject.id, name, dvNo, date, period, gross, pct, balAmt, balPct, fileUrl)
                        },
                        onUpdatePayment = { payment ->
                            viewModel.updateProjectPayment(payment)
                        },
                        onDeletePayment = { viewModel.deleteProjectPayment(it.id) }
                    )
                    "Audit Log" -> AuditLogTab(auditLogs = auditLogs)
                }
            }
        }
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
            onSelectProject = null
        )
    }
}
