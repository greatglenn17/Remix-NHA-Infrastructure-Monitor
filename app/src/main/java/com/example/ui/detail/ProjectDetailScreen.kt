package com.example.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import com.example.data.model.Permission
import com.example.data.model.Project
import com.example.data.model.hasPermission
import com.example.ui.components.TopAppBarHeader
import com.example.ui.detail.sections.ChipRow
import com.example.ui.detail.sections.ProjectHeader
import com.example.ui.detail.sections.RelatedFilesList
import com.example.ui.detail.sections.SummarySection
import com.example.ui.detail.sections.MoneySection
import com.example.ui.detail.tabs.*
import com.example.ui.viewmodel.ProjectViewModel
import com.example.util.ContractCost
import com.example.util.ProjectAttention
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    onAddIssueClick: () -> Unit,
    // Two panes: the portfolio top bar spans both panes, so this screen shows none.
    embedded: Boolean = false
) {
    val currentUser by viewModel.currentUserAccount.collectAsState()
    val registeredUsers by viewModel.registeredUsers.collectAsState()
    val registeredUsersLoaded by viewModel.registeredUsersLoaded.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val project by viewModel.selectedProject.collectAsState()
    val pendingDocuments by viewModel.pendingDocuments.collectAsState()
    val weeklyReports by viewModel.weeklyReports.collectAsState()
    val monthlyReports by viewModel.monthlyReports.collectAsState()
    val projectIssues by viewModel.projectIssues.collectAsState()
    val projectPayments by viewModel.projectPayments.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val dailyWeatherLogs by viewModel.dailyWeatherLogs.collectAsState()
    val section by viewModel.selectedSection.collectAsState()

    val sdpPlans by viewModel.sdpPlans.collectAsState()
    val activeSdpPlan by viewModel.activeSdpPlan.collectAsState()
    val activeSdpLots by viewModel.activeSdpLots.collectAsState()
    val activeSdpRoads by viewModel.activeSdpRoads.collectAsState()
    val activeSdpLotProgressMap by viewModel.activeSdpLotProgressMap.collectAsState()

    var projectToEdit by remember { mutableStateOf<Project?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    val canViewAuditLogs = currentUser.role.hasPermission(Permission.VIEW_AUDIT_LOGS)

    val isHousingProject = remember(project) {
        project?.projectType?.equals(com.example.data.model.ProjectType.HOUSING_PROJECT.label, ignoreCase = true) == true ||
        project?.projectType?.equals("Housing Project", ignoreCase = true) == true
    }

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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

    val contractCost = ContractCost.current(currentProject.scopeOfWork, currentProject.contractCostOriginal, pendingDocuments)
    val billedTotal = projectPayments.sumOf { it.grossAmount }

    // The header folds to one line while the section scrolls up and opens again at the top.
    var headerCollapsed by remember(currentProject.id, section) { mutableStateOf(false) }
    val collapseOnScroll = remember(currentProject.id, section) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) headerCollapsed = true
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 1f) headerCollapsed = false
                return Offset.Zero
            }
        }
    }
    var progressChip by rememberSaveable(currentProject.id) { mutableStateOf<String?>(null) }
    var documentsChip by rememberSaveable(currentProject.id) { mutableStateOf<String?>(null) }

    CompositionLocalProvider(com.example.ui.components.LocalProjectUuid provides currentProject.projectUuid) {
    Scaffold(
        topBar = {
            if (!embedded) TopAppBarHeader(
                currentUser = currentUser,
                onSwitchUser = { viewModel.switchUserAccount(it) },
                onBackClick = onBackClick,
                isSyncing = isSyncing,
                onEditProfileClick = { showEditProfileDialog = true },
                onSignOut = { viewModel.signOutUser() },
                unreadNotificationCount = unreadCount,
                onOpenNotifications = { showNotificationsDialog = true },
                appThemeMode = appThemeMode,
                onSetThemeMode = { viewModel.setAppThemeMode(it) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        // Embedded, the portfolio's Scaffold has already padded for the system bars.
        contentWindowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ProjectHeader(
                project = currentProject,
                billedTotal = billedTotal,
                contractCost = contractCost,
                collapsed = headerCollapsed,
                // Two panes have no top bar back arrow.
                onClose = if (embedded) onBackClick else null
            )

            // Four fixed sections; no sideways scrolling.
            TabRow(selectedTabIndex = section.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                ProjectSection.entries.forEach { s ->
                    Tab(
                        selected = s == section,
                        onClick = { viewModel.setSection(s) },
                        text = { Text(s.label, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.testTag("detail_tab_${s.ordinal}")
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).nestedScroll(collapseOnScroll)) {
                when (section) {
                    ProjectSection.SUMMARY -> {
                        val latestReport = weeklyReports.maxByOrNull { it.createdAtTimestamp }
                        val today = LocalDate.now()
                        SummarySection(
                            project = currentProject,
                            flags = ProjectAttention.flags(
                                currentProject, pendingDocuments, billedTotal,
                                latestReport?.let { Instant.ofEpochMilli(it.createdAtTimestamp).atZone(ZoneId.systemDefault()).toLocalDate() },
                                today
                            ),
                            documents = pendingDocuments,
                            latestReport = latestReport,
                            // Same rule as the Issues tab's "Resolved" filter.
                            openIssues = projectIssues.count {
                                !it.status.contains("Resolved", ignoreCase = true) && !it.status.contains("Closed", ignoreCase = true)
                            },
                            canEditProject = currentUser.role.hasPermission(Permission.EDIT_PROJECT),
                            onEditProject = { projectToEdit = currentProject },
                            currentUserRole = currentUser.role,
                            onAddOrder = onAddDocumentClick,
                            onUpdateOrderStatus = { doc, status, remarks -> viewModel.updatePendingDocumentStatus(doc, status, remarks) },
                            onDeleteOrder = { viewModel.deletePendingDocument(it) },
                            today = today
                        )
                    }
                    ProjectSection.PROGRESS -> Column {
                        val chips = SectionChips.progress(isHousingProject)
                        val chip = SectionChips.chipOrFirst(chips, progressChip)
                        // A new chip may not scroll, so open the header again.
                        ChipRow(chips, chip) { progressChip = it; headerCollapsed = false }
                        Box(modifier = Modifier.weight(1f)) {
                            when (chip) {
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
                            "Lots" -> SubdivisionPlanTab(
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
                                onDetectLots = viewModel::detectSdpLots,
                                onAddDetectedLots = viewModel::addDetectedSdpLots,
                                onTraceLot = viewModel::traceSdpLot,
                                onRemoveDetectedLots = viewModel::removeDetectedSdpLots,
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
                                "Photos" -> PhotoGalleryTab(weeklyReports = weeklyReports)
                                "Weather" -> WeatherChartTab(
                                    dailyWeatherLogs = dailyWeatherLogs,
                                    weeklyReports = weeklyReports
                                )
                                "Issues" -> IssuesTab(
                                    issues = projectIssues,
                                    currentUserRole = currentUser.role,
                                    onAddClick = onAddIssueClick,
                                    onEditClick = { issue -> viewModel.projectIssueToEdit.value = issue; onAddIssueClick() },
                                    onDeleteClick = { issue -> viewModel.deleteProjectIssue(issue) }
                                )
                            }
                        }
                    }
                    ProjectSection.MONEY -> MoneySection(
                        payments = projectPayments,
                        contractCost = contractCost,
                        actualPercent = currentProject.actualAccomplishment,
                        documents = pendingDocuments,
                        canAddVariationOrder = currentUser.role.hasPermission(Permission.UPLOAD_DOCUMENT),
                        onAddVariationOrder = onAddVariationOrderClick,
                        currentUserRole = currentUser.role,
                        onUpdateOrderStatus = { doc, status, remarks -> viewModel.updatePendingDocumentStatus(doc, status, remarks) },
                        onDeleteOrder = { viewModel.deletePendingDocument(it) },
                        onAddPayment = { name, dvNo, date, period, gross, pct, balAmt, balPct, fileUrl ->
                            viewModel.addProjectPayment(currentProject.id, name, dvNo, date, period, gross, pct, balAmt, balPct, fileUrl)
                        },
                        onUpdatePayment = { payment ->
                            viewModel.updateProjectPayment(payment)
                        },
                        onDeletePayment = { viewModel.deleteProjectPayment(it.id) }
                    )
                    ProjectSection.DOCUMENTS -> Column {
                        val chips = SectionChips.documents(canViewAuditLogs)
                        val chip = SectionChips.chipOrFirst(chips, documentsChip)
                        ChipRow(chips, chip) { documentsChip = it; headerCollapsed = false }
                        Box(modifier = Modifier.weight(1f)) {
                            when (chip) {
                                "Related Files" -> RelatedFilesList(
                                    documents = pendingDocuments,
                                    currentUserRole = currentUser.role,
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
                                "Audit log" -> AuditLogTab(auditLogs = auditLogs)
                            }
                        }
                    }
                }
            }
        }
    }

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
            onEdit = { name, location, implementingOffice, contractor, scopeOfWork, projectType, landArea, numberOfUnits, cost, duration, dateStarted, completionDateOriginal, assignedStaff, assignedUsers ->
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
