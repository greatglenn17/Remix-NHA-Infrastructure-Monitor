package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.files.Attachments
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectDuplicateMerger
import com.example.data.local.RecordSyncSchema
import com.example.data.model.Project
import com.example.data.model.SyncOutboxEvent
import com.example.util.AppNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private var recordListener: ListenerRegistration? = null
    private val recordSync = RecordSync(db)
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private val reconcileMutex = Mutex()
    private val flushMutex = Mutex()
    /** Set after the first server snapshot, so a fresh install doesn't alert for every existing project. */
    @Volatile private var hasServerSnapshot = false

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

        // Upload every change as soon as it is queued (edits, deletes, report updates),
        // not only on startup or sign-in.
        scope.launch(Dispatchers.IO) {
            db.syncOutboxDao().newPendingCount().distinctUntilChanged().collect { count ->
                if (count > 0 && auth.currentUser != null) flushOutbox()
            }
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
        hasServerSnapshot = false
        projectListener = firestore.collection("projects")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshots, e ->
                if (e != null) {
                    Log.w(tag, "Firestore listen failed.", e)
                    _syncStatus.value = FirestoreSyncStatus.Error(e.localizedMessage ?: "Sync listener error")
                    return@addSnapshotListener
                }
                if (snapshots == null || snapshots.metadata.hasPendingWrites()) return@addSnapshotListener

                val docs = snapshots.documents
                val fromServer = !snapshots.metadata.isFromCache
                scope.launch(Dispatchers.IO) {
                    reconcileMutex.withLock {
                        try {
                            reconcile(docs, fromServer, currentUser.uid)
                        } catch (ex: Exception) {
                            Log.e(tag, "Error reconciling Firestore projects", ex)
                        }
                    }
                }
            }

        // Reports, documents, issues and payments: only changed documents are applied.
        recordListener = firestore.collection("records")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(tag, "Firestore records listen failed.", e)
                    return@addSnapshotListener
                }
                val docs = snapshots?.documentChanges.orEmpty()
                    .filter { it.type != DocumentChange.Type.REMOVED && !it.document.metadata.hasPendingWrites() }
                    .map { it.document.data + ("uuid" to it.document.id) }
                if (docs.isEmpty()) return@addSnapshotListener
                scope.launch(Dispatchers.IO) {
                    reconcileMutex.withLock {
                        try {
                            recordSync.applyRemote(docs)
                        } catch (ex: Exception) {
                            Log.e(tag, "Error applying Firestore records", ex)
                        }
                    }
                }
            }
    }

    /**
     * Line local projects up with the cloud: adopt the cloud uuid for projects that
     * already exist under another uuid, insert new ones, apply newer revisions, and
     * upload local projects the cloud has never seen.
     */
    private suspend fun reconcile(docs: List<DocumentSnapshot>, fromServer: Boolean, myUid: String) {
        ProjectDuplicateMerger.merge(db)

        val projectDao = db.projectDao()
        val locals = projectDao.getAllProjectsList()
        val liveDocs = docs.filter { it.getBoolean("isDeleted") != true && it.getString("name") != null }
        val remoteRefs = docs.map { doc ->
            ProjectReconciler.RemoteRef(
                uuid = doc.id,
                key = ProjectReconciler.key(doc.getString("name").orEmpty(), doc.getString("location").orEmpty()),
                createdAt = doc.getLong("createdAt") ?: Long.MAX_VALUE,
                isDeleted = doc.getBoolean("isDeleted") == true || doc.getString("name") == null
            )
        }
        val plan = ProjectReconciler.plan(
            locals.map { ProjectReconciler.LocalRef(it.id, it.projectUuid, ProjectReconciler.key(it.name, it.location)) },
            remoteRefs
        )

        for (id in plan.deleteLocal) {
            projectDao.deleteProjectById(id)
            Log.d(tag, "Reconciled remote tombstone deletion for local project $id")
        }
        for ((id, uuid) in plan.adoptUuid) {
            projectDao.updateProjectUuid(id, uuid)
            Log.d(tag, "Local project $id now uses cloud uuid $uuid (duplicate prevented)")
        }

        for (doc in liveDocs) {
            try {
                val projectUuid = doc.id
                val name = doc.getString("name") ?: continue
                val revision = doc.getLong("revision") ?: 1L
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

                val localProject = projectDao.getProjectByUuid(projectUuid)
                if (localProject == null) {
                    if (projectUuid !in plan.insertRemote) continue // non-canonical duplicate doc
                    // Remote project doesn't exist locally — insert into Room
                    val newProj = Project(
                        projectUuid = projectUuid,
                        name = name,
                        location = doc.getString("location").orEmpty(),
                        implementingOffice = doc.getString("implementingOffice") ?: "Bulacan District Office",
                        contractor = doc.getString("contractor").orEmpty(),
                        scopeOfWork = doc.getString("scopeOfWork").orEmpty(),
                        projectType = doc.getString("projectType") ?: "Housing Project",
                        landArea = doc.getString("landArea") ?: "",
                        numberOfUnits = doc.getString("numberOfUnits") ?: "",
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
                        latestUpdatePhotoUrl = doc.getString("latestUpdatePhotoUrl").orEmpty(),
                        revision = revision,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = updatedAt,
                        createdByUid = doc.getString("createdByUid").orEmpty(),
                        updatedByUid = doc.getString("updatedByUid").orEmpty(),
                        isDeleted = false
                    )
                    projectDao.insertProject(newProj)
                    Log.d(tag, "Ingested new remote project: $name ($projectUuid)")
                    if (hasServerSnapshot && newProj.createdByUid != myUid) {
                        AppNotificationManager.sendProjectNotification(
                            context = context,
                            projectUuid = projectUuid,
                            title = "📋 New Project Created",
                            message = "$name has been added by ${newProj.assignedStaff}."
                        )
                    }
                } else if (localProject.revision > revision) {
                    enqueueProjectEvent(localProject.projectUuid, "UPDATE", localProject.revision)
                } else if (
                    // A doc without updatedAt can only be compared by revision.
                    RemoteVersion.isNewer(localProject.revision, localProject.updatedAt, revision, doc.getLong("updatedAt") ?: localProject.updatedAt) &&
                    (revision > localProject.revision || !db.syncOutboxDao().hasPendingFor(projectUuid))
                ) {
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
                        latestUpdatePhotoUrl = doc.getString("latestUpdatePhotoUrl") ?: localProject.latestUpdatePhotoUrl,
                        revision = revision,
                        updatedAt = updatedAt,
                        updatedByUid = doc.getString("updatedByUid") ?: localProject.updatedByUid,
                        isDeleted = false
                    )
                    projectDao.updateProject(updatedProj)
                    Log.d(tag, "Updated local project from remote revision $revision: $name")
                }
            } catch (ex: Exception) {
                Log.e(tag, "Error processing remote Firestore document", ex)
            }
        }

        // Only trust "missing from the cloud" when the snapshot came from the server;
        // a cache snapshot may simply not have loaded every document yet.
        if (fromServer) {
            for (uuid in plan.uploadLocal) {
                val project = projectDao.getProjectByUuid(uuid) ?: continue
                enqueueProjectEvent(uuid, "CREATE", project.revision)
            }
            if (plan.uploadLocal.isNotEmpty()) {
                Log.d(tag, "Queued ${plan.uploadLocal.size} local projects that were never uploaded")
            }
            hasServerSnapshot = true
        }
        // Records that arrived before their project can be applied now.
        recordSync.retryDeferred()
        flushOutbox()
    }

    private suspend fun enqueueProjectEvent(projectUuid: String, operation: String, revision: Long) {
        if (db.syncOutboxDao().hasPendingFor(projectUuid)) return
        db.syncOutboxDao().enqueue(
            SyncOutboxEvent(
                entityType = "project",
                entityUuid = projectUuid,
                operation = operation,
                revision = revision,
                payloadJson = "{\"projectUuid\":\"$projectUuid\"}"
            )
        )
    }

    /**
     * Stop real-time Firestore listeners.
     */
    fun stopRealtimeSync() {
        projectListener?.remove()
        projectListener = null
        recordListener?.remove()
        recordListener = null
    }

    /**
     * Process pending events in the local Room sync_outbox table and upload to Firestore.
     */
    suspend fun flushOutbox(): Result<Int> = withContext(Dispatchers.IO) { flushMutex.withLock {
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
        val uploadedRecords = mutableSetOf<String>()

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
                            "latestUpdatePhotoUrl" to project.latestUpdatePhotoUrl,
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
                    } else {
                        // The row was merged into another project or its uuid was replaced by the
                        // cloud one; the surviving row has its own event, so nothing is left to send.
                        db.syncOutboxDao().markSynced(event.eventId)
                    }
                } else if (event.entityType.startsWith(RecordSyncSchema.ENTITY_PREFIX)) {
                    // Every event uploads the row as it is now, so one upload per record covers the rest.
                    if (event.entityUuid !in uploadedRecords) {
                        val table = event.entityType.removePrefix(RecordSyncSchema.ENTITY_PREFIX)
                        val doc = recordSync.buildDocument(table, event.entityUuid, currentUser.uid)
                        val record = firestore.collection("records").document(event.entityUuid)
                        if (doc != null) {
                            record.set(doc).await()
                            uploadedRecords.add(event.entityUuid)
                        } else if (event.operation == "DELETE") {
                            val projectUuid = org.json.JSONObject(event.payloadJson).optString("projectUuid")
                            record.set(
                                hashMapOf<String, Any>(
                                    "uuid" to event.entityUuid,
                                    "type" to table,
                                    "projectUuid" to projectUuid,
                                    "revision" to event.revision,
                                    "isDeleted" to true,
                                    "updatedAt" to System.currentTimeMillis(),
                                    "updatedByUid" to currentUser.uid
                                ),
                                SetOptions.merge()
                            ).await()
                            uploadedRecords.add(event.entityUuid)
                        }
                        syncedCount++
                    }
                    db.syncOutboxDao().markSynced(event.eventId)
                } else if (event.entityType == Attachments.ENTITY_TYPE) {
                    Attachments.upload(context, event)
                    db.syncOutboxDao().markSynced(event.eventId)
                    syncedCount++
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to sync outbox event ${event.eventId}", e)
                db.syncOutboxDao().updateAttempt(event.eventId, "FAILED", e.localizedMessage.orEmpty())
            }
        }

        _syncStatus.value = FirestoreSyncStatus.Synced()
        Result.success(syncedCount)
    }
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
