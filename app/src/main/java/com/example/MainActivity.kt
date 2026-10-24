package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.util.AppNotificationManager
import com.example.util.ProjectSchedule
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.data.model.MonthlyReport
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.detail.ProjectDetailScreen
import com.example.ui.dialogs.*
import com.example.ui.login.LoginScreen
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.NHATheme
import com.example.ui.viewmodel.ProjectViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ProjectViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppNotificationManager.createNotificationChannel(this)
        // Android 13+ blocks every notification (push and in-app) until the user allows it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        setContent {
            val appThemeMode by viewModel.appThemeMode.collectAsState()
            NHATheme(themeMode = appThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    color = BackgroundLight
                ) {
                    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
                    val authLoading by viewModel.authLoading.collectAsState()
                    val authError by viewModel.authError.collectAsState()
                    val authSuccessMessage by viewModel.authSuccessMessage.collectAsState()
                    val selectedProjectId by viewModel.selectedProjectId.collectAsState()

                    if (!isLoggedIn) {
                        LoginScreen(
                            onLoginSuccess = { email, password, displayName, role, isSignUp, position, office ->
                                viewModel.authenticateWithFirebase(email, password, displayName, role, isSignUp, position, office)
                            },
                            onSendPasswordReset = { email, newPassword ->
                                viewModel.sendPasswordReset(email, newPassword)
                            },
                            isLoading = authLoading,
                            errorMessage = authError,
                            successMessage = authSuccessMessage,
                            onClearError = { viewModel.clearAuthError() },
                            onClearSuccessMessage = { viewModel.clearAuthSuccessMessage() }
                        )
                    } else {
                        // Dialog States
                        var showCreateProjectDialog by remember { mutableStateOf(false) }
                        var showAddWeeklyReportDialog by remember { mutableStateOf(false) }
                        var showAddMonthlyReportDialog by remember { mutableStateOf(false) }
                        var showAddTimeExtensionDialog by remember { mutableStateOf(false) }
                        var showAddWorkSuspensionDialog by remember { mutableStateOf(false) }
                        var showAddWorkResumptionDialog by remember { mutableStateOf(false) }
                        var showAddDocumentDialog by remember { mutableStateOf(false) }
                        var addDocumentInitialType by remember { mutableStateOf("Custom Document") }
                        var showAddIssueDialog by remember { mutableStateOf(false) }

                        // Navigation: Null selected project -> Dashboard Screen; Selected -> Detail Screen
                        if (selectedProjectId == null) {
                            DashboardScreen(
                                viewModel = viewModel,
                                onProjectClick = { project ->
                                    viewModel.selectProject(project.id)
                                },
                                onCreateProjectClick = {
                                    showCreateProjectDialog = true
                                }
                            )
                        } else {
                            ProjectDetailScreen(
                                viewModel = viewModel,
                                onBackClick = {
                                    viewModel.selectProject(null)
                                },
                                onAddWeeklyReportClick = { showAddWeeklyReportDialog = true },
                                onAddMonthlyReportClick = { showAddMonthlyReportDialog = true },
                                onAddTimeExtensionClick = { showAddTimeExtensionDialog = true },
                                onAddVariationOrderClick = { addDocumentInitialType = "Variation Order"; showAddDocumentDialog = true },
                                onAddWorkSuspensionClick = { showAddWorkSuspensionDialog = true },
                                onAddWorkResumptionClick = { showAddWorkResumptionDialog = true },
                                onAddDocumentClick = { addDocumentInitialType = "Custom Document"; showAddDocumentDialog = true },
                                onAddIssueClick = { showAddIssueDialog = true }
                            )
                        }

                        // Render Active Dialogs
                        if (showCreateProjectDialog) {
                            CreateProjectDialog(
                                onDismiss = { showCreateProjectDialog = false },
                                onCreate = { name, location, office, contractor, scope, type, landArea, numberOfUnits, cost, duration, dateStarted, completionDate, assignedStaff ->
                                    viewModel.createProject(
                                        name = name,
                                        location = location,
                                        implementingOffice = office,
                                        contractor = contractor,
                                        scopeOfWork = scope,
                                        projectType = type,
                                        landArea = landArea,
                                        numberOfUnits = numberOfUnits,
                                        contractCostOriginal = cost,
                                        contractDurationDays = duration,
                                        dateStarted = dateStarted,
                                        completionDateOriginal = completionDate,
                                        assignedStaff = assignedStaff
                                    )
                                    showCreateProjectDialog = false
                                }
                            )
                        }

                    if (showAddWeeklyReportDialog) {
                        selectedProjectId?.let { pId ->
                            val selectedProject = viewModel.selectedProject.value
                            val reportToEdit = viewModel.weeklyReportToEdit.value
                            AddWeeklyReportDialog(
                                project = selectedProject,
                                projectId = pId,
                                existingReport = reportToEdit,
                                onDismiss = { 
                                    showAddWeeklyReportDialog = false
                                    viewModel.weeklyReportToEdit.value = null
                                },
                                onSubmit = { week, target, actual, activities, photoUrl, dailyWeatherMap, dailyWeatherDates, manpowerJson, equipmentJson, issuesJson, accItemsJson ->
                                    val (daysElapsed, remainingDays) = if (selectedProject != null) {
                                        ProjectSchedule.elapsedAndRemaining(
                                            dateStarted = selectedProject.dateStarted,
                                            completionDate = ProjectSchedule.effectiveCompletion(
                                                selectedProject.completionDateOriginal,
                                                selectedProject.completionDateRevised
                                            ),
                                            asOf = dailyWeatherDates.values.maxOrNull().orEmpty()
                                        )
                                    } else 0 to 0
                                    if (reportToEdit != null) {
                                        val updated = reportToEdit.copy(
                                            reportingWeek = week,
                                            daysElapsed = daysElapsed,
                                            remainingDays = remainingDays,
                                            targetAccomplishmentPct = target,
                                            actualAccomplishmentPct = actual,
                                            activitiesJson = activities,
                                            attachedPhotoUrlsJson = com.example.data.files.Attachments.toJson(com.example.data.files.Attachments.parseList(photoUrl)),
                                            manpowerJson = manpowerJson,
                                            equipmentJson = equipmentJson,
                                            issuesJson = issuesJson,
                                            accomplishmentItemsJson = accItemsJson
                                        )
                                        viewModel.updateWeeklyReportWithWeather(
                                            updated,
                                            dailyWeatherDates.map { (dayOfWeek, dateStr) ->
                                                val rawValue = dailyWeatherMap[dayOfWeek] ?: "FAIR"
                                                val conditions = if (rawValue.contains(",")) rawValue else List(24) { rawValue }.joinToString(",")
                                                com.example.data.model.DailyHourlyWeather(
                                                    projectId = pId,
                                                    date = dateStr,
                                                    dayOfWeek = dayOfWeek,
                                                    hourlyConditionsCsv = conditions
                                                )
                                            }
                                        )
                                        viewModel.weeklyReportToEdit.value = null
                                    } else {
                                        viewModel.submitWeeklyReportWithWeather(
                                            projectId = pId,
                                            reportingWeek = week,
                                            daysElapsed = daysElapsed,
                                            remainingDays = remainingDays,
                                            targetAccomplishmentPct = target,
                                            actualAccomplishmentPct = actual,
                                            manpowerJson = manpowerJson,
                                            equipmentJson = equipmentJson,
                                            activitiesJson = activities,
                                            issuesJson = issuesJson,
                                            accomplishmentItemsJson = accItemsJson,
                                            documentsIssuedReceivedJson = "[]",
                                            attachedPhotoUrl = photoUrl,
                                            weatherDrafts = dailyWeatherDates.map { (dayOfWeek, dateStr) ->
                                                val rawValue = dailyWeatherMap[dayOfWeek] ?: "FAIR"
                                                val conditions = if (rawValue.contains(",")) rawValue else List(24) { rawValue }.joinToString(",")
                                                com.example.data.model.DailyHourlyWeather(
                                                    projectId = pId,
                                                    date = dateStr,
                                                    dayOfWeek = dayOfWeek,
                                                    hourlyConditionsCsv = conditions
                                                )
                                            }
                                        )
                                    }
                                    showAddWeeklyReportDialog = false
                                    viewModel.weeklyReportToEdit.value = null
                                }
                            )
                        }
                    }

                    if (showAddMonthlyReportDialog) {
                        val selectedProject by viewModel.selectedProject.collectAsState(initial = null)
                        val weeklyReports by viewModel.weeklyReports.collectAsState(initial = emptyList())
                        val baseCost = selectedProject?.let { if (it.contractCostRevised > 0) it.contractCostRevised else it.contractCostOriginal } ?: 0.0
                        val reportToEdit = viewModel.monthlyReportToEdit.value
                        AddMonthlyReportDialog(
                            project = selectedProject,
                            existingReport = reportToEdit,
                            weeklyReports = weeklyReports,
                            scopeOfWork = selectedProject?.scopeOfWork ?: "Project Scope",
                            baseCost = baseCost,
                            onDismiss = { 
                                showAddMonthlyReportDialog = false
                                viewModel.monthlyReportToEdit.value = null
                            },
                            onSubmit = { reportingMonth, scopeWeight, scopeTarget, scopeActual, unworkableDays, workableDays, recommendations, paymentsJson, accItemsJson ->
                                selectedProjectId?.let { pId ->
                                    if (reportToEdit != null) {
                                        val updated = reportToEdit.copy(
                                            reportingMonth = reportingMonth,
                                            scopeWeightPct = scopeWeight,
                                            scopeTargetPct = scopeTarget,
                                            scopeActualPct = scopeActual,
                                            unworkableDaysCount = unworkableDays,
                                            workableDaysCount = workableDays,
                                            recommendations = recommendations,
                                            paymentsJson = paymentsJson,
                                            accomplishmentItemsJson = accItemsJson
                                        )
                                        viewModel.updateMonthlyReportFull(updated)
                                        viewModel.monthlyReportToEdit.value = null
                                    } else {
                                        viewModel.submitMonthlyReport(
                                            projectId = pId,
                                            reportingMonth = reportingMonth,
                                            scopeWeight = scopeWeight,
                                            scopeTarget = scopeTarget,
                                            scopeActual = scopeActual,
                                            unworkableDays = unworkableDays,
                                            workableDays = workableDays,
                                            recommendations = recommendations,
                                            paymentsJson = paymentsJson,
                                            accomplishmentItemsJson = accItemsJson
                                        )
                                    }
                                }
                                showAddMonthlyReportDialog = false
                            }
                        )
                    }

                    if (showAddTimeExtensionDialog) {
                        selectedProjectId?.let { pId ->
                            AddTimeExtensionDialog(
                                onDismiss = { showAddTimeExtensionDialog = false },
                                onSubmit = { extNo, days, period, reason, revisedDate, remarks ->
                                    viewModel.addTimeExtension(pId, extNo, days, period, reason, revisedDate, remarks)
                                    showAddTimeExtensionDialog = false
                                }
                            )
                        }
                    }

                    if (showAddWorkSuspensionDialog) {
                        selectedProjectId?.let { pId ->
                            AddWorkSuspensionDialog(
                                onDismiss = { showAddWorkSuspensionDialog = false },
                                onSubmit = { name, effectivityDate, durationDays, endDate, reason, remarks ->
                                    viewModel.addWorkSuspensionOrder(pId, name, effectivityDate, durationDays, endDate, reason, remarks)
                                    showAddWorkSuspensionDialog = false
                                }
                            )
                        }
                    }
                    if (showAddWorkResumptionDialog) {
                        selectedProjectId?.let { pId ->
                            AddWorkResumptionDialog(
                                onDismiss = { showAddWorkResumptionDialog = false },
                                onSubmit = { name, dateResumed, reason, remarks ->
                                    viewModel.addWorkResumptionLog(pId, name, dateResumed, reason, remarks)
                                    showAddWorkResumptionDialog = false
                                }
                            )
                        }
                    }

                    if (showAddDocumentDialog) {
                        selectedProjectId?.let { pId ->
                            AddCustomDocumentDialog(
                                initialType = addDocumentInitialType,
                                onDismiss = { showAddDocumentDialog = false },
                                onSubmit = { docName, remarks ->
                                    viewModel.addPendingDocument(pId, docName, remarks)
                                    showAddDocumentDialog = false
                                }
                            )
                        }
                    }

                    if (showAddIssueDialog) {
                        selectedProjectId?.let { pId ->
                            val issueToEdit = viewModel.projectIssueToEdit.value
                            AddIssueDialog(
                                existingIssue = issueToEdit,
                                onDismiss = { 
                                    showAddIssueDialog = false
                                    viewModel.projectIssueToEdit.value = null
                                },
                                onSubmit = { date, description, actionTaken, status, isCritical ->
                                    if (issueToEdit != null) {
                                        viewModel.updateProjectIssue(issueToEdit.copy(date = date, description = description, actionTaken = actionTaken, status = status, isCritical = isCritical))
                                    } else {
                                        viewModel.addProjectIssue(com.example.data.model.ProjectIssue(projectId = pId, date = date, description = description, actionTaken = actionTaken, remarks = "", loggedBy = "", status = status, isCritical = isCritical))
                                    }
                                    showAddIssueDialog = false
                                    viewModel.projectIssueToEdit.value = null
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}
