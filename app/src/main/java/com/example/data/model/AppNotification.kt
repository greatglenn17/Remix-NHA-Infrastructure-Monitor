package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val actorName: String,
    val actorRole: String, // e.g. "FIELD_ENGINEER"
    val targetRole: String = "ADMIN", // Visible to SUPER_ADMIN & ENGINEER_ADMIN
    val projectId: Long? = null,
    val projectName: String = "",
    val actionType: String, // "CREATE_PROJECT", "UPDATE_PROJECT", "ADD_WEEKLY_REPORT", "ADD_PAYMENT", etc.
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
