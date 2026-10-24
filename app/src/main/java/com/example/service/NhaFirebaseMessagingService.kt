package com.example.service

import android.util.Log
import com.example.util.AppNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NhaFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "NhaFcmService"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "New FCM Registration Token: $token")
        registerTokenToFirestore(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(tag, "FCM Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val title = remoteMessage.notification?.title ?: data["title"] ?: "NHA Monitor Alert"
        val message = remoteMessage.notification?.body ?: data["message"] ?: "New activity recorded in project monitor."
        val eventId = data["eventId"]

        Log.d(tag, "Processing push notification: title=$title, eventId=$eventId")

        // Trigger native Android Status Bar Notification
        AppNotificationManager.sendAdminNotification(
            context = applicationContext,
            title = title,
            message = message
        )
    }

    private fun registerTokenToFirestore(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val firestore = FirebaseFirestore.getInstance()

        val tokenData = mapOf(
            "token" to token,
            "updatedAt" to System.currentTimeMillis(),
            "device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "uid" to currentUser.uid
        )

        firestore.collection("users")
            .document(currentUser.uid)
            .collection("fcmTokens")
            .document(token.takeLast(20))
            .set(tokenData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(tag, "FCM Token registered successfully in Firestore for uid: ${currentUser.uid}")
            }
            .addOnFailureListener { ex ->
                Log.e(tag, "Failed to register FCM Token in Firestore", ex)
            }
    }
}
