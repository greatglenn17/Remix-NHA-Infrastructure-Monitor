package com.example.data.local

import android.util.Log
import com.example.data.files.Attachments
import com.example.data.remote.ProjectReconciler

/**
 * Collapses local project rows that describe the same project (same name + location)
 * into the oldest row. Child records are moved onto the kept row before the
 * duplicate is deleted, so no inspections, payments or reports are lost.
 */
object ProjectDuplicateMerger {

    private val CHILD_TABLES = listOf(
        "pending_documents", "time_extensions", "variation_orders", "work_suspensions",
        "work_resumptions", "weekly_reports", "daily_hourly_weather", "monthly_reports",
        "project_inspections", "project_images", "project_issues", "audit_logs",
        "project_payments", "app_notifications", "sdp_plans", "sdp_lots", "sdp_roads",
        "sdp_lot_progress", "sdp_lot_inspections", "developer_requests"
    )

    /** Returns the number of duplicate rows removed. */
    suspend fun merge(db: AppDatabase): Int {
        val projects = db.projectDao().getAllProjectsList()
        val merges = ProjectReconciler.localDuplicates(
            projects.map { ProjectReconciler.LocalRef(it.id, it.projectUuid, ProjectReconciler.key(it.name, it.location)) }
        )
        if (merges.isEmpty()) return 0

        val uuidOf = projects.associate { it.id to it.projectUuid }
        val sql = db.openHelper.writableDatabase
        db.runInTransaction {
            for ((duplicateId, keepId) in merges) {
                for (table in CHILD_TABLES) {
                    // OR IGNORE: a child that would break a unique index stays on the
                    // duplicate and is removed with it by the ON DELETE CASCADE.
                    sql.execSQL("UPDATE OR IGNORE $table SET projectId = ? WHERE projectId = ?", arrayOf<Any>(keepId, duplicateId))
                }
                // The moved records' attachment references must name the surviving project.
                val from = uuidOf[duplicateId]
                val to = uuidOf[keepId]
                if (from != null && to != null) Attachments.moveProjectRefs(sql, from, to)
                sql.execSQL("DELETE FROM projects WHERE id = ?", arrayOf<Any>(duplicateId))
            }
        }
        Log.i("ProjectDuplicateMerger", "Merged ${merges.size} duplicate project rows: $merges")
        return merges.size
    }
}
