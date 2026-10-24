package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object AppNotificationManager {

    private const val CHANNEL_ID = "nha_super_admin_alerts"
    private const val CHANNEL_NAME = "Super Admin Activity Notifications"
    private const val CHANNEL_DESC = "Real-time alerts for project creation, field entries, and user registrations."
    private var notificationIdCounter = 1000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendAdminNotification(
        context: Context,
        title: String,
        message: String
    ) = post(context, notificationIdCounter++, title, message)

    /**
     * One notification per project: the FCM push and the in-app Firestore listener can both
     * report the same new project, and a shared id makes the second one replace the first.
     */
    fun sendProjectNotification(
        context: Context,
        projectUuid: String,
        title: String,
        message: String
    ) = post(context, "project:$projectUuid".hashCode(), title, message)

    private fun post(context: Context, notificationId: Int, title: String, message: String) {
        try {
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notification)
        } catch (e: Exception) {
            android.util.Log.e("AppNotificationManager", "Error sending notification: ${e.message}", e)
        }
    }
}
