package com.example.data.repository

import com.example.data.local.ProjectDao
import com.example.data.local.ReportDao
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val reportDao: ReportDao
) {
    private fun validatePermission(role: UserRole?, permission: Permission) {
        val activeRole = role ?: UserRole.SUPER_ADMIN
        if (!activeRole.hasPermission(permission)) {
            throw SecurityException("Access Denied (Repository Layer): Role '${activeRole.label}' lacks permission '${permission.name}'.")
        }
    }

    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()

    fun getProjectById(id: Long): Flow<Project?> = projectDao.getProjectById(id)

    suspend fun insertProject(project: Project, role: UserRole? = null): Long {
        validatePermission(role, Permission.CREATE_PROJECT)
        return projectDao.insertProject(project)
    }

    suspend fun updateProject(project: Project, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_PROJECT)
        projectDao.updateProject(project)
    }

    suspend fun deleteProjectById(id: Long, role: UserRole? = null) {
        validatePermission(role, Permission.DELETE_PROJECT)
        projectDao.deleteProjectById(id)
    }

    // Time Extensions
    fun getTimeExtensions(projectId: Long): Flow<List<TimeExtension>> = projectDao.getTimeExtensionsForProject(projectId)
    suspend fun insertTimeExtension(extension: TimeExtension, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_PROJECT)
        projectDao.insertTimeExtension(extension)
    }

    // Variation Orders
    fun getVariationOrders(projectId: Long): Flow<List<VariationOrder>> = projectDao.getVariationOrdersForProject(projectId)
    suspend fun insertVariationOrder(vo: VariationOrder, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_PROJECT)
        projectDao.insertVariationOrder(vo)
    }

    // Work Suspensions
    fun getWorkSuspensionOrders(projectId: Long): Flow<List<WorkSuspensionOrder>> = projectDao.getWorkSuspensionOrdersForProject(projectId)
    suspend fun insertWorkSuspensionOrder(so: WorkSuspensionOrder, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_PROJECT)
        projectDao.insertWorkSuspensionOrder(so)
    }

    fun getWorkResumptionLogs(projectId: Long): Flow<List<WorkResumptionLog>> = projectDao.getWorkResumptionLogsForProject(projectId)
    suspend fun insertWorkResumptionLog(log: WorkResumptionLog, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_PROJECT)
        projectDao.insertWorkResumptionLog(log)
    }

    // Pending Documents
    fun getPendingDocuments(projectId: Long): Flow<List<PendingDocument>> = projectDao.getPendingDocumentsForProject(projectId)
    fun getAllPendingDocuments(): Flow<List<PendingDocument>> = projectDao.getAllPendingDocuments()
    fun getDashboardSummaryStats(): Flow<com.example.data.model.DashboardSummaryStats> = projectDao.getDashboardSummaryStats()

    suspend fun insertPendingDocument(doc: PendingDocument, role: UserRole? = null) {
        validatePermission(role, Permission.UPLOAD_DOCUMENT)
        projectDao.insertPendingDocument(doc)
    }

    suspend fun updatePendingDocument(doc: PendingDocument, role: UserRole? = null) {
        validatePermission(role, Permission.UPLOAD_DOCUMENT)
        projectDao.updatePendingDocument(doc)
    }

    suspend fun deletePendingDocument(id: Long, role: UserRole? = null) {
        validatePermission(role, Permission.UPLOAD_DOCUMENT)
        projectDao.deletePendingDocument(id)
    }

    // Weekly Reports
    fun getWeeklyReports(projectId: Long): Flow<List<WeeklyReport>> = reportDao.getWeeklyReportsForProject(projectId)
    suspend fun insertWeeklyReport(report: WeeklyReport, role: UserRole? = null): Long {
        validatePermission(role, Permission.SUBMIT_REPORT)
        return reportDao.insertWeeklyReport(report)
    }

    suspend fun updateWeeklyReport(report: WeeklyReport, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_REPORT)
        reportDao.updateWeeklyReport(report)
    }

    suspend fun deleteWeeklyReport(id: Long, role: UserRole? = null) {
        validatePermission(role, Permission.DELETE_REPORT)
        reportDao.deleteWeeklyReport(id)
    }

    // Daily Weather
    fun getDailyWeather(projectId: Long): Flow<List<DailyHourlyWeather>> = reportDao.getDailyWeatherForProject(projectId)
    fun getDailyWeatherForMonth(projectId: Long, monthPrefix: String): Flow<List<DailyHourlyWeather>> = reportDao.getDailyWeatherForProjectAndMonth(projectId, monthPrefix)

    suspend fun insertDailyWeather(weather: DailyHourlyWeather, role: UserRole? = null) {
        validatePermission(role, Permission.SUBMIT_WEATHER)
        reportDao.insertDailyWeather(weather)
    }

    suspend fun insertDailyWeatherList(list: List<DailyHourlyWeather>, role: UserRole? = null) {
        validatePermission(role, Permission.SUBMIT_WEATHER)
        reportDao.insertDailyWeatherList(list)
    }

    // Monthly Reports
    fun getMonthlyReports(projectId: Long): Flow<List<MonthlyReport>> = reportDao.getMonthlyReportsForProject(projectId)
    suspend fun insertMonthlyReport(report: MonthlyReport, role: UserRole? = null): Long {
        validatePermission(role, Permission.SUBMIT_REPORT)
        return reportDao.insertMonthlyReport(report)
    }

    suspend fun updateMonthlyReport(report: MonthlyReport, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_REPORT)
        reportDao.updateMonthlyReport(report)
    }

    suspend fun deleteMonthlyReport(id: Long, role: UserRole? = null) {
        validatePermission(role, Permission.DELETE_REPORT)
        reportDao.deleteMonthlyReport(id)
    }

    // Payments
    fun getProjectPaymentsForProject(projectId: Long): Flow<List<ProjectPayment>> = projectDao.getProjectPaymentsForProject(projectId)

    suspend fun insertProjectPayment(payment: ProjectPayment, role: UserRole? = null) {
        validatePermission(role, Permission.MANAGE_PAYMENTS)
        projectDao.insertProjectPayment(payment)
    }

    suspend fun updateProjectPayment(payment: ProjectPayment, role: UserRole? = null) {
        validatePermission(role, Permission.MANAGE_PAYMENTS)
        projectDao.updateProjectPayment(payment)
    }

    suspend fun deleteProjectPayment(id: Long, role: UserRole? = null) {
        validatePermission(role, Permission.MANAGE_PAYMENTS)
        projectDao.deleteProjectPayment(id)
    }

    // Project Issues
    fun getProjectIssues(projectId: Long): Flow<List<ProjectIssue>> = projectDao.getProjectIssuesForProject(projectId)

    suspend fun insertProjectIssue(issue: ProjectIssue, role: UserRole? = null) {
        validatePermission(role, Permission.LOG_ISSUE)
        projectDao.insertProjectIssue(issue)
    }

    suspend fun updateProjectIssue(issue: ProjectIssue, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_ISSUE)
        projectDao.updateProjectIssue(issue)
    }

    suspend fun deleteProjectIssue(id: Long, role: UserRole? = null) {
        validatePermission(role, Permission.DELETE_ISSUE)
        projectDao.deleteProjectIssue(id)
    }

    // Audit Logs
    fun getAuditLogs(projectId: Long): Flow<List<AuditLog>> = projectDao.getAuditLogsForProject(projectId)
    fun getAllAuditLogs(): Flow<List<AuditLog>> = projectDao.getAllAuditLogs()

    suspend fun insertAuditLog(log: AuditLog) = projectDao.insertAuditLog(log)

    // SDP Plans
    fun getSdpPlansForProject(projectId: Long): Flow<List<SdpPlan>> = projectDao.getSdpPlansForProject(projectId)
    fun getActiveSdpPlanForProject(projectId: Long): Flow<SdpPlan?> = projectDao.getActiveSdpPlanForProject(projectId)

    suspend fun insertSdpPlan(sdpPlan: SdpPlan, role: UserRole? = null): Long {
        validatePermission(role, Permission.MANAGE_SDP_PLANS)
        projectDao.deactivateAllSdpPlansForProject(sdpPlan.projectId)
        return projectDao.insertSdpPlan(sdpPlan)
    }

    suspend fun setActiveSdpPlan(projectId: Long, sdpPlanId: Long, role: UserRole? = null) {
        validatePermission(role, Permission.MANAGE_SDP_PLANS)
        projectDao.setActiveSdpPlan(projectId, sdpPlanId)
    }

    // SDP Lots
    fun getActiveLotsForPlan(sdpPlanId: Long): Flow<List<SdpLot>> = projectDao.getActiveLotsForPlan(sdpPlanId)

    suspend fun insertSdpLot(lot: SdpLot, role: UserRole? = null): Long {
        validatePermission(role, Permission.EDIT_PROJECT)
        return projectDao.insertSdpLot(lot)
    }

    suspend fun updateSdpLot(lot: SdpLot, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_PROJECT)
        projectDao.updateSdpLot(lot)
    }

    suspend fun deactivateSdpLot(lotId: Long, modifiedBy: String, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_PROJECT)
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
        projectDao.deactivateSdpLot(lotId, modifiedBy, date)
    }

    // SDP Roads
    fun getActiveRoadsForPlan(sdpPlanId: Long): Flow<List<SdpRoad>> = projectDao.getActiveRoadsForPlan(sdpPlanId)

    suspend fun insertSdpRoad(road: SdpRoad, role: UserRole? = null): Long {
        validatePermission(role, Permission.EDIT_PROJECT)
        return projectDao.insertSdpRoad(road)
    }

    suspend fun updateSdpRoad(road: SdpRoad, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_PROJECT)
        projectDao.updateSdpRoad(road)
    }

    suspend fun deactivateSdpRoad(roadId: Long, modifiedBy: String, role: UserRole? = null) {
        validatePermission(role, Permission.EDIT_PROJECT)
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
        projectDao.deactivateSdpRoad(roadId, modifiedBy, date)
    }

    // SDP Lot Progress
    fun getLotProgressForPlan(sdpPlanId: Long): Flow<List<SdpLotProgress>> = projectDao.getLotProgressForPlan(sdpPlanId)

    suspend fun insertOrUpdateLotProgress(progress: SdpLotProgress, role: UserRole? = null): Long {
        validatePermission(role, Permission.EDIT_PROJECT)
        return projectDao.insertOrUpdateLotProgress(progress)
    }

    // SDP Lot Inspection History
    fun getInspectionsForLot(sdpLotId: Long): Flow<List<SdpLotInspection>> = projectDao.getInspectionsForLot(sdpLotId)

    suspend fun insertSdpLotInspection(inspection: SdpLotInspection, role: UserRole? = null): Long {
        validatePermission(role, Permission.EDIT_PROJECT)
        return projectDao.insertSdpLotInspection(inspection)
    }
}
