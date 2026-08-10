package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY id ASC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectById(id: Long): Flow<Project?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectWithDetails(id: Long): Flow<ProjectWithDetails?>

    @Transaction
    @Query("SELECT * FROM projects ORDER BY id ASC")
    fun getAllProjectsWithDetails(): Flow<List<ProjectWithDetails>>

    // Inspections
    @Query("SELECT * FROM project_inspections WHERE projectId = :projectId ORDER BY inspectionDate DESC")
    fun getInspectionsForProject(projectId: Long): Flow<List<ProjectInspection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspection(inspection: ProjectInspection)

    // Images
    @Query("SELECT * FROM project_images WHERE projectId = :projectId ORDER BY id DESC")
    fun getImagesForProject(projectId: Long): Flow<List<ProjectImage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ProjectImage)

    // Sub logs
    @Query("SELECT * FROM time_extensions WHERE projectId = :projectId ORDER BY extensionNo ASC")
    fun getTimeExtensionsForProject(projectId: Long): Flow<List<TimeExtension>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeExtension(timeExtension: TimeExtension)

    @Query("SELECT * FROM variation_orders WHERE projectId = :projectId ORDER BY voNo ASC")
    fun getVariationOrdersForProject(projectId: Long): Flow<List<VariationOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariationOrder(variationOrder: VariationOrder)

    @Query("SELECT * FROM work_suspensions WHERE projectId = :projectId ORDER BY id ASC")
    fun getWorkSuspensionOrdersForProject(projectId: Long): Flow<List<WorkSuspensionOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkSuspensionOrder(workSuspensionOrder: WorkSuspensionOrder)

    @Query("SELECT * FROM work_resumptions WHERE projectId = :projectId ORDER BY id ASC")
    fun getWorkResumptionLogsForProject(projectId: Long): Flow<List<WorkResumptionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkResumptionLog(workResumptionLog: WorkResumptionLog)

    @Query("SELECT * FROM pending_documents WHERE projectId = :projectId ORDER BY isCoreChecklist DESC, id ASC")
    fun getPendingDocumentsForProject(projectId: Long): Flow<List<PendingDocument>>
    @Query("SELECT * FROM pending_documents")
    fun getAllPendingDocuments(): Flow<List<PendingDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDocument(pendingDocument: PendingDocument)

    @Update
    suspend fun updatePendingDocument(pendingDocument: PendingDocument)

    @Query("DELETE FROM pending_documents WHERE id = :id")
    suspend fun deletePendingDocument(id: Long)

    @Query("SELECT * FROM project_payments WHERE projectId = :projectId ORDER BY id ASC")
    fun getProjectPaymentsForProject(projectId: Long): Flow<List<ProjectPayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectPayment(payment: ProjectPayment)

    @Update
    suspend fun updateProjectPayment(payment: ProjectPayment)

    @Query("DELETE FROM project_payments WHERE id = :id")
    suspend fun deleteProjectPayment(id: Long)

    @Query("SELECT * FROM project_issues WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getProjectIssuesForProject(projectId: Long): Flow<List<ProjectIssue>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectIssue(issue: ProjectIssue)

    @Update
    suspend fun updateProjectIssue(issue: ProjectIssue)

    @Query("DELETE FROM project_issues WHERE id = :id")
    suspend fun deleteProjectIssue(id: Long)

    @Query("SELECT * FROM audit_logs WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getAuditLogsForProject(projectId: Long): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog)

    // Cloud Backup Queries
    @Query("SELECT * FROM time_extensions")
    fun getAllTimeExtensions(): Flow<List<TimeExtension>>

    @Query("SELECT * FROM variation_orders")
    fun getAllVariationOrders(): Flow<List<VariationOrder>>

    @Query("SELECT * FROM work_suspensions")
    fun getAllWorkSuspensions(): Flow<List<WorkSuspensionOrder>>

    @Query("SELECT * FROM work_resumptions")
    fun getAllWorkResumptions(): Flow<List<WorkResumptionLog>>

    @Query("SELECT * FROM project_inspections")
    fun getAllInspections(): Flow<List<ProjectInspection>>

    @Query("SELECT * FROM project_images")
    fun getAllImages(): Flow<List<ProjectImage>>

    @Query("SELECT * FROM project_payments")
    fun getAllPayments(): Flow<List<ProjectPayment>>

    @Query("SELECT * FROM project_issues")
    fun getAllIssues(): Flow<List<ProjectIssue>>

    @Query("SELECT * FROM sdp_plans")
    fun getAllSdpPlans(): Flow<List<SdpPlan>>

    @Query("SELECT * FROM sdp_lots")
    fun getAllSdpLots(): Flow<List<SdpLot>>

    @Query("SELECT * FROM sdp_roads")
    fun getAllSdpRoads(): Flow<List<SdpRoad>>

    @Query("SELECT * FROM sdp_lot_progress")
    fun getAllSdpLotProgress(): Flow<List<SdpLotProgress>>

    @Query("SELECT * FROM sdp_lot_inspections")
    fun getAllSdpLotInspections(): Flow<List<SdpLotInspection>>

    @Query("""
        SELECT 
            (SELECT COUNT(*) FROM projects WHERE status IN ('On-going', 'Behind Schedule')) AS activeProjectsCount,
            (SELECT COUNT(*) FROM projects WHERE status = 'Behind Schedule' OR (status = 'On-going' AND (actualAccomplishment - targetAccomplishment) < 0)) AS slippageProjectsCount,
            (SELECT COUNT(*) FROM pending_documents WHERE status = 'Pending') AS pendingDocsCount
    """)
    fun getDashboardSummaryStats(): Flow<com.example.data.model.DashboardSummaryStats>

    // SDP Plans
    @Query("SELECT * FROM sdp_plans WHERE projectId = :projectId ORDER BY version DESC")
    fun getSdpPlansForProject(projectId: Long): Flow<List<SdpPlan>>

    @Query("SELECT * FROM sdp_plans WHERE projectId = :projectId AND isActive = 1 LIMIT 1")
    fun getActiveSdpPlanForProject(projectId: Long): Flow<SdpPlan?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSdpPlan(sdpPlan: SdpPlan): Long

    @Query("UPDATE sdp_plans SET isActive = 0 WHERE projectId = :projectId")
    suspend fun deactivateAllSdpPlansForProject(projectId: Long)

    @Query("UPDATE sdp_plans SET isActive = CASE WHEN id = :sdpPlanId THEN 1 ELSE 0 END WHERE projectId = :projectId")
    suspend fun setActiveSdpPlan(projectId: Long, sdpPlanId: Long)

    // SDP Lots
    @Query("SELECT * FROM sdp_lots WHERE sdpPlanId = :sdpPlanId AND isActive = 1 ORDER BY id ASC")
    fun getActiveLotsForPlan(sdpPlanId: Long): Flow<List<SdpLot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSdpLot(lot: SdpLot): Long

    @Update
    suspend fun updateSdpLot(lot: SdpLot)

    @Query("UPDATE sdp_lots SET isActive = 0, lastModifiedBy = :modifiedBy, lastModifiedDate = :modifiedDate WHERE id = :lotId")
    suspend fun deactivateSdpLot(lotId: Long, modifiedBy: String, modifiedDate: String)

    // SDP Roads
    @Query("SELECT * FROM sdp_roads WHERE sdpPlanId = :sdpPlanId AND isActive = 1 ORDER BY id ASC")
    fun getActiveRoadsForPlan(sdpPlanId: Long): Flow<List<SdpRoad>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSdpRoad(road: SdpRoad): Long

    @Update
    suspend fun updateSdpRoad(road: SdpRoad)

    @Query("UPDATE sdp_roads SET isActive = 0, lastModifiedBy = :modifiedBy, lastModifiedDate = :modifiedDate WHERE id = :roadId")
    suspend fun deactivateSdpRoad(roadId: Long, modifiedBy: String, modifiedDate: String)

    // SDP Lot Progress
    @Query("SELECT * FROM sdp_lot_progress WHERE sdpPlanId = :sdpPlanId")
    fun getLotProgressForPlan(sdpPlanId: Long): Flow<List<SdpLotProgress>>

    @Query("SELECT * FROM sdp_lot_progress WHERE sdpLotId = :sdpLotId LIMIT 1")
    suspend fun getLotProgressByLotId(sdpLotId: Long): SdpLotProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLotProgress(progress: SdpLotProgress): Long

    // SDP Lot Inspection History
    @Query("SELECT * FROM sdp_lot_inspections WHERE sdpLotId = :sdpLotId ORDER BY inspectionTimestamp DESC")
    fun getInspectionsForLot(sdpLotId: Long): Flow<List<SdpLotInspection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSdpLotInspection(inspection: SdpLotInspection): Long
}
