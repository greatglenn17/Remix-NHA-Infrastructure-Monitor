package com.example.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The core document checklist older versions gave every new project, and cleanups of
 * the sample values older versions filled in (made-up statuses and remarks on that
 * checklist, and made-up monthly report signatories).
 */
object SampleData {

    val CORE_CHECKLIST = listOf(
        "Contractor Notice to Proceed (NTP)",
        "PERT/CPM Schedule Approval",
        "DOLE Approved Safety & Health Plan",
        "Environmental Compliance Certificate (ECC)",
        "Quality Control Material Testing Plan",
        "As-Built Plans & Turnover Docs"
    )

    /** Document name to the status and remarks older versions seeded it with. */
    private val OLD_CHECKLIST_SEEDS = mapOf(
        "Contractor Notice to Proceed (NTP)" to ("Approved" to "Issued"),
        "PERT/CPM Schedule Approval" to ("Pending" to "For review"),
        "DOLE Approved Safety & Health Plan" to ("Submitted" to "Awaiting DOLE clearance"),
        "Environmental Compliance Certificate (ECC)" to ("Pending" to "DENR processing"),
        "Quality Control Material Testing Plan" to ("Pending" to "For submission"),
        "As-Built Plans & Turnover Docs" to ("Pending" to "Completion stage requirement")
    )

    /**
     * Resets checklist items nobody has touched (still the seeded status and remarks, no
     * file attached) to Pending with no remarks, and clears the sample signatories from
     * monthly reports. Runs once per phone; the updates sync like any other edit.
     */
    suspend fun clearFromExistingRecords(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("sample_data", Context.MODE_PRIVATE)
        if (prefs.getBoolean("cleared_v1", false)) return@withContext
        val sql = db.openHelper.writableDatabase
        db.runInTransaction {
            for ((name, seed) in OLD_CHECKLIST_SEEDS) {
                sql.execSQL(
                    "UPDATE pending_documents SET status = 'Pending', remarks = '' " +
                        "WHERE isCoreChecklist = 1 AND documentName = ? AND status = ? AND remarks = ? AND fileUrl = ''",
                    arrayOf(name, seed.first, seed.second)
                )
            }
            sql.execSQL("UPDATE monthly_reports SET preparedByName = '' WHERE preparedByName = 'Engr. Juan Dela Cruz'")
            sql.execSQL(
                "UPDATE monthly_reports SET checkedByName = '', checkedByStatus = 'Pending Review' " +
                    "WHERE checkedByName = 'Engr. Roberto Santos' AND checkedByStatus = 'Reviewed'"
            )
            sql.execSQL(
                "UPDATE monthly_reports SET notedByName = '', notedByStatus = 'Pending Review' " +
                    "WHERE notedByName = 'Director Maria Perez' AND notedByStatus = 'Noted'"
            )
        }
        prefs.edit().putBoolean("cleared_v1", true).apply()
    }

    /**
     * Deletes core checklist items nobody filled in (Pending, no remarks, no file); filled-in
     * ones stay as related files. Runs every launch so items synced in from older phones go
     * too; the deletes sync like any other edit.
     */
    suspend fun removeUntouchedCoreChecklist(db: AppDatabase) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM pending_documents WHERE isCoreChecklist = 1 AND status = 'Pending' AND remarks = '' AND fileUrl = '' " +
                "AND documentName IN (${CORE_CHECKLIST.joinToString { "?" }})",
            CORE_CHECKLIST.toTypedArray()
        )
    }
}
