package com.example.data.local

import com.example.data.model.*

object PrepopulateData {
    suspend fun populateDatabase(projectDao: ProjectDao, reportDao: ReportDao) {
        try {
            projectDao.deleteAllProjects()
        } catch (e: Exception) {
            android.util.Log.e("PrepopulateData", "Error clearing placeholder projects", e)
        }
    }
}
