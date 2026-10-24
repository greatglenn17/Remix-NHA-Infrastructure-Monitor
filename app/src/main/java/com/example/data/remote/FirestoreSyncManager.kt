package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.files.Attachments
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectDuplicateMerger
import com.example.data.local.RecordSyncSchema
import com.example.data.model.AssignedUids
import com.example.data.model.Project
import com.example.data.model.SyncOutboxEvent
import com.example.util.AppNotificationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    /** Reads the role, then attaches the listeners; replaced on every start. */
    private var listenerJob: Job? = null
    private val listenerLock = Any()
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
     * The Super Admin listens to every project and record; everyone else only to those
     * assigned to their account, so the role is read before the listeners are attached.
     */
    fun startRealtimeSync() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d(tag, "No authenticated user. Skipping realtime sync.")
            return
        }

        // Cancel and launch under one lock (re-entrant), so a concurrent start or stop always sees this job.
        synchronized(listenerLock) {
            stopRealtimeSync()
            listenerJob = scope.launch(Dispatchers.IO) { listenFor(currentUser) }
        }
    }

    /** Reads the role, retrying until the token is available, then attaches the listeners unless stopped meanwhile. */
    private suspend fun CoroutineScope.listenFor(currentUser: FirebaseUser) {
        var wait = 2_000L
        var superAdmin: Boolean? = null
        while (superAdmin == null) {
            try {
                superAdmin = AssignmentPolicy.isSuperAdmin(roleOf(currentUser))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Guessing the role could filter a Super Admin's listeners, so wait for the token.
                Log.w(tag, "Could not read the account role; retrying in ${wait / 1000}s: ${e.message}")
                delay(wait)
                wait = (wait * 2).coerceAtMost(60_000L)
            }
        }
        synchronized(listenerLock) {
            if (!isActive) return
            attachListeners(currentUser.uid, superAdmin == true)
        }
    }

    /** The account's role custom claim from the cached ID token (refreshed when it has expired). */
    private suspend fun roleOf(user: FirebaseUser): String? =
        user.getIdToken(false).await().claims["role"] as? String

    private fun attachListeners(uid: String, superAdmin: Boolean) {
        fun scoped(collection: String): Query = firestore.collection(collection).let {
            if (superAdmin) it else it.whereArrayContains("assignedUids", uid)
        }

        Log.d(tag, "Starting Firestore realtime listener for projects (User: $uid, all projects: $superAdmin)...")
        hasServerSnapshot = false
        projectListener = scoped("projects")
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
                            reconcile(docs, fromServer, uid, superAdmin)
                        } catch (ex: Exception) {
                            Log.e(tag, "Error reconciling Firestore projects", ex)
                        }
                    }
                }
            }

        // Reports, documents, issues and payments: only changed documents are applied.
        recordListener = scoped("records")
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
     * upload local projects the cloud has never seen. For everyone but the Super Admin
     * the snapshot holds only their assigned projects, so a local project missing from it
     * may instead have been unassigned; see [handleMissingProject].
     */
    private suspend fun reconcile(docs: List<DocumentSnapshot>, fromServer: Boolean, myUid: String, isSuperAdmin: Boolean) {
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
                        assignedUids = remoteAssignedUids(doc) ?: "",
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
                        assignedUids = remoteAssignedUids(doc) ?: localProject.assignedUids,
                        isDeleted = false
                    )
                    projectDao.updateProject(updatedProj)
                    Log.d(tag, "Updated local project from remote revision $revision: $name")
                }
                if (localProject != null) adoptAssignedUids(localProject, doc, isSuperAdmin)
            } catch (ex: Exception) {
                Log.e(tag, "Error processing remote Firestore document", ex)
            }
        }

        // Only trust "missing from the cloud" when the snapshot came from the server;
        // a cache snapshot may simply not have loaded every document yet.
        if (fromServer) {
            for (uuid in plan.uploadLocal) {
                val project = projectDao.getProjectByUuid(uuid) ?: continue
                if (isSuperAdmin) {
                    enqueueProjectEvent(uuid, "CREATE", project.revision)
                } else {
                    handleMissingProject(project, myUid)
                }
            }
            if (plan.uploadLocal.isNotEmpty()) {
                Log.d(tag, "Checked ${plan.uploadLocal.size} local projects missing from the cloud snapshot")
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

    /** The document's assignedUids as stored locally (a JSON array), or null when it has none yet. */
    private fun remoteAssignedUids(doc: DocumentSnapshot): String? =
        AssignmentPolicy.storedList(doc.contains("assignedUids"), doc.get("assignedUids"))

    /**
     * Keeps the local list equal to the cloud's even when the revision did not change
     * (the migration script and the Super Admin's edits set it), so records are uploaded
     * with the list the rules expect. Only the Super Admin edits it, so only their unsent
     * change is kept.
     */
    private suspend fun adoptAssignedUids(local: Project, doc: DocumentSnapshot, isSuperAdmin: Boolean) {
        val remote = remoteAssignedUids(doc) ?: return
        if (AssignedUids.parse(remote) == AssignedUids.parse(local.assignedUids) && local.assignedUids.isNotBlank()) return
        if (isSuperAdmin && db.syncOutboxDao().hasPendingFor(local.projectUuid)) return
        recordSync.setAssignedUidsLocally(local.projectUuid, remote)
    }

    /** What a direct server read says about a project missing from the listener's snapshot. */
    private sealed class Probe {
        object Missing : Probe()
        data class Found(val assignedUidsJson: String) : Probe()
        /** Not readable: unassigned under the new rules (or missing; the rules hide which). */
        object Denied : Probe()
        object Unknown : Probe()
    }

    private suspend fun probe(projectUuid: String): Probe = try {
        val doc = firestore.collection("projects").document(projectUuid).get(Source.SERVER).await()
        // A document without a list yet says nothing about this user: keep the project.
        if (!doc.exists()) Probe.Missing else remoteAssignedUids(doc)?.let { Probe.Found(it) } ?: Probe.Unknown
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) Probe.Denied else Probe.Unknown
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Probe.Unknown
    }

    /**
     * A non-admin's local project that a server snapshot of their assigned projects does
     * not contain. Anything this phone still has to send keeps it here and queued (a
     * project created on this phone is still uploaded). Otherwise the cloud is asked
     * once: a listener can't deliver the change that unassigned the user, so the local
     * list would still name them. An unassigned project leaves this phone only: it is
     * deleted, or hidden when it has data the cloud can't give back
     * ([RecordSync.unassignLocally]).
     */
    private suspend fun handleMissingProject(project: Project, myUid: String) {
        val pending = recordSync.hasPendingUpload(project.id, project.projectUuid)
        val createdByMe = db.syncOutboxDao().isUnconfirmedCreate(project.projectUuid)
        if (pending || createdByMe) {
            enqueueProjectEvent(project.projectUuid, "CREATE", project.revision)
            return
        }
        val list = if (project.assignedUids.isNotBlank() && !AssignedUids.contains(project.assignedUids, myUid)) {
            // Already the server's answer (a project hidden earlier): no need to ask again.
            project.assignedUids
        } else when (val probe = probe(project.projectUuid)) {
            Probe.Missing -> {
                // Old rules: the cloud has never seen it, so upload it as before.
                enqueueProjectEvent(project.projectUuid, "CREATE", project.revision)
                return
            }
            is Probe.Found -> probe.assignedUidsJson
            Probe.Denied -> "[]"
            Probe.Unknown -> return
        }
        if (!AssignmentPolicy.shouldRemoveLocally(false, myUid, list, inServerSnapshot = false, hasPendingUpload = false, createdByMe = false)) {
            // Still assigned; the project will arrive with a later snapshot.
            if (list != project.assignedUids) recordSync.setAssignedUidsLocally(project.projectUuid, list)
            return
        }
        // Files go without queuing anything: the garbage collector would also delete the
        // cloud copies of unreferenced files, and they still belong to the project.
        val outcome = recordSync.unassignLocally(project.id, project.projectUuid, list) { names ->
            names.forEach { Attachments.fileForName(context, it)?.delete() }
        }
        // A hidden project comes through here on every snapshot; log only the change.
        if (outcome == RecordSync.Unassigned.REMOVED || (outcome == RecordSync.Unassigned.HIDDEN && list != project.assignedUids)) {
            Log.d(tag, "Unassigned project ${project.projectUuid}: $outcome on this phone")
        }
    }

    /**
     * Stop real-time Firestore listeners.
     */
    fun stopRealtimeSync() = synchronized(listenerLock) {
        listenerJob?.cancel()
        listenerJob = null
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

        // Projects first: a record (or file) is only accepted once its project exists in the cloud.
        val pending = db.syncOutboxDao().pendingEventsList().sortedBy { if (it.entityType == "project") 0 else 1 }
        if (pending.isEmpty()) {
            return@withContext Result.success(0)
        }

        val role = try {
            roleOf(currentUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Without the role the upload can't be shaped for the rules; everything stays queued.
            Log.w(tag, "Could not read the account role; outbox left for the next sync", e)
            return@withContext Result.failure(e)
        }
        val superAdmin = AssignmentPolicy.isSuperAdmin(role)
        val canWrite = AssignmentPolicy.canWrite(role)

        _syncStatus.value = FirestoreSyncStatus.Syncing
        var syncedCount = 0
        val uploadedRecords = mutableSetOf<String>()
        /** Records whose project has no assigned accounts on this phone yet; they wait for the next flush. */
        val waitingRecords = mutableSetOf<String>()

        for (event in pending) {
            try {
                if (!canWrite && (event.entityType == "project" || event.entityType.startsWith(RecordSyncSchema.ENTITY_PREFIX))) {
                    // A Viewer can't write projects or records; the change stays on this phone only.
                    db.syncOutboxDao().markSynced(event.eventId)
                    continue
                }
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

                        uploadProject(project, data, superAdmin, currentUser.uid)

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
                        val ref = firestore.collection("projects").document(event.entityUuid)
                        if (superAdmin) {
                            ref.set(deleteData, SetOptions.merge()).await()
                        } else {
                            // update() never creates a document, so no tombstone without assignedUids.
                            try {
                                ref.update(deleteData).await()
                            } catch (e: FirebaseFirestoreException) {
                                // Never reached the cloud: nothing there to mark deleted.
                                if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
                            }
                        }

                        db.syncOutboxDao().markSynced(event.eventId)
                        syncedCount++
                    } else {
                        // The row was merged into another project or its uuid was replaced by the
                        // cloud one; the surviving row has its own event, so nothing is left to send.
                        db.syncOutboxDao().markSynced(event.eventId)
                    }
                } else if (event.entityType.startsWith(RecordSyncSchema.ENTITY_PREFIX)) {
                    // Every event uploads the row as it is now, so one upload per record covers the rest.
                    if (event.entityUuid in waitingRecords) continue
                    if (event.entityUuid !in uploadedRecords) {
                        val table = event.entityType.removePrefix(RecordSyncSchema.ENTITY_PREFIX)
                        val doc = recordSync.buildDocument(table, event.entityUuid, currentUser.uid)
                        val record = firestore.collection("records").document(event.entityUuid)
                        if (doc != null) {
                            if ("assignedUids" !in doc) {
                                // The project's list hasn't reached this phone yet (any role): wait for it.
                                waitingRecords.add(event.entityUuid)
                                continue
                            }
                            record.set(doc).await()
                            uploadedRecords.add(event.entityUuid)
                        } else if (event.operation == "DELETE") {
                            val projectUuid = org.json.JSONObject(event.payloadJson).optString("projectUuid")
                            val tombstone = hashMapOf<String, Any>(
                                "uuid" to event.entityUuid,
                                "type" to table,
                                "projectUuid" to projectUuid,
                                "revision" to event.revision,
                                "isDeleted" to true,
                                "updatedAt" to System.currentTimeMillis(),
                                "updatedByUid" to currentUser.uid
                            )
                            // Merged, so without a local list the stored record keeps its own.
                            recordSync.assignedUidsOf(projectUuid)?.takeIf { it.isNotBlank() }?.let {
                                tombstone["assignedUids"] = AssignedUids.parse(it)
                            }
                            record.set(tombstone, SetOptions.merge()).await()
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
     * Writes a project document. The Super Admin sends the whole document including
     * `assignedUids`. Anyone else updates it without touching the list, and creates it,
     * naming only themselves, when it doesn't exist yet. The new rules may report a missing
     * document as PERMISSION_DENIED (the update rule reads the stored list), so that also
     * falls back to the create, which they only allow for a missing document anyway.
     */
    private suspend fun uploadProject(project: Project, data: Map<String, Any>, superAdmin: Boolean, uid: String) {
        val ref: DocumentReference = firestore.collection("projects").document(project.projectUuid)
        if (superAdmin) {
            val list = AssignmentPolicy.projectUploadFields(true, exists = true, myUid = uid, localAssignedUids = project.assignedUids)
            ref.set(if (list == null) data else data + ("assignedUids" to list), SetOptions.merge()).await()
            return
        }
        try {
            ref.update(data).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND && e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e
            val list = AssignmentPolicy.projectUploadFields(false, exists = false, myUid = uid, localAssignedUids = project.assignedUids)!!
            ref.set(data + ("assignedUids" to list)).await()
            // The records uploaded next must carry the same list as the project.
            recordSync.setAssignedUidsLocally(project.projectUuid, AssignedUids.toJson(list))
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
