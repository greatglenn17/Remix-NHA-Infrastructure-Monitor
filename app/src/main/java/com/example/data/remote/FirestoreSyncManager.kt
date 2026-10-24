package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.Project
import com.example.data.model.SyncOutboxEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class FirestoreSyncStatus {
    object Idle : FirestoreSyncStatus()
    object Syncing : FirestoreSyncStatus()
    data class Synced(val timestamp: Long = System.currentTimeMillis()) : FirestoreSyncStatus()
    data class Error(val message: String) : FirestoreSyncStatus()
}

class FirestoreSyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val scope: CoroutineScope
) {
    private val tag = "FirestoreSyncManager"
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val _syncStatus = MutableStateFlow<FirestoreSyncStatus>(FirestoreSyncStatus.Idle)
    private var projectListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    init {
        // Self-healing auth observer: automatically starts sync the instant Firebase Auth initializes
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d(tag, "FirebaseAuth state changed: user ${user.uid} authenticated. Starting sync.")
                startRealtimeSync()
                scope.launch(Dispatchers.IO) {
                    flushOutbox()
                }
            } else {
                Log.d(tag, "FirebaseAuth state changed: no user. Stopping sync.")
                stopRealtimeSync()
            }
        }
        try {
            auth.addAuthStateListener(authStateListener!!)
        } catch (e: Exception) {
            Log.w(tag, "Failed to register authStateListener: ${e.message}")
        }
    }

    /**
     * Start real-time Firestore synchronization listeners for cross-device updates.
     */
    fun startRealtimeSync() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(tag, "No authenticated user. Skipping realtime sync.")
            return
        }

        stopRealtimeSync()

        Log.d(tag, "Starting Firestore realtime listener for projects (User: ${currentUser.uid})...")
        projectListener = firestore.collection("projects")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(tag, "Firestore listen failed.", e)
                    _syncStatus.value = FirestoreSyncStatus.Error(e.localizedMessage ?: "Sync listener error")
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    scope.launch(Dispatchers.IO) {
                        for (doc in snapshots.documents) {
                            try {
                                val projectUuid = doc.id
                                val name = doc.getString("name") ?: continue
                                val revision = doc.getLong("revision") ?: 1L
                                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                val isDeleted = doc.getBoolean("isDeleted") ?: false

                                val localProject = db.projectDao().getProjectByUuid(projectUuid)
                                if (isDeleted) {
                                    // Tombstone detected: remove from local Room cache so it disappears across all devices
                                    if (localProject != null) {
                                        db.projectDao().deleteProjectById(localProject.id)
                                        Log.d(tag, "Reconciled remote tombstone deletion for project: $name ($projectUuid)")
                                    }
                                    continue
                                }

                                if (localProject == null) {
                                    // Remote project doesn't exist locally — insert into Room
                                    val newProj = Project(
                                        projectUuid = projectUuid,
                                        name = name,
                                        location = doc.getString("location").orEmpty(),
                                        implementingOffice = doc.getString("implementingOffice") ?: "Bulacan District Office",
                                        contractor = doc.getString("contractor").orEmpty(),
                                        scopeOfWork = doc.getString("scopeOfWork").orEmpty(),
                                        projectType = doc.getString("projectType") ?: "Housing Project",
                                        landArea = doc.getString("landArea") ?: "4.5 Hectares",
                                        numberOfUnits = doc.getString("numberOfUnits") ?: "120 Housing Units",
                                        contractCostOriginal = doc.getDouble("contractCostOriginal") ?: 0.0,
                                        contractCostRevised = doc.getDouble("contractCostRevised") ?: 0.0,
                                        contractDurationDays = doc.getLong("contractDurationDays")?.toInt() ?: 0,
                                        dateStarted = doc.getString("dateStarted") ?: "2026-08-01",
                                        completionDateOriginal = doc.getString("completionDateOriginal") ?: "2026-12-31",
                                        completionDateRevised = doc.getString("completionDateRevised") ?: "2026-12-31",
                                        status = doc.getString("status") ?: "On-going",
                                        targetAccomplishment = doc.getDouble("targetAccomplishment") ?: 0.0,
                                        actualAccomplishment = doc.getDouble("actualAccomplishment") ?: 0.0,
                                        assignedStaff = doc.getString("assignedStaff") ?: "Engr. Staff",
                                        revision = revision,
                                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                        updatedAt = updatedAt,
                                        createdByUid = doc.getString("createdByUid").orEmpty(),
                                        updatedByUid = doc.getString("updatedByUid").orEmpty(),
                                        isDeleted = false
                                    )
                                    db.projectDao().insertProject(newProj)
                                    Log.d(tag, "Ingested new remote project: $name ($projectUuid)")
                                } else if (revision > localProject.revision) {
                                    // Remote version is newer — update local Room entity
                                    val updatedProj = localProject.copy(
                                        name = name,
                                        location = doc.getString("location") ?: localProject.location,
                                        implementingOffice = doc.getString("implementingOffice") ?: localProject.implementingOffice,
                                        contractor = doc.getString("contractor") ?: localProject.contractor,
                                        scopeOfWork = doc.getString("scopeOfWork") ?: localProject.scopeOfWork,
                                        contractCostRevised = doc.getDouble("contractCostRevised") ?: localProject.contractCostRevised,
                                        status = doc.getString("status") ?: localProject.status,
                                        targetAccomplishment = doc.getDouble("targetAccomplishment") ?: localProject.targetAccomplishment,
                                        actualAccomplishment = doc.getDouble("actualAccomplishment") ?: localProject.actualAccomplishment,
                                        assignedStaff = doc.getString("assignedStaff") ?: localProject.assignedStaff,
                                        revision = revision,
                                        updatedAt = updatedAt,
                                        updatedByUid = doc.getString("updatedByUid") ?: localProject.updatedByUid,
                                        isDeleted = false
                                    )
                                    db.projectDao().updateProject(updatedProj)
                                    Log.d(tag, "Updated local project from remote revision $revision: $name")
                                }
                            } catch (ex: Exception) {
                                Log.e(tag, "Error processing remote Firestore document", ex)
                            }
                        }
                    }
                }
            }
    }

    /**
     * Stop real-time Firestore listeners.
     */
    fun stopRealtimeSync() {
        projectListener?.remove()
        projectListener = null
    }

    /**
     * Process pending events in the local Room sync_outbox table and upload to Firestore.
     */
    suspend fun flushOutbox(): Result<Int> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            return@withContext Result.failure(IllegalStateException("User is not authenticated with Firebase."))
        }

        val pending = db.syncOutboxDao().pendingEventsList()
        if (pending.isEmpty()) {
            return@withContext Result.success(0)
        }

        _syncStatus.value = FirestoreSyncStatus.Syncing
        var syncedCount = 0

        for (event in pending) {
            try {
                if (event.entityType == "project") {
                    val project = db.projectDao().getProjectByUuid(event.entityUuid)
                    if (project != null) {
                        val data = hashMapOf<String, Any>(
                            "projectUuid" to project.projectUuid,
                            "name" to project.name,
                            "location" to project.location,
                            "implementingOffice" to project.implementingOffice,
                            "contractor" to project.contractor,
                            "scopeOfWork" to project.scopeOfWork,
                            "projectType" to project.projectType,
                            "landArea" to project.landArea,
                            "numberOfUnits" to project.numberOfUnits,
                            "contractCostOriginal" to project.contractCostOriginal,
                            "contractCostRevised" to project.contractCostRevised,
                            "contractDurationDays" to project.contractDurationDays,
                            "dateStarted" to project.dateStarted,
                            "completionDateOriginal" to project.completionDateOriginal,
                            "completionDateRevised" to project.completionDateRevised,
                            "status" to project.status,
                            "targetAccomplishment" to project.targetAccomplishment,
                            "actualAccomplishment" to project.actualAccomplishment,
                            "assignedStaff" to project.assignedStaff,
                            "revision" to project.revision,
                            "createdAt" to project.createdAt,
                            "updatedAt" to project.updatedAt,
                            "createdByUid" to (project.createdByUid.ifBlank { currentUser.uid }),
                            "updatedByUid" to currentUser.uid,
                            "isDeleted" to (event.operation == "DELETE" || project.isDeleted)
                        )

                        firestore.collection("projects")
                            .document(project.projectUuid)
                            .set(data, SetOptions.merge())
                            .await()

                        db.syncOutboxDao().markSynced(event.eventId)
                        syncedCount++
                    } else if (event.operation == "DELETE") {
                        // Mark as deleted in remote
                        val deleteData = hashMapOf<String, Any>(
                            "projectUuid" to event.entityUuid,
                            "isDeleted" to true,
                            "updatedAt" to System.currentTimeMillis(),
                            "updatedByUid" to currentUser.uid
                        )
                        firestore.collection("projects")
                            .document(event.entityUuid)
                            .set(deleteData, SetOptions.merge())
                            .await()

                        db.syncOutboxDao().markSynced(event.eventId)
                        syncedCount++
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to sync outbox event ${event.eventId}", e)
                db.syncOutboxDao().updateAttempt(event.eventId, "FAILED", e.localizedMessage.orEmpty())
            }
        }

        _syncStatus.value = FirestoreSyncStatus.Synced()
        Result.success(syncedCount)
    }

    /**
     * Synchronize authoritative user profile to Firestore users/{uid} collection.
     */
    suspend fun syncUserProfile(
        uid: String,
        email: String,
        displayName: String,
        role: String,
        position: String,
        office: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userProfile = hashMapOf<String, Any>(
                "uid" to uid,
                "email" to email.trim().lowercase(),
                "displayName" to displayName,
                "role" to role,
                "position" to position,
                "office" to office,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(uid)
                .set(userProfile, SetOptions.merge())
                .await()

            // Fetch and register FCM token
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        val tokenData = mapOf(
                            "token" to token,
                            "updatedAt" to System.currentTimeMillis(),
                            "device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                            "uid" to uid
                        )
                        firestore.collection("users")
                            .document(uid)
                            .collection("fcmTokens")
                            .document(token.takeLast(20))
                            .set(tokenData, SetOptions.merge())
                    }
                }
            } catch (ex: Exception) {
                Log.w(tag, "Failed to register FCM token during profile sync: ${ex.message}")
            }

            // Force refresh Auth token to receive newly minted custom claims
            try {
                auth.currentUser?.getIdToken(true)?.await()
            } catch (ex: Exception) {
                Log.w(tag, "Token refresh skipped: ${ex.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync user profile to Firestore", e)
            Result.failure(e)
        }
    }
}
