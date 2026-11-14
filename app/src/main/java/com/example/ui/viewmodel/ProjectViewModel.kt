package com.example.ui.viewmodel

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.data.auth.AuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.WeeklyReportWeatherWriter
import com.example.data.local.barChartPayload
import com.example.data.model.*
import kotlinx.coroutines.tasks.await
import com.example.data.remote.AssignmentPolicy
import com.example.data.remote.FirestoreSyncManager
import com.example.data.auth.isEmailName
import com.example.data.repository.ProjectRepository
import com.example.util.AppNotificationManager
import com.example.util.AttentionFlag
import com.example.util.BarChart
import com.example.util.BillingProgress
import com.example.util.ContractCost
import com.example.util.Portfolio
import com.example.util.PortfolioFilter
import com.example.util.PortfolioItem
import com.example.util.PortfolioSummary
import com.example.util.ProjectAttention
import com.example.util.SitePhotos
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

private data class FilterParams(
    val query: String,
    val status: String,
    val type: String,
    val sort: String,
    val user: UserAccount
)

data class PortfolioUiState(
    val summary: PortfolioSummary,
    val attention: List<Pair<PortfolioItem, AttentionFlag>>,  // from all scoped projects
    val rows: List<PortfolioItem>,                            // search, status, type, filter and sort applied
    val filter: PortfolioFilter?,
    val totalProjects: Int
) {
    companion object {
        val EMPTY = PortfolioUiState(PortfolioSummary(0, 0, 0, 0, 0), emptyList(), emptyList(), null, 0)
    }
}

/** Starts the description of lots the app found in the plan PDF, so a new run can replace them. */
private const val DETECTED_LOT = "Auto-detected"
// A found lot someone has checked against the plan; Find lots and Remove found lots leave it alone.
private const val RENUMBERED_LOT = "Renumbered to the plan"

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val projectDao: com.example.data.local.ProjectDao
    private val reportDao: com.example.data.local.ReportDao
    private val syncOutboxDao: com.example.data.local.SyncOutboxDao
    private lateinit var database: AppDatabase
    private lateinit var weeklyReportWeatherWriter: WeeklyReportWeatherWriter
    private val repository: ProjectRepository
    private val firestoreSyncManager: FirestoreSyncManager
    private val authManager: AuthManager = AuthManager(application)

    // Auth States
    private val _isLoggedIn = MutableStateFlow(authManager.isUserSignedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authSuccessMessage = MutableStateFlow<String?>(null)
    val authSuccessMessage: StateFlow<String?> = _authSuccessMessage.asStateFlow()

    // Theme Mode State (System, Light, Dark)
    private val _appThemeMode = MutableStateFlow(authManager.getSavedAppThemeMode())
    val appThemeMode: StateFlow<com.example.data.model.AppThemeMode> = _appThemeMode.asStateFlow()

    fun setAppThemeMode(mode: com.example.data.model.AppThemeMode) {
        _appThemeMode.value = mode
        authManager.saveAppThemeMode(mode)
    }

    // User Role State
    private val _currentUserAccount = MutableStateFlow(DefaultUserAccount)
    val currentUserAccount: StateFlow<UserAccount> = _currentUserAccount.asStateFlow()

    /** The server's view of the signed-in account; nulls when offline. */
    private data class ServerClaims(val role: UserRole?, val seesAll: Boolean?, val approved: Boolean?) {
        /** The role to work as: an account waiting for approval can only view, as the rules allow. */
        val workingRole: UserRole? get() = if (approved == false) UserRole.VIEWER else role
    }

    /**
     * The signed-in user's role, "See all projects" switch and approval from the server: the token
     * claims, else users/{uid}.
     */
    private suspend fun serverClaims(): ServerClaims {
        val user = authManager.currentFirebaseUser ?: return ServerClaims(null, null, null)
        val claims = runCatching { user.getIdToken(true).await().claims }.getOrNull()
        val doc = if (claims?.get("role") is String) null else runCatching {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(user.uid).get().await()
        }.getOrNull()
        val name = claims?.get("role") as? String ?: doc?.getString("role")
        val seesAll = if (claims != null) claims["allProjects"] == true else doc?.getBoolean("seesAllProjects")
        // Same rule as firestore.rules approved(): no approved claim means approved only when a role claim exists.
        val approved = if (claims != null) claims["approved"] as? Boolean ?: (claims["role"] is String) else doc?.getBoolean("approved")
        return ServerClaims(name?.let { runCatching { UserRole.valueOf(it.uppercase()) }.getOrNull() }, seesAll, approved)
    }

    /** The name, position and office saved on the server, for a phone the account was not registered on. */
    private suspend fun serverProfile(): Triple<String?, String?, String?>? {
        val user = authManager.currentFirebaseUser ?: return null
        val doc = runCatching {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(user.uid).get().await()
        }.getOrNull() ?: return null
        return Triple(doc.getString("displayName")?.ifBlank { null }, doc.getString("position")?.ifBlank { null }, doc.getString("office")?.ifBlank { null })
    }

    /** Applies the role the Super Admin set, and the name, position and office saved from any phone, since this one last signed in. */
    private suspend fun refreshAccountFromServer(email: String) {
        val profile = serverProfile()
        val account = _currentUserAccount.value
        val server = serverClaims()
        val updated = com.example.data.auth.refreshedAccount(account, email, server.workingRole, profile?.first, profile?.second, profile?.third, server.seesAll)
            .let { acc -> server.approved?.let { acc.copy(awaitingApproval = !it) } ?: acc }
        // The server has no real name yet: this phone's goes up so the other phones get it too.
        if (profile != null && isEmailName(profile.first, email) && !isEmailName(account.name, email)) {
            authManager.currentFirebaseUser?.let { firestoreSyncManager.syncUserProfile(it.uid, email, account.name, null, null, null) }
        }
        if (updated == account) return
        _currentUserAccount.value = updated
        // The listeners were set up for the old claims; a changed switch or role re-attaches them.
        if (updated.seesAll != account.seesAll) firestoreSyncManager.startRealtimeSync()
        authManager.saveUserSession(updated, email)
        authManager.saveRegisteredRole(email, updated.role)
    }

    /** Super Admin: sets another user's role; syncUserCustomClaims turns it into their claim. [onDone] gets the message to show. */
    fun setUserRole(user: RegisteredUser, role: UserRole, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(user.uid).update("role", role.name).await()
                _registeredUsers.value = _registeredUsers.value.map { if (it.uid == user.uid) it.copy(role = role.name) else it }
                onDone("${user.name} is now ${role.label}. It applies the next time they open the app.")
                logAuditAction(
                    projectId = null,
                    actionType = "Role Change",
                    details = "Changed role of ${user.name}",
                    oldValue = user.role,
                    newValue = role.name
                )
            } catch (e: Exception) {
                onDone("Could not change the role: ${e.localizedMessage}")
            }
        }
    }

    /** Super Admin: approves a new account so it can add and edit; syncUserCustomClaims mints the claim. */
    fun approveUser(user: RegisteredUser, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // The rules want a role on every Super Admin edit; a profile without one counts as Field Engineer.
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(user.uid)
                    .update(mapOf("approved" to true, "role" to user.role.ifBlank { UserRole.FIELD_ENGINEER.name })).await()
                _registeredUsers.value = _registeredUsers.value.map { if (it.uid == user.uid) it.copy(approved = true) else it }
                onDone("${user.name} is approved. It applies the next time they open the app.")
                logAuditAction(projectId = null, actionType = "Role Change", details = "Approved the account of ${user.name}", oldValue = "Waiting for approval", newValue = "Approved")
            } catch (e: Exception) {
                onDone("Could not approve: ${e.localizedMessage}")
            }
        }
    }

    /** Super Admin: lets [user] open every project view-only; syncUserCustomClaims mints the claim. */
    fun setUserSeesAll(user: RegisteredUser, on: Boolean, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // The rules want a role on every Super Admin edit; a profile without one counts as Field Engineer.
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(user.uid)
                    .update(mapOf("seesAllProjects" to on, "role" to user.role.ifBlank { UserRole.FIELD_ENGINEER.name })).await()
                _registeredUsers.value = _registeredUsers.value.map { if (it.uid == user.uid) it.copy(seesAllProjects = on) else it }
                onDone(
                    if (on) "${user.name} can now open every project, view only. It applies the next time they open the app."
                    else "${user.name} now sees only their own projects. It applies the next time they open the app."
                )
                logAuditAction(
                    projectId = null,
                    actionType = "Role Change",
                    details = "${if (on) "Allowed" else "Removed"} all projects (view only) for ${user.name}",
                    oldValue = (!on).toString(),
                    newValue = on.toString()
                )
            } catch (e: Exception) {
                onDone("Could not change it: ${e.localizedMessage}")
            }
        }
    }

    /** When every change on this phone was last in the cloud, for the sync chip's window. */
    val lastSyncedAt: StateFlow<Long?> by lazy { firestoreSyncManager.lastSyncedAt }

    /** The sync chip's Retry: send the queued and failed changes again now. */
    fun retrySync() {
        viewModelScope.launch { firestoreSyncManager.flushOutbox() }
    }

    /** The header's sync chip, from the upload queue. Lazy because the DAO is set up in init. */
    val syncChip: StateFlow<com.example.ui.components.SyncChip> by lazy {
        // The account changes on sign-in and sign-out, when the Firebase user does.
        combine(currentUserAccount, syncOutboxDao.newPendingCount(), syncOutboxDao.failedCount()) { _, pending, failed ->
            com.example.ui.components.SyncChip.of(authManager.currentFirebaseUser != null, pending, failed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.example.ui.components.SyncChip.of(authManager.currentFirebaseUser != null, 0, 0))
    }

    /** Uuids of records queued for the cloud and not confirmed yet, for the cards' "Waiting to upload"; none when signed in offline. */
    val pendingUploads: StateFlow<Set<String>> by lazy {
        combine(currentUserAccount, syncOutboxDao.pendingEvents()) { _, events ->
            if (authManager.currentFirebaseUser == null) emptySet()
            else events.filter { it.entityType.startsWith(com.example.data.local.RecordSyncSchema.ENTITY_PREFIX) }.mapTo(HashSet()) { it.entityUuid }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    }

    // Filters & Sorting
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow("All")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    private val _typeFilter = MutableStateFlow("All")
    val typeFilter: StateFlow<String> = _typeFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(Portfolio.NEEDS_ATTENTION_SORT)
    val sortOrder: StateFlow<String> = _sortOrder.asStateFlow()

    // Selected Project ID for detail view
    private val _selectedProjectId = MutableStateFlow<Long?>(null)
    val selectedProjectId: StateFlow<Long?> = _selectedProjectId.asStateFlow()

    // Last project section used; kept when switching projects.
    private val _selectedSection = MutableStateFlow(com.example.ui.detail.ProjectSection.SUMMARY)
    val selectedSection: StateFlow<com.example.ui.detail.ProjectSection> = _selectedSection.asStateFlow()
    fun setSection(section: com.example.ui.detail.ProjectSection) { _selectedSection.value = section }

    /**
     * Project writes run on this handler: a write for a project that left this phone meanwhile
     * (unassigned, merged, deleted elsewhere) fails its foreign key and is dropped instead of
     * crashing the app. Any other error is passed on as before.
     */
    private val missingProjectGuard = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        if (e is android.database.sqlite.SQLiteConstraintException && e.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true)) {
            android.util.Log.w("ProjectViewModel", "Change dropped: its project is no longer on this phone", e)
        } else {
            Thread.currentThread().let { t -> t.uncaughtExceptionHandler?.uncaughtException(t, e) }
        }
    }

    // All raw projects
    val rawProjects: StateFlow<List<Project>>
    val debugError = kotlinx.coroutines.flow.MutableStateFlow<String>("")

    // User-scoped projects based on role/assignment
    val userScopedProjects: StateFlow<List<Project>>

    // The dashboard's My projects / All projects switch, for an account that sees all projects.
    private val _showAllProjects = MutableStateFlow(false)
    val showAllProjects: StateFlow<Boolean> = _showAllProjects.asStateFlow()
    fun setShowAllProjects(show: Boolean) { _showAllProjects.value = show }

    /** Whether [project] is one this account works on; other projects open view-only. */
    fun isMine(project: Project): Boolean = AssignmentPolicy.isVisible(
        currentUserAccount.value.role == UserRole.SUPER_ADMIN, authManager.currentFirebaseUser?.uid, project.assignedUids, project.createdByUid
    )

    /** The signed-in account as it acts on [project]: a Viewer on a project not assigned to it. */
    fun accountFor(project: Project?, account: UserAccount): UserAccount = account.forProject(project, authManager.currentFirebaseUser?.uid)

    // Accounts a project can be assigned to (Super Admin only). The list is trusted for
    // editing only once it has loaded; a failed load leaves it empty and not loaded.
    private val _registeredUsers = MutableStateFlow<List<RegisteredUser>>(emptyList())
    val registeredUsers: StateFlow<List<RegisteredUser>> = _registeredUsers.asStateFlow()
    private val _registeredUsersLoaded = MutableStateFlow(false)
    val registeredUsersLoaded: StateFlow<Boolean> = _registeredUsersLoaded.asStateFlow()

    // Filtered and Sorted Projects
    val filteredProjects: StateFlow<List<Project>>

    // Notifications State
    private val notificationDao: com.example.data.local.NotificationDao
    val notifications: StateFlow<List<AppNotification>>
    val unreadNotificationCount: StateFlow<Int>

    init {
        database = AppDatabase.getDatabase(application, viewModelScope)
        projectDao = database.projectDao()
        reportDao = database.reportDao()
        syncOutboxDao = database.syncOutboxDao()
        weeklyReportWeatherWriter = WeeklyReportWeatherWriter(database, reportDao)
        repository = ProjectRepository(projectDao, reportDao)
        firestoreSyncManager = FirestoreSyncManager(application, database, viewModelScope)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.example.data.local.SampleData.clearFromExistingRecords(application, database)
                com.example.data.local.SampleData.removeUntouchedCoreChecklist(database)
            } catch (e: Exception) {
                android.util.Log.w("ProjectViewModel", "Sample data cleanup failed", e)
            }
            try {
                com.example.data.files.Attachments.migrateLegacy(application, database)
                com.example.data.files.Attachments.collectGarbage(application, database)
            } catch (e: Exception) {
                android.util.Log.w("ProjectViewModel", "Attachment upkeep failed", e)
            }
        }
        notificationDao = database.notificationDao()

        notifications = notificationDao.getAllNotifications().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        unreadNotificationCount = notificationDao.getUnreadCount().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

        val prefs = application.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        val isCleared = prefs.getBoolean("placeholders_cleared_v105", false)

        // Restore persisted user session on startup
        val savedSession = authManager.getSavedUserSession()
        if (savedSession != null && authManager.isOfflineOnlySession) {
            // Logged in via the on-device fallback only: ask for the password once so the
            // account is linked to Firebase and this phone's projects can sync.
            authManager.clearUserSession()
            _isLoggedIn.value = false
            _authSuccessMessage.value = "Please sign in again to turn on cloud sync for this phone."
        } else if (savedSession != null) {
            _currentUserAccount.value = savedSession
            _isLoggedIn.value = true
        } else if (authManager.isUserSignedIn) {
            val fbUser = authManager.currentFirebaseUser
            if (fbUser != null) {
                val reconstructed = authManager.mapToUserAccount(
                    email = fbUser.email ?: "officer@nha.gov.ph",
                    displayName = fbUser.displayName,
                    selectedRole = UserRole.FIELD_ENGINEER
                )
                _currentUserAccount.value = reconstructed
                _isLoggedIn.value = true
            } else {
                _isLoggedIn.value = false
            }
        } else {
            _isLoggedIn.value = false
        }
        authManager.currentFirebaseUser?.email?.let { email ->
            if (_isLoggedIn.value) viewModelScope.launch { refreshAccountFromServer(email.trim().lowercase()) }
        }

        // Start realtime sync if user is already logged in (AuthStateListener in FirestoreSyncManager
        // will also trigger this automatically when Firebase Auth token is ready)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.example.data.local.ProjectDuplicateMerger.merge(database)
            } catch (e: Exception) {
                android.util.Log.w("ProjectViewModel", "Duplicate project merge skipped: ${e.message}")
            }
            if (_isLoggedIn.value) {
                try {
                    firestoreSyncManager.startRealtimeSync()
                    firestoreSyncManager.flushOutbox()
                } catch (e: Exception) {
                    android.util.Log.w("ProjectViewModel", "Startup sync skipped: ${e.message}")
                }
            }
        }

        rawProjects = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        userScopedProjects = combine(rawProjects, currentUserAccount, _showAllProjects, repository.getAllPendingDocuments()) { projects, user, showAll, docs ->
            // Super Admin and Super Viewer see everything; everyone else the projects naming their
            // account, and with the Super Admin's switch, all of them while All projects is on.
            val isSuperAdmin = user.role == UserRole.SUPER_ADMIN
            val all = user.role == UserRole.SUPER_VIEWER || (user.seesAll && showAll)
            val myUid = authManager.currentFirebaseUser?.uid
            projects
                .filter { AssignmentPolicy.isVisible(isSuperAdmin, myUid, it.assignedUids, it.createdByUid, all) }
                .distinctBy { project ->
                    "${project.name.normalizeProjectKey()}|${project.location.normalizeProjectKey()}"
                }
                .withSchedule(docs)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Close the open project when it leaves this phone or is hidden (e.g. the account was
        // unassigned), so its screen can't keep writing into a row that is gone.
        viewModelScope.launch {
            combine(_selectedProjectId, repository.allProjects, currentUserAccount) { id, projects, user ->
                Triple(id, projects.firstOrNull { it.id == id }, user)
            }.collect { (id, project, user) ->
                if (id == null) return@collect
                // The list may predate a project selected right after it was saved; ask the database.
                val current = project ?: database.projectDao().getProjectByIdOnce(id)
                val visible = current != null && AssignmentPolicy.isVisible(
                    user.role == UserRole.SUPER_ADMIN, authManager.currentFirebaseUser?.uid, current.assignedUids, current.createdByUid, user.seesAll
                )
                if (!visible) _selectedProjectId.compareAndSet(id, null)
            }
        }

        // Only the Super Admin may assign projects, so only they read the account list.
        viewModelScope.launch {
            currentUserAccount
                .map { it.role == UserRole.SUPER_ADMIN }
                .distinctUntilChanged()
                .collect { isSuperAdmin ->
                    if (isSuperAdmin) refreshRegisteredUsers()
                    else {
                        _registeredUsers.value = emptyList()
                        _registeredUsersLoaded.value = false
                    }
                }
        }

        val filterParams = combine(searchQuery, statusFilter, typeFilter, sortOrder, currentUserAccount) { query, status, type, sort, user ->
            FilterParams(query, status, type, sort, user)
        }

        filteredProjects = combine(userScopedProjects, filterParams) { projects, params ->
            var list = projects

            if (params.query.isNotBlank()) {
                list = list.filter {
                    it.name.contains(params.query, ignoreCase = true) ||
                    it.location.contains(params.query, ignoreCase = true) ||
                    it.contractor.contains(params.query, ignoreCase = true) ||
                    it.assignedStaff.contains(params.query, ignoreCase = true)
                }
            }

            if (params.status != "All") {
                list = list.filter { it.displayStatus.equals(params.status, ignoreCase = true) }
            }

            if (params.type != "All") {
                list = list.filter { it.projectType.equals(params.type, ignoreCase = true) }
            }

            when (params.sort) {
                // Ordered later from the attention flags in portfolioState.
                Portfolio.NEEDS_ATTENTION_SORT -> list
                "Variance (Worst First)" -> list.sortedBy { it.variance }
                "Variance (Best First)" -> list.sortedByDescending { it.variance }
                "Completion Date" -> list.sortedBy { it.completionDateRevised }
                "Name" -> list.sortedBy { it.name }
                else -> list
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // Auth Actions
    fun authenticateWithFirebase(
        email: String,
        pass: String,
        displayName: String?,
        role: UserRole,
        isSignUp: Boolean,
        position: String? = null,
        office: String? = null
    ) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            _authSuccessMessage.value = null

            val cleanEmail = email.trim().lowercase()
            val cleanPass = pass.trim()

            if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                _authError.value = "Please enter a valid email address."
                _authLoading.value = false
                return@launch
            }

            if (cleanPass.length < 6) {
                _authError.value = "Password must be at least 6 characters long."
                _authLoading.value = false
                return@launch
            }

            if (isSignUp) {
                // New accounts start as Field Engineer or Viewer; the Super Admin promotes them.
                val role = if (role == UserRole.VIEWER) UserRole.VIEWER else UserRole.FIELD_ENGINEER
                val result = authManager.signUp(cleanEmail, cleanPass, displayName, role, position, office)
                _authLoading.value = false
                if (result.isSuccess) {
                    _authSuccessMessage.value = "Registration Successful! Account created for $cleanEmail. Please type your password to sign in. You can view projects now; adding and editing start once the Super Admin approves your account."
                    val createdUser = result.getOrNull()
                    if (createdUser != null) {
                        firestoreSyncManager.syncUserProfile(
                            uid = createdUser.uid,
                            email = cleanEmail,
                            displayName = displayName ?: cleanEmail.substringBefore("@"),
                            role = role.name,
                            position = position.orEmpty(),
                            office = office.orEmpty()
                        )
                    }
                    logAuditAction(
                        projectId = null,
                        actionType = "User Registration",
                        details = "Registered new user: $cleanEmail (${role.label})",
                        oldValue = "",
                        newValue = "Email: $cleanEmail, Role: ${role.label}",
                        user = displayName ?: cleanEmail
                    )
                } else {
                    val errMessage = result.exceptionOrNull()?.localizedMessage
                    val formattedErr = when {
                        errMessage?.contains("already in use", ignoreCase = true) == true ->
                            "This email is already registered. Please sign in instead."
                        errMessage?.contains("badly formatted", ignoreCase = true) == true ->
                            "Invalid email address format."
                        else -> errMessage ?: "Registration failed. Please verify information and try again."
                    }
                    _authError.value = formattedErr
                }
            } else {
                val result = authManager.signIn(cleanEmail, cleanPass)
                if (result.isSuccess) {
                    val server = serverProfile()
                    val localAcc = authManager.getRegisteredUserAccount(
                        email = cleanEmail,
                        fallbackRole = UserRole.FIELD_ENGINEER,
                        displayName = server?.first ?: displayName,
                        position = server?.second ?: position,
                        office = server?.third ?: office
                    )
                    // The role is the server's; the on-device copy only covers being offline.
                    val claims = serverClaims()
                    val userAcc = localAcc.copy(
                        role = claims.workingRole ?: localAcc.role,
                        seesAllProjects = claims.seesAll ?: localAcc.seesAllProjects,
                        awaitingApproval = claims.approved?.not() ?: localAcc.awaitingApproval
                    )
                    authManager.saveRegisteredRole(cleanEmail, userAcc.role)
                    _currentUserAccount.value = userAcc
                    _isLoggedIn.value = true
                    _authLoading.value = false
                    authManager.saveUserSession(userAcc, cleanEmail)

                    // Trigger realtime Firestore sync, flush outbox, and sync profile/FCM token
                    firestoreSyncManager.startRealtimeSync()
                    firestoreSyncManager.flushOutbox()
                    val currentFirebaseUser = authManager.currentFirebaseUser
                    if (currentFirebaseUser != null) {
                        // Only a real name goes back; position and office stay as the server has them.
                        firestoreSyncManager.syncUserProfile(
                            uid = currentFirebaseUser.uid,
                            email = cleanEmail,
                            displayName = userAcc.name.takeUnless { isEmailName(it, cleanEmail) },
                            role = null,
                            position = null,
                            office = null
                        )
                    }

                    logAuditAction(
                        projectId = null,
                        actionType = "User Login",
                        details = "User logged in: ${userAcc.name}",
                        oldValue = "",
                        newValue = "Email: $cleanEmail, Role: ${userAcc.role.label}",
                        user = userAcc.name
                    )
                } else {
                    val errMessage = result.exceptionOrNull()?.localizedMessage
                    val formattedErr = when {
                        errMessage?.contains("no user record", ignoreCase = true) == true ||
                        errMessage?.contains("USER_NOT_FOUND", ignoreCase = true) == true -> 
                            "No account found with this email. Click 'Create Account' to register."
                        errMessage?.contains("invalid", ignoreCase = true) == true ->
                            "Invalid email or password. Please verify your credentials."
                        else -> errMessage ?: "Authentication failed. Please check credentials."
                    }
                    _authError.value = formattedErr
                    _authLoading.value = false
                }
            }
        }
    }

    fun clearAuthSuccessMessage() {
        _authSuccessMessage.value = null
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            val cleanEmail = email.trim().lowercase()
            if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                _authError.value = "Please enter a valid email address."
                return@launch
            }

            val result = authManager.sendPasswordReset(cleanEmail)
            if (result.isSuccess) {
                _authSuccessMessage.value = "Password reset email sent to $cleanEmail. Please check your inbox and spam folder."
                _authError.value = null
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Firebase Auth email delivery unconfigured."
                _authError.value = "Could not send the reset email: $err"
            }
        }
    }

    fun loginAsQuickDemo(userAccount: UserAccount) {
        authManager.saveUserSession(userAccount, "${userAccount.id}@nha.gov.ph")
        _currentUserAccount.value = userAccount
        _isLoggedIn.value = true
        _authError.value = null
        logAuditAction(
            projectId = null,
            actionType = "User Login",
            details = "Quick Demo login: ${userAccount.name}",
            oldValue = "",
            newValue = "Email: ${userAccount.id}@nha.gov.ph, Role: ${userAccount.role.label}",
            user = userAccount.name
        )
    }

    fun updateUserProfile(name: String, position: String, office: String) {
        val updated = authManager.updateUserProfile(name, position, office)
        _currentUserAccount.value = updated
        // So the account shows the same name and title on every phone.
        authManager.currentFirebaseUser?.let { user ->
            viewModelScope.launch {
                firestoreSyncManager.syncUserProfile(user.uid, user.email.orEmpty(), name.trim(), null, position.trim(), office.trim())
            }
        }
        logAuditAction(
            projectId = null,
            actionType = "Update Profile",
            details = "Updated user profile & position: ${updated.name}",
            oldValue = "",
            newValue = "Position: $position, Office: $office",
            user = updated.name
        )
    }

    fun signOutUser() = viewModelScope.launch {
        val previousUser = currentUserAccount.value.name
        // While still signed in: this phone stops getting the account's pushes.
        firestoreSyncManager.unregisterPushToken()
        firestoreSyncManager.stopRealtimeSync()
        authManager.signOut()
        _currentUserAccount.value = DefaultUserAccount
        _isLoggedIn.value = false
        _selectedProjectId.value = null
        logAuditAction(
            projectId = null,
            actionType = "User Logout",
            details = "User signed out: $previousUser",
            oldValue = "User: $previousUser",
            newValue = "",
            user = previousUser
        )
    }

    fun clearAuthError() {
        _authError.value = null
    }

    // Selected Project Flow
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedProject: StateFlow<Project?> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else combine(repository.getProjectById(id), repository.getPendingDocuments(id)) { project, docs ->
            project?.let { BarChart.withSchedule(it, docs, LocalDate.now()) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // shortcut: read at each emission, so an app left open past midnight shows yesterday's target until data changes.
    private fun List<Project>.withSchedule(docs: List<PendingDocument>): List<Project> {
        val today = LocalDate.now()
        val byProject = docs.groupBy { it.projectId }
        return map { BarChart.withSchedule(it, byProject[it.id].orEmpty(), today) }
    }

    // Sub-logs for selected project
    @OptIn(ExperimentalCoroutinesApi::class)
    val timeExtensions: StateFlow<List<TimeExtension>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getTimeExtensions(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val workSuspensionOrders: StateFlow<List<WorkSuspensionOrder>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getWorkSuspensionOrders(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val workResumptionLogs: StateFlow<List<WorkResumptionLog>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getWorkResumptionLogs(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPendingDocuments: StateFlow<List<PendingDocument>> = repository.getAllPendingDocuments().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Total net billed (after retention) per project id, for the dashboard billing bars. */
    val billedTotalsByProject: StateFlow<Map<Long, Double>> = projectDao.getAllPayments()
        .map { payments -> payments.groupBy { it.projectId }.mapValues { (_, p) -> p.sumOf { it.netAmount } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _portfolioFilter = MutableStateFlow<PortfolioFilter?>(null)

    /** Tapping the active count again clears it. */
    fun setPortfolioFilter(filter: PortfolioFilter?) {
        _portfolioFilter.value = if (_portfolioFilter.value == filter) null else filter
    }

    private val portfolioItems: Flow<List<PortfolioItem>> = combine(
        userScopedProjects, allPendingDocuments, billedTotalsByProject, reportDao.getLatestWeeklyReportTimes(), projectDao.getAllDeveloperRequests()
    ) { projects, docs, billed, latest, requests ->
        val today = LocalDate.now()
        val docsByProject = docs.groupBy { it.projectId }
        val requestsByProject = requests.groupBy { it.projectId }
        val latestByProject = latest.associate {
            it.projectId to Instant.ofEpochMilli(it.latestAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        projects.map { project ->
            val projectDocs = docsByProject[project.id].orEmpty()
            val billedTotal = billed[project.id] ?: 0.0
            val cost = ContractCost.current(project.scopeOfWork, project.contractCostOriginal, projectDocs)
            val lastUpdate = latestByProject[project.id]
            PortfolioItem(
                project = project,
                flags = ProjectAttention.flags(project, projectDocs, billedTotal, lastUpdate, today, requestsByProject[project.id].orEmpty()),
                billedFraction = BillingProgress.billedFraction(billedTotal, cost),
                billedTotal = billedTotal,
                contractCost = cost,
                pendingDocsCount = ProjectAttention.waitingApproval(projectDocs),
                lastFieldUpdate = lastUpdate,
                // userScopedProjects re-emits when the account changes, so reading it here stays current.
                // A Super Viewer reads everything and edits nothing, so the tag would only mark assignments.
                viewOnly = !isMine(project) && currentUserAccount.value.role != UserRole.SUPER_VIEWER
            )
        }
    }

    val portfolioState: StateFlow<PortfolioUiState> = combine(
        portfolioItems, filteredProjects, _portfolioFilter, sortOrder
    ) { items, filtered, filter, sort ->
        val byId = items.associateBy { it.project.id }
        val rows = filtered.mapNotNull { byId[it.id] }.filter { Portfolio.matches(it, filter) }
        PortfolioUiState(
            summary = Portfolio.summarize(items),
            attention = Portfolio.attentionQueue(items),
            rows = if (sort == Portfolio.NEEDS_ATTENTION_SORT) Portfolio.sortByAttention(rows) else rows,
            filter = filter,
            totalProjects = items.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PortfolioUiState.EMPTY)

    @OptIn(ExperimentalCoroutinesApi::class)
    val developerRequests: StateFlow<List<com.example.data.model.DeveloperRequest>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else projectDao.getDeveloperRequestsForProject(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val contractorLetters: StateFlow<List<com.example.data.model.ContractorLetter>> = _selectedProjectId.flatMapLatest { id ->
        // Letters the Documents tab kept as documents before v2.6 show here too.
        if (id == null) flowOf(emptyList())
        else combine(projectDao.getContractorLettersForProject(id), repository.getPendingDocuments(id), com.example.util.ContractorLetters::merged)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The uploaded abstracts of the actual work (Actual Accomplishment tab), newest "As of" date first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val projectAbstracts: StateFlow<List<ProjectAbstract>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else projectDao.getProjectAbstractsForProject(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingDocuments: StateFlow<List<PendingDocument>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getPendingDocuments(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val weeklyReports: StateFlow<List<WeeklyReport>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getWeeklyReports(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthlyReports: StateFlow<List<MonthlyReport>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getMonthlyReports(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyWeatherLogs: StateFlow<List<DailyHourlyWeather>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getDailyWeather(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    @OptIn(ExperimentalCoroutinesApi::class)
    val projectPayments: StateFlow<List<ProjectPayment>> = selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getProjectPaymentsForProject(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val projectIssues: StateFlow<List<ProjectIssue>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getProjectIssues(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val auditLogs: StateFlow<List<AuditLog>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getAuditLogs(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuditLogs: StateFlow<List<AuditLog>> = repository.getAllAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val sdpPlans: StateFlow<List<SdpPlan>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getSdpPlansForProject(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // The site each project shows on this phone. Not synced, so one engineer's pick doesn't move the others.
    private val _selectedSdpSite = MutableStateFlow<Map<Long, String>>(emptyMap())

    /** The plan on screen: the picked site's plan in use, else the first site's. */
    val activeSdpPlan: StateFlow<SdpPlan?> = combine(_selectedProjectId, sdpPlans, _selectedSdpSite) { id, plans, picked ->
        val current = SdpSites.current(plans.filter { it.projectId == id })
        current.firstOrNull { it.siteName == picked[id] } ?: current.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectSdpSite(projectId: Long, site: String) = _selectedSdpSite.update { it + (projectId to site) }

    /** The lots of every site's plan in use, with their progress by lot id. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allSitesSdpLots: StateFlow<Pair<List<SdpLot>, Map<Long, SdpLotProgress>>> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList<SdpLot>() to emptyMap()) else combine(
            repository.getSdpPlansForProject(id), repository.getActiveLotsForProject(id), repository.getLotProgressForProject(id)
        ) { plans, lots, progress ->
            val inUse = SdpSites.current(plans).map { it.id }.toSet()
            lots.filter { it.sdpPlanId in inUse } to progress.associateBy { it.sdpLotId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<SdpLot>() to emptyMap())

    /** Every site's lots of the project, for its totals. */
    val allSitesSdpItems: StateFlow<List<com.example.util.SdpSummary.Item>> = allSitesSdpLots.map { (lots, progress) ->
        lots.map { com.example.util.SdpSummary.Item(it.use, it.lotAreaSqM, progress[it.id]?.physicalProgress ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSdpLots: StateFlow<List<SdpLot>> = activeSdpPlan.flatMapLatest { plan ->
        if (plan == null) flowOf(emptyList()) else repository.getActiveLotsForPlan(plan.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSdpRoads: StateFlow<List<SdpRoad>> = activeSdpPlan.flatMapLatest { plan ->
        if (plan == null) flowOf(emptyList()) else repository.getActiveRoadsForPlan(plan.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSdpLotProgressList: StateFlow<List<SdpLotProgress>> = activeSdpPlan.flatMapLatest { plan ->
        if (plan == null) flowOf(emptyList()) else repository.getLotProgressForPlan(plan.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSdpLotProgressMap: StateFlow<Map<Long, SdpLotProgress>> = activeSdpLotProgressList.map { list ->
        list.associateBy { it.sdpLotId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun updateLotProgress(
        projectId: Long,
        sdpPlanId: Long,
        sdpLotId: Long,
        physicalProgress: Int,
        constructionStatus: String,
        currentActivity: String,
        startDate: String,
        targetCompletionDate: String,
        contractor: String,
        remarks: String,
        // Null keeps the lot's photos as they are.
        photos: List<String>? = null,
        // Road lots only; null keeps the saved value.
        drainageProgress: Int? = null
    ) {
        val safeProgress = physicalProgress.coerceIn(0, 100)
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val existing = activeSdpLotProgressMap.value[sdpLotId]
            
            val progressRecord = SdpLotProgress(
                id = existing?.id ?: 0,
                projectId = projectId,
                sdpPlanId = sdpPlanId,
                sdpLotId = sdpLotId,
                physicalProgress = safeProgress,
                drainageProgress = (drainageProgress ?: existing?.drainageProgress ?: 0).coerceIn(0, 100),
                constructionStatus = constructionStatus,
                currentActivity = currentActivity,
                startDate = startDate,
                targetCompletionDate = targetCompletionDate,
                contractor = contractor,
                remarks = remarks,
                billingStatus = existing?.billingStatus ?: "NOT BILLED",
                billingDate = existing?.billingDate ?: "",
                billedBy = existing?.billedBy ?: "",
                billingReference = existing?.billingReference ?: "",
                billingRemarks = existing?.billingRemarks ?: "",
                m2BillingStatus = existing?.m2BillingStatus ?: "NOT BILLED",
                m2BillingDate = existing?.m2BillingDate ?: "",
                m2BilledBy = existing?.m2BilledBy ?: "",
                m2BillingReference = existing?.m2BillingReference ?: "",
                m2BillingRemarks = existing?.m2BillingRemarks ?: "",
                photosJson = photos?.let(com.example.data.files.Attachments::toJson) ?: existing?.photosJson ?: "[]",
                createdBy = existing?.createdBy?.ifBlank { user } ?: user,
                createdDate = existing?.createdDate?.ifBlank { currentDate } ?: currentDate,
                lastModifiedBy = user,
                lastModifiedDate = currentDate
            )
            
            repository.insertOrUpdateLotProgress(progressRecord, currentUserAccount.value.role)
            
            val oldVal = existing?.let { "Progress: ${it.physicalProgress}%, Status: ${it.constructionStatus}" } ?: "Not Started"
            logAuditAction(
                projectId = projectId,
                actionType = "Construction Progress Update",
                details = "Updated Lot Progress (Lot ID: $sdpLotId) to $safeProgress% ($constructionStatus)",
                oldValue = oldVal,
                newValue = "Progress: $safeProgress%, Status: $constructionStatus, Activity: $currentActivity"
            )
        }
    }

    /** Marks [milestone] 1 (M1, 80%) or 2 (M2, 20%) of the lot billed or not; the other milestone is kept. */
    fun updateLotBillingStatus(
        projectId: Long,
        sdpPlanId: Long,
        sdpLotId: Long,
        milestone: Int,
        isBilled: Boolean,
        billingReference: String = "",
        billingRemarks: String = ""
    ) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val existing = activeSdpLotProgressMap.value[sdpLotId]

            val newStatus = if (isBilled) "BILLED" else "NOT BILLED"
            val m1 = milestone == 1
            val progressRecord = SdpLotProgress(
                id = existing?.id ?: 0,
                projectId = projectId,
                sdpPlanId = sdpPlanId,
                sdpLotId = sdpLotId,
                physicalProgress = existing?.physicalProgress ?: 0,
                drainageProgress = existing?.drainageProgress ?: 0,
                constructionStatus = existing?.constructionStatus ?: "Not Started",
                currentActivity = existing?.currentActivity ?: "",
                startDate = existing?.startDate ?: "",
                targetCompletionDate = existing?.targetCompletionDate ?: "",
                contractor = existing?.contractor ?: "",
                remarks = existing?.remarks ?: "",
                billingStatus = if (m1) newStatus else existing?.billingStatus ?: "NOT BILLED",
                billingDate = if (!m1) existing?.billingDate ?: "" else if (isBilled) currentDate else "",
                billedBy = if (!m1) existing?.billedBy ?: "" else if (isBilled) user else "",
                billingReference = if (!m1) existing?.billingReference ?: "" else if (isBilled) billingReference else "",
                billingRemarks = if (!m1) existing?.billingRemarks ?: "" else if (isBilled) billingRemarks else "",
                m2BillingStatus = if (!m1) newStatus else existing?.m2BillingStatus ?: "NOT BILLED",
                m2BillingDate = if (m1) existing?.m2BillingDate ?: "" else if (isBilled) currentDate else "",
                m2BilledBy = if (m1) existing?.m2BilledBy ?: "" else if (isBilled) user else "",
                m2BillingReference = if (m1) existing?.m2BillingReference ?: "" else if (isBilled) billingReference else "",
                m2BillingRemarks = if (m1) existing?.m2BillingRemarks ?: "" else if (isBilled) billingRemarks else "",
                photosJson = existing?.photosJson ?: "[]",
                createdBy = existing?.createdBy?.ifBlank { user } ?: user,
                createdDate = existing?.createdDate?.ifBlank { currentDate } ?: currentDate,
                lastModifiedBy = user,
                lastModifiedDate = currentDate
            )

            repository.insertOrUpdateLotProgress(progressRecord, currentUserAccount.value.role)

            logAuditAction(
                projectId = projectId,
                actionType = "Developer Billing Update",
                details = "Updated M$milestone billing for Lot ID $sdpLotId to $newStatus (Ref: $billingReference)",
                oldValue = existing.billingLabel,
                newValue = "${progressRecord.billingLabel}, Billed By: $user, Date: $currentDate, Ref: $billingReference"
            )
        }
    }

    fun recordLotInspection(
        projectId: Long,
        sdpPlanId: Long,
        sdpLotId: Long,
        physicalProgress: Int,
        constructionStatus: String,
        currentActivity: String,
        contractor: String,
        remarks: String,
        billingStatus: String,
        billingReference: String
    ) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val safeProgress = physicalProgress.coerceIn(0, 100)

            val inspectionRecord = SdpLotInspection(
                projectId = projectId,
                sdpPlanId = sdpPlanId,
                sdpLotId = sdpLotId,
                inspectionTimestamp = System.currentTimeMillis(),
                inspectionDate = currentDate,
                inspectedBy = user,
                physicalProgress = safeProgress,
                constructionStatus = constructionStatus,
                currentActivity = currentActivity,
                contractor = contractor,
                remarks = remarks,
                billingStatus = billingStatus,
                billingReference = billingReference,
                createdDate = currentDate
            )

            repository.insertSdpLotInspection(inspectionRecord, currentUserAccount.value.role)

            // Also update current SdpLotProgress state to match snapshot
            updateLotProgress(
                projectId = projectId,
                sdpPlanId = sdpPlanId,
                sdpLotId = sdpLotId,
                physicalProgress = safeProgress,
                constructionStatus = constructionStatus,
                currentActivity = currentActivity,
                startDate = "",
                targetCompletionDate = "",
                contractor = contractor,
                remarks = "Snapshot recorded: $remarks"
            )

            logAuditAction(
                projectId = projectId,
                actionType = "Inspection Snapshot Recorded",
                details = "Recorded formal inspection for Lot ID $sdpLotId: $safeProgress% ($constructionStatus)",
                oldValue = "N/A",
                newValue = "Progress: $safeProgress%, Inspector: $user, Date: $currentDate"
            )
        }
    }

    fun getInspectionsForLot(sdpLotId: Long): Flow<List<SdpLotInspection>> {
        return repository.getInspectionsForLot(sdpLotId)
    }

    // PHASE 9 BENCHMARK DATASET GENERATOR (100 - 2,000+ LOTS)
    fun generateTestSdpLots(projectId: Long, sdpPlanId: Long, count: Int) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val rows = Math.ceil(Math.sqrt(count.toDouble())).toInt()
            val cols = Math.ceil(count.toDouble() / rows).toInt()
            val lotW = 0.85f / cols
            val lotH = 0.85f / rows
            
            for (i in 1..count) {
                val r = (i - 1) / cols
                val c = (i - 1) % cols
                val blockNo = (r + 1).toString()
                val lotNo = (c + 1).toString()
                val x1 = 0.05f + (c * lotW)
                val y1 = 0.05f + (r * lotH)
                val x2 = x1 + (lotW * 0.90f)
                val y2 = y1 + (lotH * 0.90f)
                val json = "[{\"x\":$x1,\"y\":$y1},{\"x\":$x2,\"y\":$y1},{\"x\":$x2,\"y\":$y2},{\"x\":$x1,\"y\":$y2}]"
                
                val lot = SdpLot(
                    projectId = projectId,
                    sdpPlanId = sdpPlanId,
                    blockNumber = blockNo,
                    lotNumber = lotNo,
                    housingUnitNumber = "BLK$blockNo-LOT$lotNo",
                    lotAreaSqM = 50.0 + (i % 30),
                    polygonNormalizedJson = json,
                    description = "Benchmark Lot #$i",
                    createdBy = user,
                    createdDate = currentDate
                )
                repository.insertSdpLot(lot, currentUserAccount.value.role)
            }
            
            logAuditAction(
                projectId = projectId,
                actionType = "Benchmark Dataset Generated",
                details = "Generated $count test lots for performance testing."
            )
        }
    }

    /**
     * [newSite]: the name of another site under this contract; null: a revision of the site on screen.
     * [currentSiteName] names the site on screen when it has no name yet.
     */
    fun addSdpPlan(
        projectId: Long,
        planName: String,
        pdfFileUrl: String,
        description: String = "",
        newSite: String? = null,
        currentSiteName: String = ""
    ) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            var site = activeSdpPlan.value?.takeIf { it.projectId == projectId }?.siteName ?: ""
            if (newSite != null) {
                if (site.isBlank() && currentSiteName.isNotBlank()) {
                    repository.renameSdpSite(projectId, "", currentSiteName.trim(), currentUserAccount.value.role)
                }
                site = newSite.trim()
            }
            val existingPlans = repository.getSdpPlansForProject(projectId).first().filter { it.siteName == site }
            val nextVersion = (existingPlans.maxOfOrNull { it.version } ?: 0) + 1
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val plan = SdpPlan(
                projectId = projectId,
                planName = planName,
                pdfFileUrl = pdfFileUrl,
                version = nextVersion,
                isActive = true,
                uploadedDate = currentDate,
                uploadedBy = user,
                description = description,
                siteName = site
            )
            repository.insertSdpPlan(plan, currentUserAccount.value.role)
            // Shows the new plan, so lot detection runs on it.
            selectSdpSite(projectId, site)
            val siteNote = if (site.isBlank()) "" else " ($site)"
            logAuditAction(
                projectId = projectId,
                actionType = "Document Upload",
                details = "Uploaded Approved SDP Plan$siteNote Version $nextVersion: $planName",
                oldValue = "",
                newValue = "Plan: $planName, Version: $nextVersion, File: $pdfFileUrl"
            )
        }
    }

    /** The plan PDF's first page as line pixels, kept for the last plan so taps on it are quick. */
    private class PlanMask(val planUuid: String, val dark: BooleanArray, val width: Int, val height: Int)
    private var planMask: PlanMask? = null
    private val planMaskLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun planMask(plan: SdpPlan): PlanMask? = planMaskLock.withLock {
        planMask?.takeIf { it.planUuid == plan.uuid }?.let { return@withLock it }
        val file = com.example.data.files.Attachments.ensureLocal(getApplication(), plan.pdfFileUrl) ?: return@withLock null
        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                renderer.openPage(0).use { page ->
                    // Lot lines on a CAD sheet are thin; ~3200 px keeps a typical lot tens of pixels wide.
                    val scale = 3200f / maxOf(page.width, page.height)
                    val w = (page.width * scale).toInt(); val h = (page.height * scale).toInt()
                    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val dark = BooleanArray(w * h)
                    val row = IntArray(w)
                    for (y in 0 until h) {
                        bitmap.getPixels(row, 0, w, 0, y, w, 1)
                        for (x in 0 until w) {
                            val c = row[x]
                            val lum = (299 * ((c shr 16) and 255) + 587 * ((c shr 8) and 255) + 114 * (c and 255)) / 1000
                            dark[y * w + x] = lum < 225
                        }
                    }
                    bitmap.recycle()
                    PlanMask(plan.uuid, dark, w, h).also { planMask = it }
                }
            }
        }
    }

    /** Finds the lots in the plan's PDF (page 1), named by the block and lot printed on it; null when the PDF cannot be opened. */
    fun detectSdpLots(plan: SdpPlan, onResult: (List<com.example.util.LotDetector.Area>?) -> Unit) {
        viewModelScope.launch {
            val areas = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { planMask(plan)?.let { com.example.util.LotDetector.detect(it.dark, it.width, it.height) } }.getOrNull()
                    ?.let { areas -> namedFromPlan(plan, areas) }
            }
            onResult(areas)
        }
    }

    /** [areas] with the block and lot the plan prints on each lot; unchanged when the PDF's text cannot be read. */
    private suspend fun namedFromPlan(plan: SdpPlan, areas: List<com.example.util.LotDetector.Area>): List<com.example.util.LotDetector.Area> {
        val file = com.example.data.files.Attachments.ensureLocal(getApplication(), plan.pdfFileUrl) ?: return areas
        val page = runCatching { com.example.util.PlanLabels.read(getApplication(), file) }.getOrNull() ?: return areas
        val lots = areas.filter { it.isLot }
        val names = com.example.util.PlanLabels.name(lots.map { it.polygon }, lots.map { it.block }, page).iterator()
        return areas.map { a -> if (a.isLot) names.next().let { (b, l) -> a.copy(blockName = b, lotName = l) } else a }
    }

    /** The outline of the lot around a tapped point (0..1) of the plan, for a lot detection missed. */
    fun traceSdpLot(plan: SdpPlan, x: Float, y: Float, onResult: (List<Pair<Float, Float>>?) -> Unit) {
        viewModelScope.launch {
            val outline = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { planMask(plan)?.let { com.example.util.LotDetector.traceAt(it.dark, it.width, it.height, x, y) } }.getOrNull()
            }
            onResult(outline)
        }
    }

    private val pageSizes = java.util.concurrent.ConcurrentHashMap<String, Pair<Double, Double>>()

    /** Page 1 of the plan's PDF in points (1/72 inch); null when the PDF cannot be opened. */
    private suspend fun planPageSize(plan: SdpPlan): Pair<Double, Double>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        pageSizes[plan.uuid] ?: runCatching {
            val file = com.example.data.files.Attachments.ensureLocal(getApplication(), plan.pdfFileUrl) ?: return@runCatching null
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { r -> r.openPage(0).use { p -> p.width.toDouble() to p.height.toDouble() } }
            }
        }.getOrNull()?.also { pageSizes[plan.uuid] = it }
    }

    private fun polygonOf(json: String): List<Pair<Double, Double>> = runCatching {
        val pts = org.json.JSONArray(json)
        (0 until pts.length()).map { pts.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } }
    }.getOrDefault(emptyList())

    /** Square metres of [polygon] at the plan's scale; null when the plan has no scale or no readable PDF. */
    private suspend fun areaOn(plan: SdpPlan, polygon: List<Pair<Double, Double>>, scale: Int = plan.scaleDenominator): Double? {
        if (scale <= 0) return null
        val (w, h) = planPageSize(plan) ?: return null
        return com.example.util.LotArea.squareMetres(polygon, w, h, scale)
    }

    /** The area to suggest for a lot being added, from the plan's scale. */
    fun sdpLotArea(plan: SdpPlan, polygon: List<Pair<Float, Float>>, onResult: (Double?) -> Unit) {
        viewModelScope.launch { onResult(areaOn(plan, polygon.map { (x, y) -> x.toDouble() to y.toDouble() })) }
    }

    /** Sets the plan's printed scale and works out the areas of its lots that nobody typed in. */
    fun setSdpPlanScale(plan: SdpPlan, scale: Int) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val lots = activeSdpLots.value.filter { it.sdpPlanId == plan.id }.mapNotNull { lot ->
                val polygon = polygonOf(lot.polygonNormalizedJson)
                val area = areaOn(plan, polygon, scale) ?: return@mapNotNull null
                val oldWorkedOut = areaOn(plan, polygon)
                // Typed areas stay: only blank ones, the add dialog's 50 default, or the last worked-out value change.
                val untyped = lot.lotAreaSqM == 0.0 || lot.lotAreaSqM == 50.0 || (oldWorkedOut != null && kotlin.math.abs(lot.lotAreaSqM - oldWorkedOut) < 0.01)
                if (untyped && area != lot.lotAreaSqM) lot.copy(lotAreaSqM = area, lastModifiedBy = user, lastModifiedDate = currentDate) else null
            }
            repository.setSdpPlanScale(plan.copy(scaleDenominator = scale), lots, currentUserAccount.value.role)
            logAuditAction(
                projectId = plan.projectId,
                actionType = "Project Edit",
                details = "Set SDP Plan v${plan.version} scale to 1:$scale and worked out ${lots.size} lot areas",
                oldValue = if (plan.scaleDenominator > 0) "1:${plan.scaleDenominator}" else "No scale",
                newValue = "1:$scale"
            )
        }
    }

    /** Removes the plan's auto-detected lots that have no progress yet. */
    fun removeDetectedSdpLots(plan: SdpPlan) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val removed = repository.replaceDetectedLots(plan.id, emptyList(), DETECTED_LOT, user, currentUserAccount.value.role)
            logAuditAction(
                projectId = plan.projectId,
                actionType = "Lot Deactivated",
                details = "Removed $removed auto-detected lots from SDP Plan v${plan.version}",
                oldValue = "Active",
                newValue = "Deactivated"
            )
        }
    }

    /** Adds detected areas as lots, replacing earlier detected lots that have no progress yet. */
    fun addDetectedSdpLots(plan: SdpPlan, areas: List<com.example.util.LotDetector.Area>) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            // Lots that stay (added by hand, or with progress) must not get a found copy on top.
            val progress = activeSdpLotProgressMap.value
            val kept = activeSdpLots.value.filter { it.sdpPlanId == plan.id && (!it.description.startsWith(DETECTED_LOT) || it.id in progress) }
                .mapNotNull { lot ->
                    runCatching {
                        val pts = org.json.JSONArray(lot.polygonNormalizedJson)
                        val xs = (0 until pts.length()).map { pts.getJSONArray(it).getDouble(0) }
                        val ys = (0 until pts.length()).map { pts.getJSONArray(it).getDouble(1) }
                        doubleArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
                    }.getOrNull()
                }
            // A name the plan gives a found lot that a kept lot already has stays off, so no name is used twice.
            val keptNames = activeSdpLots.value.filter { it.sdpPlanId == plan.id && !it.isUnassigned && (!it.description.startsWith(DETECTED_LOT) || it.id in progress) }
                .map { it.blockNumber to it.lotNumber }.toSet()
            val lots = areas.filter { a ->
                val cx = a.polygon.map { it.first }.average(); val cy = a.polygon.map { it.second }.average()
                kept.none { b -> cx in b[0]..b[2] && cy in b[1]..b[3] }
            }.map { a ->
                val named = a.lotName.isNotBlank() && (a.blockName to a.lotName) !in keptNames
                SdpLot(
                    projectId = plan.projectId,
                    sdpPlanId = plan.id,
                    // The block and lot printed on the plan, else none and the engineer names the lot.
                    blockNumber = if (named) a.blockName else "",
                    lotNumber = if (named) a.lotName else "",
                    lotUse = if (a.isLot) "" else "Open Space",
                    lotAreaSqM = areaOn(plan, a.polygon.map { (x, y) -> x.toDouble() to y.toDouble() }) ?: 0.0,
                    polygonNormalizedJson = org.json.JSONArray(a.polygon.map { (x, y) -> org.json.JSONArray(listOf(x.toDouble(), y.toDouble())) }).toString(),
                    description = if (a.isLot) DETECTED_LOT else "$DETECTED_LOT open space or facility",
                    createdBy = user,
                    createdDate = currentDate,
                    lastModifiedBy = user,
                    lastModifiedDate = currentDate
                )
            }
            repository.replaceDetectedLots(plan.id, lots, DETECTED_LOT, user, currentUserAccount.value.role)
            logAuditAction(
                projectId = plan.projectId,
                actionType = "Lot Created",
                details = "Added ${lots.size} lots found in SDP Plan v${plan.version}",
                oldValue = "",
                newValue = "${lots.count { it.isSaleable }} lots, ${lots.count { !it.isSaleable }} larger areas"
            )
        }
    }

    fun moveSdpPlanToNewSite(plan: SdpPlan, newSite: String, currentSiteName: String = "") {
        viewModelScope.launch {
            repository.moveSdpPlanToNewSite(plan, newSite.trim(), currentSiteName.trim(), currentUserAccount.value.role)
            selectSdpSite(plan.projectId, newSite.trim())
            val oldSite = com.example.data.model.SdpSites.label(currentSiteName.trim().ifBlank { plan.siteName })
            logAuditAction(
                projectId = plan.projectId,
                actionType = "Project Edit",
                details = "Made SDP Plan v${plan.version} of $oldSite its own site: ${newSite.trim()}",
                oldValue = "Site: $oldSite, Version: ${plan.version}",
                newValue = "Site: ${newSite.trim()}, Version: 1"
            )
        }
    }

    /** [before] and [currentSiteName] as given to [moveSdpPlanToNewSite]. */
    fun undoMoveSdpPlanToNewSite(before: SdpPlan, newSite: String, currentSiteName: String = "") {
        viewModelScope.launch {
            repository.undoMoveSdpPlanToNewSite(before, currentSiteName.trim(), currentUserAccount.value.role)
            selectSdpSite(before.projectId, before.siteName)
            val oldSite = com.example.data.model.SdpSites.label(before.siteName)
            logAuditAction(
                projectId = before.projectId,
                actionType = "Project Edit",
                details = "Undid making SDP Plan v${before.version} its own site: back in $oldSite",
                oldValue = "Site: ${newSite.trim()}, Version: 1",
                newValue = "Site: $oldSite, Version: ${before.version}"
            )
        }
    }

    fun setActiveSdpPlan(projectId: Long, sdpPlanId: Long) {
        viewModelScope.launch {
            repository.setActiveSdpPlan(projectId, sdpPlanId, currentUserAccount.value.role)
            logAuditAction(
                projectId = projectId,
                actionType = "Project Edit",
                details = "Set active SDP Plan ID: $sdpPlanId",
                oldValue = "",
                newValue = "Active SDP Plan ID: $sdpPlanId"
            )
        }
    }

    fun addSdpLot(
        projectId: Long,
        sdpPlanId: Long,
        blockNumber: String,
        lotNumber: String,
        housingUnitNumber: String,
        lotAreaSqM: Double,
        polygonNormalizedJson: String,
        description: String = "",
        lotUse: String = ""
    ) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val lot = SdpLot(
                projectId = projectId,
                sdpPlanId = sdpPlanId,
                blockNumber = blockNumber,
                lotNumber = lotNumber,
                housingUnitNumber = housingUnitNumber,
                lotAreaSqM = lotAreaSqM,
                polygonNormalizedJson = polygonNormalizedJson,
                isActive = true,
                description = description,
                lotUse = lotUse,
                createdBy = user,
                createdDate = currentDate,
                lastModifiedBy = user,
                lastModifiedDate = currentDate
            )
            val lotId = repository.insertSdpLot(lot, currentUserAccount.value.role)
            logAuditAction(
                projectId = projectId,
                actionType = "Lot Created",
                details = "Digitized Lot B${blockNumber}-L${lotNumber} (ID: $lotId)",
                oldValue = "",
                newValue = "${lotUse.ifBlank { "Saleable" }}: Block $blockNumber, Lot $lotNumber, Unit: $housingUnitNumber, Area: ${lotAreaSqM}sqm"
            )
        }
    }

    fun updateSdpLot(lot: SdpLot) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val updated = lot.copy(lastModifiedBy = user, lastModifiedDate = currentDate)
            repository.updateSdpLot(updated, currentUserAccount.value.role)
            logAuditAction(
                projectId = lot.projectId,
                actionType = "Lot Geometry Changed",
                details = "Updated Lot B${lot.blockNumber}-L${lot.lotNumber} (ID: ${lot.id})",
                oldValue = "",
                newValue = "Updated boundary/info for Lot ID ${lot.id}"
            )
        }
    }

    /** Marks [lots] as one of LOT_USES (saleable, road lot, open space, ...). */
    fun setSdpLotUse(lots: List<SdpLot>, use: String) {
        if (lots.isEmpty()) return
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            repository.updateSdpLots(lots.map { it.copy(lotUse = use, lastModifiedBy = user, lastModifiedDate = currentDate) }, currentUserAccount.value.role)
            logAuditAction(
                projectId = lots.first().projectId,
                actionType = "Lot Use Changed",
                details = "Marked ${lots.size} lot(s) as $use: " + lots.take(20).joinToString { "B${it.blockNumber}-L${it.lotNumber}" },
                oldValue = lots.map { it.use }.distinct().joinToString(),
                newValue = use
            )
        }
    }

    /** Gives a lot the block and lot names the user typed, e.g. "12" and "5-A". */
    fun renameSdpLot(lot: SdpLot, block: String, lotNo: String) {
        if (block.isBlank() || lotNo.isBlank() || (block == lot.blockNumber && lotNo == lot.lotNumber)) return
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            repository.updateSdpLots(listOf(lot.copy(blockNumber = block, lotNumber = lotNo, lastModifiedBy = user, lastModifiedDate = currentDate)), currentUserAccount.value.role)
            logAuditAction(
                projectId = lot.projectId,
                actionType = "Lot Renamed",
                details = "Renamed B${lot.blockNumber}-L${lot.lotNumber} to B$block-L$lotNo (Lot ID: ${lot.id})",
                oldValue = "Block ${lot.blockNumber} Lot ${lot.lotNumber}",
                newValue = "Block $block Lot $lotNo"
            )
        }
    }

    /** Saves the plan area of the page used for the print; null goes back to the whole page. */
    fun setSdpPlanFrame(plan: SdpPlan, frame: List<Float>?) {
        viewModelScope.launch {
            repository.setSdpPlanScale(plan.copy(frame = frame?.let(com.example.util.SdpSummary::formatFrame) ?: ""), emptyList(), currentUserAccount.value.role)
        }
    }

    /** Turns the plan a quarter clockwise on screen; lots keep their page coordinates. */
    fun rotateSdpPlan(plan: SdpPlan) {
        viewModelScope.launch {
            repository.setSdpPlanScale(plan.copy(rotation = com.example.util.PlanRotation.next(plan.rotation)), emptyList(), currentUserAccount.value.role)
        }
    }

    /** Deletes the plan, its lots and their progress; the delete syncs, so other phones and the website drop it too. */
    fun deleteSdpPlan(plan: SdpPlan) {
        viewModelScope.launch(missingProjectGuard) {
            repository.deleteSdpPlan(plan, currentUserAccount.value.role)
            val siteNote = if (plan.siteName.isBlank()) "" else " (${plan.siteName})"
            logAuditAction(
                projectId = plan.projectId,
                actionType = "Document Deletion",
                details = "Deleted SDP Plan$siteNote Version ${plan.version}: ${plan.planName}, with its lots and progress",
                oldValue = "Plan: ${plan.planName}, Version: ${plan.version}",
                newValue = ""
            )
        }
    }

    /**
     * The SDP print of [plans] (one per site): each plan's frame (saved, or around all lots) with each lot
     * coloured by progress, and the legend over it. Null when a plan PDF cannot be opened.
     */
    suspend fun sdpPlanForm(project: Project, plans: List<SdpPlan>, landscape: Boolean): String? {
        val progress = repository.getLotProgressForProject(project.id).first().associateBy { it.sdpLotId }
        val sheets = plans.map { plan ->
            val lots = repository.getActiveLotsForPlan(plan.id).first()
            val image = sdpPlanImage(plan, lots, progress) ?: return null
            val summary = com.example.util.SdpSummary.of(
                lots.map { com.example.util.SdpSummary.Item(it.use, it.lotAreaSqM, progress[it.id]?.physicalProgress ?: 0) }
            )
            com.example.util.BlueBookForms.SdpSheet(plan.siteName, image, summary)
        }
        val bandHex = listOf(0, 1, 26, 51, 76, 100).map { p ->
            "#%06X".format(com.example.ui.detail.tabs.lotProgressFill(p, com.example.ui.theme.LightNHAColors).toArgb() and 0xFFFFFF)
        }
        return com.example.util.BlueBookForms.sdpPlan(project, sheets, bandHex, LocalDate.now(), blueBookBanner, landscape)
    }

    private suspend fun sdpPlanImage(plan: SdpPlan, lots: List<SdpLot>, progress: Map<Long, SdpLotProgress>): String? {
        val polygons = lots.associate { it.id to polygonOf(it.polygonNormalizedJson).map { (x, y) -> x.toFloat() to y.toFloat() } }
        val frame = com.example.util.SdpSummary.parseFrame(plan.frame)
            ?: com.example.util.SdpSummary.frameAround(polygons.values.toList())
            ?: listOf(0f, 0f, 1f, 1f)
        val colors = com.example.ui.theme.LightNHAColors
        fun fill(lot: SdpLot) = com.example.ui.detail.tabs.lotFill(lot, progress[lot.id]?.physicalProgress ?: 0, colors)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val file = com.example.data.files.Attachments.ensureLocal(getApplication(), plan.pdfFileUrl) ?: return@withContext null
            runCatching {
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val fw = (frame[2] - frame[0]) * page.width
                            val fh = (frame[3] - frame[1]) * page.height
                            // About 300 dpi across a landscape A4 page.
                            val s = 2800f / maxOf(fw, fh)
                            val w = (fw * s).toInt().coerceAtLeast(1)
                            val h = (fh * s).toInt().coerceAtLeast(1)
                            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            val m = android.graphics.Matrix().apply {
                                setScale(s, s)
                                postTranslate(-frame[0] * page.width * s, -frame[1] * page.height * s)
                            }
                            page.render(bitmap, null, m, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val canvas = android.graphics.Canvas(bitmap)
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            for (lot in lots) {
                                val pts = polygons[lot.id].orEmpty().takeIf { it.size >= 3 } ?: continue
                                val path = android.graphics.Path()
                                pts.forEachIndexed { i, (x, y) ->
                                    val px = (x - frame[0]) / (frame[2] - frame[0]) * w
                                    val py = (y - frame[1]) / (frame[3] - frame[1]) * h
                                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                }
                                path.close()
                                paint.color = fill(lot).toArgb()
                                paint.style = android.graphics.Paint.Style.FILL
                                paint.alpha = 115
                                canvas.drawPath(path, paint)
                                paint.style = android.graphics.Paint.Style.STROKE
                                paint.strokeWidth = 2f
                                paint.alpha = 255
                                canvas.drawPath(path, paint)
                            }
                            // Prints the way the plan is turned on screen.
                            val turned = if (plan.rotation.mod(360) == 0) bitmap else android.graphics.Bitmap.createBitmap(
                                bitmap, 0, 0, w, h, android.graphics.Matrix().apply { postRotate(plan.rotation.toFloat()) }, true
                            ).also { bitmap.recycle() }
                            val out = java.io.ByteArrayOutputStream()
                            turned.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            turned.recycle()
                            "data:image/png;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                        }
                    }
                }
            }.getOrNull()
        }
    }

    /** Saves new block and lot numbers for one block; progress and billing follow the lot ids. */
    fun renumberSdpLots(renumbered: List<SdpLot>, oldBlock: String) {
        if (renumbered.isEmpty()) return
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            repository.updateSdpLots(renumbered.map {
                it.copy(
                    description = if (it.description.startsWith(DETECTED_LOT)) RENUMBERED_LOT else it.description,
                    lastModifiedBy = user,
                    lastModifiedDate = currentDate
                )
            }, currentUserAccount.value.role)
            val numbers = renumbered.map { it.lotNumber.toIntOrNull() ?: 0 }
            logAuditAction(
                projectId = renumbered.first().projectId,
                actionType = "Lot Geometry Changed",
                details = if (oldBlock.isBlank()) "Numbered ${renumbered.size} unassigned lots" else "Renumbered ${renumbered.size} lots of Block $oldBlock",
                oldValue = if (oldBlock.isBlank()) "Unassigned" else "Block $oldBlock",
                newValue = "Block ${renumbered.first().blockNumber}, Lots ${numbers.min()}-${numbers.max()}"
            )
        }
    }

    fun deactivateSdpLot(lot: SdpLot) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            repository.deactivateSdpLot(lot.id, user, currentUserAccount.value.role)
            logAuditAction(
                projectId = lot.projectId,
                actionType = "Lot Deactivated",
                details = "Deactivated Lot B${lot.blockNumber}-L${lot.lotNumber} (ID: ${lot.id})",
                oldValue = "Active",
                newValue = "Deactivated"
            )
        }
    }

    fun addSdpRoad(
        projectId: Long,
        sdpPlanId: Long,
        roadName: String,
        roadType: String,
        polylineNormalizedJson: String
    ) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val road = SdpRoad(
                projectId = projectId,
                sdpPlanId = sdpPlanId,
                roadName = roadName,
                roadType = roadType,
                polylineNormalizedJson = polylineNormalizedJson,
                isActive = true,
                createdBy = user,
                createdDate = currentDate,
                lastModifiedBy = user,
                lastModifiedDate = currentDate
            )
            val roadId = repository.insertSdpRoad(road, currentUserAccount.value.role)
            logAuditAction(
                projectId = projectId,
                actionType = "Road Created",
                details = "Digitized Road '$roadName' (ID: $roadId)",
                oldValue = "",
                newValue = "Road: $roadName, Type: $roadType"
            )
        }
    }

    fun updateSdpRoad(road: SdpRoad) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val updated = road.copy(lastModifiedBy = user, lastModifiedDate = currentDate)
            repository.updateSdpRoad(updated, currentUserAccount.value.role)
            logAuditAction(
                projectId = road.projectId,
                actionType = "Road Geometry Changed",
                details = "Updated Road '${road.roadName}' (ID: ${road.id})",
                oldValue = "",
                newValue = "Updated boundary/info for Road ID ${road.id}"
            )
        }
    }

    fun deactivateSdpRoad(road: SdpRoad) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            repository.deactivateSdpRoad(road.id, user, currentUserAccount.value.role)
            logAuditAction(
                projectId = road.projectId,
                actionType = "Road Deactivated",
                details = "Deactivated Road '${road.roadName}' (ID: ${road.id})",
                oldValue = "Active",
                newValue = "Deactivated"
            )
        }
    }

    // Edit States
    val weeklyReportToEdit = MutableStateFlow<WeeklyReport?>(null)
    val monthlyReportToEdit = MutableStateFlow<MonthlyReport?>(null)
    val projectIssueToEdit = MutableStateFlow<ProjectIssue?>(null)

    // State Setter Actions
    fun switchUserAccount(user: UserAccount) {
        _currentUserAccount.value = user
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setStatusFilter(status: String) {
        _statusFilter.value = status
    }

    fun setTypeFilter(type: String) {
        _typeFilter.value = type
    }

    fun setSortOrder(order: String) {
        _sortOrder.value = order
    }

    fun selectProject(projectId: Long?) {
        _selectedProjectId.value = projectId
    }

    // Repository Actions
    fun createProject(
        name: String,
        location: String,
        implementingOffice: String,
        contractor: String,
        scopeOfWork: String,
        projectType: String,
        landArea: String = "",
        numberOfUnits: String = "",
        contractCostOriginal: Double,
        contractDurationDays: Int,
        dateStarted: String,
        completionDateOriginal: String,
        assignedStaff: String = currentUserAccount.value.name,
        /** A project finished before the app: its actual completion date; null for a new project. */
        completedOn: String? = null,
        finalAccomplishment: Double = 100.0
    ) {
        viewModelScope.launch(missingProjectGuard) {
            if (currentUserAccount.value.isDemoAccount) {
                logAuditAction(
                    projectId = null,
                    actionType = "Access Blocked",
                    details = "Attempted project creation in Quick Demo Mode (Blocked)",
                    oldValue = "",
                    newValue = "Blocked Project Creation: $name"
                )
                return@launch
            }
            val duplicateProject = projectDao.getAllProjectsList().firstOrNull { existing ->
                existing.name.normalizeProjectKey() == name.normalizeProjectKey() &&
                    existing.location.normalizeProjectKey() == location.normalizeProjectKey()
            }
            if (duplicateProject != null) {
                android.widget.Toast.makeText(
                    getApplication(),
                    "A project with this name and location already exists.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                logAuditAction(
                    projectId = duplicateProject.id,
                    actionType = "Duplicate Project Blocked",
                    details = "Blocked duplicate project creation: $name ($location)",
                    oldValue = "Existing project ID: ${duplicateProject.id}",
                    newValue = ""
                )
                return@launch
            }
            val creatorUid = authManager.currentFirebaseUser?.uid
            val newProject = Project(
                assignedUids = if (creatorUid.isNullOrBlank()) "" else AssignedUids.toJson(listOf(creatorUid)),
                createdByUid = authManager.currentFirebaseUser?.uid.orEmpty(),
                updatedByUid = authManager.currentFirebaseUser?.uid.orEmpty(),
                name = name,
                location = location,
                implementingOffice = implementingOffice,
                contractor = contractor,
                scopeOfWork = scopeOfWork,
                projectType = projectType,
                landArea = landArea,
                numberOfUnits = numberOfUnits,
                contractCostOriginal = contractCostOriginal,
                contractCostRevised = contractCostOriginal,
                contractDurationDays = contractDurationDays,
                dateStarted = dateStarted,
                completionDateOriginal = completionDateOriginal,
                completionDateRevised = completionDateOriginal,
                status = ProjectStatus.ONGOING.label,
                targetAccomplishment = 0.0,
                actualAccomplishment = 0.0,
                assignedStaff = assignedStaff.ifBlank { currentUserAccount.value.name }
            ).markedCompleted(completedOn, finalAccomplishment)
            val newId = repository.insertProject(newProject, currentUserAccount.value.role)
            val persistedProject = newProject.copy(id = newId)
            syncOutboxDao.enqueue(
                SyncOutboxEvent(
                    entityType = "project",
                    entityUuid = persistedProject.projectUuid,
                    operation = "CREATE",
                    revision = persistedProject.revision,
                    payloadJson = "{\"projectUuid\":\"${persistedProject.projectUuid}\"}"
                )
            )
            firestoreSyncManager.flushOutbox()

            logAuditAction(
                projectId = newId,
                actionType = "Project Creation",
                details = "Created new project: $name",
                oldValue = "",
                newValue = "Title: $name, Location: $location, Cost: ₱$contractCostOriginal, Duration: $contractDurationDays days" +
                    (completedOn?.let { ", Already completed on $it at $finalAccomplishment%" } ?: "")
            )
        }
    }

    /** Loads the cloud `users` list for the assignment picker; a failure leaves it empty and not loaded. */
    fun refreshRegisteredUsers() {
        viewModelScope.launch(missingProjectGuard) {
            try {
                val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").get().await()
                _registeredUsers.value = snapshot.documents.map { doc ->
                    val name = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("email")?.takeIf { it.isNotBlank() }
                        ?: doc.id
                    RegisteredUser(
                        uid = doc.id, name = name, email = doc.getString("email").orEmpty(), role = doc.getString("role").orEmpty(),
                        seesAllProjects = doc.getBoolean("seesAllProjects") == true,
                        approved = doc.getBoolean("approved") != false
                    )
                }.sortedBy { it.name.lowercase() }
                _registeredUsersLoaded.value = true
            } catch (e: Exception) {
                android.util.Log.w("ProjectViewModel", "Loading registered users failed: ${e.message}")
                _registeredUsers.value = emptyList()
                _registeredUsersLoaded.value = false
            }
        }
    }

    /**
     * Saves the edit dialog. When [assignedUsers] is given (Super Admin editing the
     * assignment) the same save also carries the new assignment, as one outbox event.
     */
    fun updateProjectMaster(project: Project, assignedUsers: List<RegisteredUser>? = null) {
        viewModelScope.launch(missingProjectGuard) {
            val previousStatus = selectedProject.value?.displayStatus ?: "Unknown"
            val admin = currentUserAccount.value.role == UserRole.SUPER_ADMIN
            val current = projectDao.getProjectByIdOnce(project.id)
            val base = AssignmentPolicy.mergeAssignment(project, current, if (admin) assignedUsers else null)
            val updatedProject = base.copy(
                revision = project.revision + 1,
                updatedAt = System.currentTimeMillis(),
                updatedByUid = authManager.currentFirebaseUser?.uid.orEmpty()
            )
            repository.updateProject(updatedProject, currentUserAccount.value.role)
            syncOutboxDao.enqueue(
                SyncOutboxEvent(
                    entityType = "project",
                    entityUuid = updatedProject.projectUuid,
                    operation = "UPDATE",
                    revision = updatedProject.revision,
                    payloadJson = "{\"projectUuid\":\"${updatedProject.projectUuid}\",\"name\":\"${updatedProject.name.replace("\"", "\\\"")}\"}"
                )
            )
            logAuditAction(
                projectId = updatedProject.id,
                actionType = "Project Edit",
                details = "Updated project master: ${updatedProject.name}",
                oldValue = "Status: $previousStatus",
                newValue = "Status: ${updatedProject.displayStatus}, Revised Cost: ₱${updatedProject.contractCostRevised}"
            )
        }
    }

    // Blue Book forms (A4 print / Save as PDF), filled from this project's records.

    private suspend fun contractLogs(project: Project) =
        com.example.util.ContractLogs.from(project, repository.getPendingDocuments(project.id).first())

    /** The NHA header banner from assets, as a data URI for the print page. */
    private val blueBookBanner: String by lazy {
        val bytes = getApplication<Application>().assets.open("blue_book_header.png").use { it.readBytes() }
        "data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    /**
     * The project's saved signatories for the weekly or the monthly report; blanks fall back to the
     * signed-in user and the form's usual positions. Prepared by is the same on both.
     */
    fun signatoriesFor(project: Project, weekly: Boolean): com.example.util.Signatories {
        val user = currentUserAccount.value
        val preparedName = project.preparedByName.ifBlank { user.name }
        val preparedPosition = project.preparedByPosition.ifBlank { user.title.ifBlank { "Site Engineer" } }
        // Offices not yet chosen default to the project's district office; the Regional Manager to the region.
        val district = project.implementingOffice.ifBlank { "Bulacan District Office" }
        val preparedOffice = project.preparedByOffice.ifBlank { district }
        return if (weekly) com.example.util.Signatories(
            preparedName, preparedPosition,
            checkedName = project.weeklyCheckedByName,
            checkedPosition = project.weeklyCheckedByPosition.ifBlank { "Principal Engineer A" },
            notedName = project.weeklyNotedByName,
            notedPosition = project.weeklyNotedByPosition.ifBlank { "District Manager" },
            preparedOffice = preparedOffice,
            checkedOffice = project.weeklyCheckedByOffice.ifBlank { district },
            notedOffice = project.weeklyNotedByOffice.ifBlank { district }
        ) else com.example.util.Signatories(
            preparedName, preparedPosition,
            checkedName = project.checkedByName,
            checkedPosition = project.checkedByPosition.ifBlank { "District Manager" },
            notedName = project.notedByName,
            notedPosition = project.notedByPosition.ifBlank { "Regional Manager" },
            preparedOffice = preparedOffice,
            checkedOffice = project.checkedByOffice.ifBlank { district },
            notedOffice = project.notedByOffice.ifBlank { "Region 3 Office" }
        )
    }

    /** Remembers the signatories on the project for the next printout; only roles that can edit the project save. */
    fun saveSignatories(project: Project, s: com.example.util.Signatories, weekly: Boolean) {
        if (!currentUserAccount.value.role.hasPermission(Permission.EDIT_PROJECT)) return
        val prepared = project.copy(preparedByName = s.preparedName, preparedByPosition = s.preparedPosition, preparedByOffice = s.preparedOffice)
        val updated = if (weekly) prepared.copy(
            weeklyCheckedByName = s.checkedName, weeklyCheckedByPosition = s.checkedPosition, weeklyCheckedByOffice = s.checkedOffice,
            weeklyNotedByName = s.notedName, weeklyNotedByPosition = s.notedPosition, weeklyNotedByOffice = s.notedOffice
        ) else prepared.copy(
            checkedByName = s.checkedName, checkedByPosition = s.checkedPosition, checkedByOffice = s.checkedOffice,
            notedByName = s.notedName, notedByPosition = s.notedPosition, notedByOffice = s.notedOffice
        )
        if (updated != project) updateProjectMaster(updated)
    }

    suspend fun projectProfileForm(project: Project): String {
        val user = currentUserAccount.value
        return com.example.util.BlueBookForms.projectProfile(
            project, contractLogs(project), com.example.util.Preparer(user.name, user.title, user.office), LocalDate.now(), blueBookBanner
        )
    }

    suspend fun weeklyReportForm(project: Project, report: WeeklyReport, signatories: com.example.util.Signatories): String {
        val context = getApplication<Application>()
        // Photos go in as data URIs so the print page needs no file access.
        val titles = com.example.data.files.PhotoDetails.parse(report.photoDetailsJson)
        val photos = com.example.data.files.Attachments.parseList(report.attachedPhotoUrlsJson)
            .filterNot { com.example.data.files.Attachments.isPdf(it) }
            .mapNotNull { ref ->
                val src = if (!com.example.data.files.Attachments.isRef(ref)) ref.takeIf { it.startsWith("http") }
                else com.example.data.files.Attachments.ensureLocal(context, ref)?.let { file ->
                    "data:image/jpeg;base64," + android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                }
                src?.let { it to titles[ref]?.title.orEmpty() }
            }
        return com.example.util.BlueBookForms.weeklyReport(
            project, report,
            com.example.ui.detail.tabs.parseManpowerList(report.manpowerJson),
            com.example.ui.detail.tabs.parseEquipmentList(report.equipmentJson),
            com.example.ui.detail.tabs.parseActivitiesList(report.activitiesJson),
            // Only the report's own construction issues; the Issues tab's are the project's and go on the monthly form.
            com.example.ui.detail.tabs.parseIssuesList(report.issuesJson),
            com.example.ui.detail.tabs.parseAccomplishmentItemsList(report.accomplishmentItemsJson),
            repository.getDailyWeather(project.id).first().filter { it.weeklyReportId == report.id },
            photos,
            contractLogs(project),
            signatories,
            blueBookBanner,
            com.example.util.BlueBookForms.weekDocuments(
                report,
                com.example.util.ContractorLetters.merged(projectDao.getContractorLettersForProject(project.id).first(), repository.getPendingDocuments(project.id).first()),
                projectDao.getDeveloperRequestsForProject(project.id).first()
            )
        )
    }

    /** [month] is the Monthly tab's selection, e.g. "July 2026"; null when it can't be read. */
    suspend fun monthlyReportForm(project: Project, month: String, monthWeeklyReports: List<WeeklyReport>, signatories: com.example.util.Signatories): String? {
        val ym = com.example.util.BlueBookForms.parseMonth(month) ?: return null
        val lastWeekly = monthWeeklyReports.maxWithOrNull(com.example.ui.dialogs.WeekRange.byWeek)
        return com.example.util.BlueBookForms.monthlyReport(
            project, ym, lastWeekly,
            lastWeekly?.let { com.example.ui.detail.tabs.parseAccomplishmentItemsList(it.accomplishmentItemsJson) }.orEmpty(),
            repository.getProjectPaymentsForProject(project.id).first(),
            repository.getProjectIssues(project.id).first(),
            contractLogs(project),
            repository.getPendingDocuments(project.id).first(),
            repository.getDailyWeather(project.id).first(),
            repository.getMonthlyReports(project.id).first().firstOrNull { it.reportingMonth.trim().equals(month.trim(), ignoreCase = true) },
            signatories,
            blueBookBanner
        )
    }

    /**
     * The billing-format Abstract of Physical Accomplishment of an uploaded [abstract] with the typed [period];
     * [saleable] is the project's saleable lots, for one without a number of units.
     */
    suspend fun billingAbstractForm(
        project: Project, abstract: ProjectAbstract, period: com.example.util.BillingAbstract.Period, saleable: Int, signatories: com.example.util.Signatories
    ): String = com.example.util.BlueBookForms.billingAbstract(
        project, repository.getPendingDocuments(project.id).first(), repository.getProjectPaymentsForProject(project.id).first(), abstract, period,
        com.example.util.BillingAbstract.units(project, saleable), signatories, blueBookBanner
    )

    /** An uploaded abstract of the actual work with every column of the Excel file, for the print preview. */
    fun actualAbstractForm(project: Project, abstract: ProjectAbstract): String =
        com.example.util.BlueBookForms.actualAbstract(project, abstract, blueBookBanner)

    /** Saves the approved bar chart ([json] from BarChart.toJson; blank removes it). */
    fun saveBarChart(projectId: Long, json: String) {
        viewModelScope.launch(missingProjectGuard) {
            if (!currentUserAccount.value.role.hasPermission(Permission.MANAGE_BAR_CHART)) return@launch
            val current = projectDao.getProjectByIdOnce(projectId) ?: return@launch
            if (current.barChartJson == json) return@launch
            saveProjectChange(current.copy(barChartJson = json), barChart = true)
            logAuditAction(projectId, "Bar Chart", if (json.isBlank()) "Removed the approved bar chart" else "Saved the approved bar chart")
        }
    }

    fun updateProjectStatus(projectId: Long, newStatus: String) {
        viewModelScope.launch(missingProjectGuard) {
            selectedProject.value?.let { current ->
                if (current.id == projectId) {
                    val updated = current.copy(
                        status = newStatus,
                        revision = current.revision + 1,
                        updatedAt = System.currentTimeMillis(),
                        updatedByUid = authManager.currentFirebaseUser?.uid.orEmpty()
                    )
                    repository.updateProject(updated, currentUserAccount.value.role)
                    syncOutboxDao.enqueue(
                        SyncOutboxEvent(
                            entityType = "project",
                            entityUuid = updated.projectUuid,
                            operation = "UPDATE",
                            revision = updated.revision,
                            payloadJson = "{\"projectUuid\":\"${updated.projectUuid}\",\"status\":\"$newStatus\"}"
                        )
                    )
                    logAuditAction(
                        projectId = projectId,
                        actionType = "Project Edit",
                        details = "Changed project status to $newStatus",
                        oldValue = current.status,
                        newValue = newStatus
                    )
                }
            }
        }
    }

    /**
     * Saves a change to a project's master record and queues it for upload. Writing to
     * the DAO directly skips the revision bump and the outbox, so other phones never see it.
     */
    private suspend fun saveProjectChange(updated: Project, barChart: Boolean = false) {
        val versioned = updated.copy(
            revision = updated.revision + 1,
            updatedAt = System.currentTimeMillis(),
            updatedByUid = authManager.currentFirebaseUser?.uid.orEmpty()
        )
        projectDao.updateProject(versioned)
        syncOutboxDao.enqueue(
            SyncOutboxEvent(
                entityType = "project",
                entityUuid = versioned.projectUuid,
                operation = "UPDATE",
                revision = versioned.revision,
                // Only a bar chart save uploads the chart (see barChartPayload).
                payloadJson = if (barChart) barChartPayload(versioned.projectUuid) else "{\"projectUuid\":\"${versioned.projectUuid}\"}"
            )
        )
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch(missingProjectGuard) {
            // Look the project up by id: deletes come from the dashboard, where no project is selected.
            val projectToDelete = projectDao.getProjectByIdOnce(projectId)
            logAuditAction(
                projectId = null,
                actionType = "Project Deletion",
                details = "Deleted project ID $projectId (${projectToDelete?.name ?: "Project"})",
                oldValue = "Project ID: $projectId, Name: ${projectToDelete?.name ?: ""}",
                newValue = ""
            )
            if (projectToDelete != null) {
                syncOutboxDao.enqueue(
                    SyncOutboxEvent(
                        entityType = "project",
                        entityUuid = projectToDelete.projectUuid,
                        operation = "DELETE",
                        revision = projectToDelete.revision + 1,
                        payloadJson = "{\"projectUuid\":\"${projectToDelete.projectUuid}\",\"isDeleted\":true}"
                    )
                )
            }
            repository.deleteProjectById(projectId, currentUserAccount.value.role)
            if (_selectedProjectId.value == projectId) {
                _selectedProjectId.value = null
            }
        }
    }

    fun addTimeExtension(
        projectId: Long,
        extensionNo: Int,
        noOfDays: Int,
        periodConsidered: String,
        reason: String,
        revisedCompletionDate: String,
        remarks: String
    ) {
        viewModelScope.launch(missingProjectGuard) {
            val p = selectedProject.value ?: return@launch
            val existingExtensions = projectDao.getTimeExtensionsForProject(projectId).first()
            val sumOfDays = existingExtensions.sumOf { it.noOfDays } + noOfDays
            val revisedDurationDays = p.contractDurationDays + sumOfDays

            var computedRevisedDate = p.completionDateOriginal
            try {
                val start = LocalDate.parse(p.dateStarted)
                computedRevisedDate = com.example.util.ProjectSchedule.lastDay(start, revisedDurationDays).toString()
            } catch (e: Exception) {}

            val extension = TimeExtension(
                projectId = projectId,
                extensionNo = extensionNo,
                noOfDays = noOfDays,
                revisedDurationDays = revisedDurationDays,
                periodConsidered = periodConsidered,
                reason = reason,
                revisedCompletionDate = computedRevisedDate,
                remarks = remarks
            )
            projectDao.insertTimeExtension(extension)

            val updated = p.copy(completionDateRevised = computedRevisedDate)
            saveProjectChange(updated)

            logAuditAction(
                projectId = projectId,
                actionType = "Project Edit",
                details = "Added Time Extension No. $extensionNo (+ $noOfDays days)",
                oldValue = "Completion: ${p.completionDateOriginal}",
                newValue = "Revised Completion: $computedRevisedDate"
            )
        }
    }

    fun addWorkSuspensionOrder(
        projectId: Long,
        name: String,
        effectivityDate: String,
        durationDays: Int,
        endDate: String,
        reason: String,
        remarks: String
    ) {
        viewModelScope.launch(missingProjectGuard) {
            val suspension = WorkSuspensionOrder(
                projectId = projectId,
                name = name,
                effectivityDate = effectivityDate,
                durationDays = durationDays,
                endDate = endDate,
                reason = reason,
                remarks = remarks
            )
            projectDao.insertWorkSuspensionOrder(suspension)
            selectedProject.value?.let { p ->
                saveProjectChange(p.copy(status = ProjectStatus.SUSPENDED.label))
            }

            logAuditAction(
                projectId = projectId,
                actionType = "Project Edit",
                details = "Issued Work Suspension Order: $name",
                oldValue = "Status: Ongoing",
                newValue = "Status: Suspended"
            )
        }
    }

    fun addWorkResumptionLog(
        projectId: Long,
        name: String,
        dateResumed: String,
        reason: String,
        remarks: String
    ) {
        viewModelScope.launch(missingProjectGuard) {
            val resumption = WorkResumptionLog(
                projectId = projectId,
                name = name,
                dateResumed = dateResumed,
                reason = reason,
                remarks = remarks
            )
            projectDao.insertWorkResumptionLog(resumption)
            selectedProject.value?.let { p ->
                saveProjectChange(p.copy(status = ProjectStatus.ONGOING.label))
            }

            logAuditAction(
                projectId = projectId,
                actionType = "Project Edit",
                details = "Issued Work Resumption Log: $name",
                oldValue = "Status: Suspended",
                newValue = "Status: Ongoing"
            )
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    fun addPendingDocument(projectId: Long, documentName: String, remarks: String) {
        viewModelScope.launch(missingProjectGuard) {
            val doc = PendingDocument(
                projectId = projectId,
                documentName = documentName,
                status = "Pending",
                remarks = remarks,
                isCoreChecklist = false
            )
            projectDao.insertPendingDocument(doc)
            logAuditAction(
                projectId = projectId,
                actionType = "Document Upload",
                details = "Added document checklist item: $documentName",
                oldValue = "",
                newValue = "Document: $documentName, Status: Pending"
            )
        }
    }

    fun addRelatedDocument(
        projectId: Long,
        documentName: String,
        status: String = "Approved",
        remarks: String = "",
        fileUrl: String = ""
    ) {
        viewModelScope.launch(missingProjectGuard) {
            val doc = PendingDocument(
                projectId = projectId,
                documentName = documentName,
                status = status,
                remarks = remarks,
                isCoreChecklist = false,
                fileUrl = fileUrl
            )
            projectDao.insertPendingDocument(doc)
            logAuditAction(
                projectId = projectId,
                actionType = "Document Upload",
                details = "Uploaded project document: $documentName",
                oldValue = "",
                newValue = "Doc: $documentName, URL: $fileUrl"
            )
        }
    }

    fun updatePendingDocumentStatus(doc: PendingDocument, newStatus: String, remarks: String) {
        viewModelScope.launch(missingProjectGuard) {
            val updated = doc.copy(status = newStatus, remarks = remarks)
            projectDao.updatePendingDocument(updated)
            logAuditAction(
                projectId = doc.projectId,
                actionType = "Document Upload",
                details = "Updated document status for ${doc.documentName}",
                oldValue = "Status: ${doc.status}",
                newValue = "Status: $newStatus"
            )
        }
    }

    fun updatePendingDocumentFileUrl(doc: PendingDocument, fileUrl: String) {
        viewModelScope.launch(missingProjectGuard) {
            val updated = doc.copy(fileUrl = fileUrl)
            projectDao.updatePendingDocument(updated)
            logAuditAction(
                projectId = doc.projectId,
                actionType = "Document Upload",
                details = "Uploaded file attachment for ${doc.documentName}",
                oldValue = doc.fileUrl,
                newValue = fileUrl
            )
        }
    }

    fun deletePendingDocument(docId: Long) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.deletePendingDocument(docId)
        }
    }

    // Submit Weekly Report & auto-update project target & actual accomplishment!
    fun logAuditAction(
        projectId: Long? = null,
        actionType: String,
        details: String = "",
        oldValue: String = "",
        newValue: String = "",
        user: String? = null
    ) {
        viewModelScope.launch(missingProjectGuard) {
            if (oldValue.isNotBlank() && oldValue == newValue) return@launch

            val currentAccount = currentUserAccount.value
            val userName = user ?: currentAccount.name
            val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

            projectDao.insertAuditLog(
                AuditLog(
                    projectId = projectId,
                    user = userName,
                    device = deviceName,
                    actionType = actionType,
                    oldValue = oldValue,
                    newValue = newValue,
                    details = details,
                    timestamp = System.currentTimeMillis()
                )
            )

            // The Super Admin hears about it from the notifySuperAdminsOnAudit Cloud Function once this entry syncs.
        }
    }

    fun markNotificationAsRead(id: Long) {
        viewModelScope.launch(missingProjectGuard) {
            notificationDao.markAsRead(id)
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch(missingProjectGuard) {
            notificationDao.markAllAsRead()
        }
    }

    fun clearAllNotifications() {
        viewModelScope.launch(missingProjectGuard) {
            notificationDao.clearAll()
        }
    }

    fun submitWeeklyReportWithWeather(
        projectId: Long,
        reportingWeek: String,
        daysElapsed: Int,
        remainingDays: Int,
        targetAccomplishmentPct: Double,
        actualAccomplishmentPct: Double,
        manpowerJson: String,
        equipmentJson: String,
        activitiesJson: String,
        issuesJson: String,
        accomplishmentItemsJson: String,
        documentsIssuedReceivedJson: String,
        attachedPhotoUrl: String,
        photoDetailsJson: String,
        weatherDrafts: List<DailyHourlyWeather>
    ) {
        viewModelScope.launch(missingProjectGuard) {
            try {
                // The form passes its photo list as a JSON array.
                val photos = com.example.data.files.Attachments.parseList(attachedPhotoUrl)
                val photoList = com.example.data.files.Attachments.toJson(photos)
                val report = WeeklyReport(
                    projectId = projectId,
                    reportingWeek = reportingWeek,
                    daysElapsed = daysElapsed,
                    remainingDays = remainingDays,
                    targetAccomplishmentPct = targetAccomplishmentPct,
                    actualAccomplishmentPct = actualAccomplishmentPct,
                    manpowerJson = manpowerJson,
                    equipmentJson = equipmentJson,
                    activitiesJson = activitiesJson,
                    issuesJson = issuesJson,
                    accomplishmentItemsJson = accomplishmentItemsJson,
                    documentsIssuedReceivedJson = documentsIssuedReceivedJson,
                    attachedPhotoUrlsJson = photoList,
                    photoDetailsJson = photoDetailsJson,
                    submittedByStaff = currentUserAccount.value.name
                )
                weeklyReportWeatherWriter.create(report, weatherDrafts)

                // Update Project Master target & actual accomplishment (the latest week's only) & latest update photo.
                val current = projectDao.getProjectById(projectId).first()
                if (current != null) {
                    val latest = isLatestWeek(report)
                    val updated = current.copy(
                        targetAccomplishment = if (latest) targetAccomplishmentPct else current.targetAccomplishment,
                        actualAccomplishment = if (latest && actualFollowsWeekly(projectId)) actualAccomplishmentPct else current.actualAccomplishment,
                        latestUpdatePhotoUrl = photos.firstOrNull() ?: current.latestUpdatePhotoUrl
                    )
                    saveProjectChange(updated)
                }

                logAuditAction(
                    projectId = projectId,
                    actionType = "Report Creation",
                    details = "Submitted Weekly Report for $reportingWeek",
                    oldValue = "",
                    newValue = "Week: $reportingWeek, Target: $targetAccomplishmentPct%, Actual: $actualAccomplishmentPct%"
                )
            } catch (error: Exception) {
                _authError.value = "Weekly report was not saved: ${error.message ?: "database transaction failed"}"
            }
        }
    }

    // Submit Monthly Report
    fun updateWeeklyReport(report: WeeklyReport) {
        viewModelScope.launch(missingProjectGuard) {
            reportDao.updateWeeklyReport(report)
            logAuditAction(report.projectId, "Edited Weekly Report", "Modified report for ${report.reportingWeek}")
            
            // Re-sync Project Master target & actual accomplishment
            selectedProject.value?.let { current ->
                if (current.id == report.projectId && isLatestWeek(report)) {
                    val updated = current.copy(
                        targetAccomplishment = report.targetAccomplishmentPct,
                        actualAccomplishment = if (actualFollowsWeekly(report.projectId)) report.actualAccomplishmentPct else current.actualAccomplishment
                    )
                    saveProjectChange(updated)
                }
            }
            refreshSitePhoto(report.projectId)
        }
    }

    fun updateWeeklyReportWithWeather(report: WeeklyReport, weatherDrafts: List<DailyHourlyWeather>) {
        viewModelScope.launch(missingProjectGuard) {
            try {
                weeklyReportWeatherWriter.update(report, weatherDrafts)
                logAuditAction(report.projectId, "Edited Weekly Report", "Modified report and weather for ${report.reportingWeek}")

                // Keep the Project Master accomplishment in step with the edited report.
                val current = projectDao.getProjectByIdOnce(report.projectId)
                if (current != null && isLatestWeek(report)) {
                    val actual = if (actualFollowsWeekly(report.projectId)) report.actualAccomplishmentPct else current.actualAccomplishment
                    if (current.targetAccomplishment != report.targetAccomplishmentPct || current.actualAccomplishment != actual) {
                        saveProjectChange(current.copy(targetAccomplishment = report.targetAccomplishmentPct, actualAccomplishment = actual))
                    }
                }
                refreshSitePhoto(report.projectId)
            } catch (error: Exception) {
                _authError.value = "Weekly report was not updated: ${error.message ?: "database transaction failed"}"
            }
        }
    }

    fun deleteWeeklyReport(report: WeeklyReport) {
        viewModelScope.launch(missingProjectGuard) {
            reportDao.deleteWeeklyReport(report.id)
            logAuditAction(report.projectId, "Deleted Weekly Report", "Deleted report for ${report.reportingWeek}")
            refreshSitePhoto(report.projectId)
        }
    }

    /** Takes one photo off its weekly report; the report's sync then removes it on the other phones. */
    fun deleteWeeklyReportPhoto(report: WeeklyReport, url: String) {
        viewModelScope.launch(missingProjectGuard) {
            // The saved copy, so a delete right after another doesn't put the first photo back.
            val current = reportDao.getWeeklyReportsForProject(report.projectId).first().find { it.id == report.id } ?: return@launch
            repository.updateWeeklyReport(SitePhotos.withoutPhoto(current, url), currentUserAccount.value.role)
            logAuditAction(report.projectId, "Deleted Photo", "Deleted a photo from the report for ${report.reportingWeek}")
            refreshSitePhoto(report.projectId)
        }
    }

    /** Points the project's card photo at the newest report photo left, after an edit or delete. */
    /** WeekRange.isLatest among the project's saved reports. */
    private suspend fun isLatestWeek(report: WeeklyReport) =
        com.example.ui.dialogs.WeekRange.isLatest(report, reportDao.getWeeklyReportsForProject(report.projectId).first())

    /** A project with an uploaded abstract takes its actual from the latest abstract, not from a weekly report; its target still follows the report. */
    private suspend fun actualFollowsWeekly(projectId: Long) = projectDao.getProjectAbstractsForProject(projectId).first().isEmpty()

    /**
     * Files an uploaded abstract under its "As of" date, in place of the one already filed under that date, and makes
     * the latest abstract the project's actual accomplishment.
     */
    fun saveAbstract(record: ProjectAbstract) {
        viewModelScope.launch(missingProjectGuard) {
            val old = projectDao.getProjectAbstractsForProject(record.projectId).first().firstOrNull { it.asOf == record.asOf }
            if (old == null) projectDao.insertProjectAbstract(record)
            else projectDao.updateProjectAbstract(old.copy(overallPct = record.overallPct, rowsJson = record.rowsJson, fileName = record.fileName, uploadedBy = record.uploadedBy, equivUnits = record.equivUnits))
            followAbstracts(record.projectId)
            val overall = com.example.util.ProjectAbstracts.pct(record.overallPct)
            logAuditAction(
                projectId = record.projectId,
                actionType = if (old == null) "Abstract Upload" else "Updated abstract",
                details = "Abstract as of ${com.example.util.ProjectAbstracts.summary(record)}",
                // logAuditAction drops an entry whose old and new values are the same, and a replacement must still be logged.
                oldValue = old?.let { com.example.util.ProjectAbstracts.pct(it.overallPct) }?.takeIf { it != overall }.orEmpty(),
                newValue = overall
            )
        }
    }

    fun deleteAbstract(record: ProjectAbstract) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.deleteProjectAbstract(record.id)
            followAbstracts(record.projectId)
            logAuditAction(
                projectId = record.projectId,
                actionType = "Deleted abstract",
                details = "Abstract as of ${com.example.util.ProjectAbstracts.summary(record)}",
                oldValue = com.example.util.ProjectAbstracts.pct(record.overallPct),
                newValue = ""
            )
        }
    }

    /** The project's actual after an abstract was saved or deleted; with none left it goes back to the latest weekly report's. */
    private suspend fun followAbstracts(projectId: Long) {
        val current = projectDao.getProjectByIdOnce(projectId) ?: return
        val actual = com.example.util.ProjectAbstracts.projectActualAfter(
            projectDao.getProjectAbstractsForProject(projectId).first(), reportDao.getWeeklyReportsForProject(projectId).first(), current.actualAccomplishment
        )
        if (actual != current.actualAccomplishment) saveProjectChange(current.copy(actualAccomplishment = actual))
    }

    private suspend fun refreshSitePhoto(projectId: Long) {
        val photo = SitePhotos.latest(reportDao.getWeeklyReportsForProject(projectId).first())
        val current = projectDao.getProjectByIdOnce(projectId) ?: return
        if (current.latestUpdatePhotoUrl != photo) saveProjectChange(current.copy(latestUpdatePhotoUrl = photo))
    }

    fun submitMonthlyReport(
        projectId: Long,
        reportingMonth: String,
        scopeWeight: Double,
        scopeTarget: Double,
        scopeActual: Double,
        unworkableDays: Int,
        workableDays: Int,
        recommendations: String,
        paymentsJson: String,
        accomplishmentItemsJson: String = "[]"
    ) {
        viewModelScope.launch(missingProjectGuard) {
            val report = MonthlyReport(
                projectId = projectId,
                reportingMonth = reportingMonth,
                scopeWeightPct = scopeWeight,
                scopeTargetPct = scopeTarget,
                scopeActualPct = scopeActual,
                paymentsJson = paymentsJson,
                unworkableDaysCount = unworkableDays,
                workableDaysCount = workableDays,
                cpesIssuesJson = "[]",
                recommendations = recommendations,
                preparedByName = currentUserAccount.value.name,
                preparedByStatus = "Reviewed",
                accomplishmentItemsJson = accomplishmentItemsJson
            )
            reportDao.insertMonthlyReport(report)
            logAuditAction(
                projectId = projectId,
                actionType = "Report Creation",
                details = "Submitted Monthly Report for $reportingMonth",
                oldValue = "",
                newValue = "Month: $reportingMonth, Target: $scopeTarget%, Actual: $scopeActual%"
            )
        }
    }

    // Update Monthly Sign-off Chain Status
    fun updateMonthlyReportFull(report: MonthlyReport) {
        viewModelScope.launch(missingProjectGuard) {
            reportDao.updateMonthlyReport(report)
            logAuditAction(
                projectId = report.projectId,
                actionType = "Report Creation",
                details = "Modified report for ${report.reportingMonth}",
                oldValue = "Previous report data",
                newValue = "Updated report data"
            )
        }
    }

    fun deleteMonthlyReport(report: MonthlyReport) {
        viewModelScope.launch(missingProjectGuard) {
            reportDao.deleteMonthlyReport(report.id)
            logAuditAction(
                projectId = report.projectId,
                actionType = "Report Deletion",
                details = "Deleted report for ${report.reportingMonth}",
                oldValue = "Report Month: ${report.reportingMonth}",
                newValue = ""
            )
        }
    }


    fun addProjectPayment(payment: ProjectPayment) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.insertProjectPayment(payment)
            logAuditAction(
                projectId = payment.projectId,
                actionType = "Payment Creation",
                details = "Created payment billing: ${payment.name} (${payment.dvNo})",
                oldValue = "",
                newValue = "Billing: ${payment.name}, DV: ${payment.dvNo}, Gross: ₱${payment.grossAmount}, Retention: ${payment.retentionPct}%"
            )
        }
    }

    fun updateProjectPayment(payment: ProjectPayment) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.updateProjectPayment(payment)
            logAuditAction(
                projectId = payment.projectId,
                actionType = "Payment Creation",
                details = "Updated billing: ${payment.name}",
                oldValue = "Previous payment info",
                newValue = "DV: ${payment.dvNo}, Gross: ₱${payment.grossAmount}"
            )
        }
    }
    
    fun deleteProjectPayment(payment: ProjectPayment) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.deleteProjectPayment(payment.id)
            logAuditAction(
                projectId = payment.projectId,
                actionType = "Payment Deletion",
                details = "Deleted billing: ${payment.name}" + if (payment.dvNo.isNotBlank()) " (${payment.dvNo})" else "",
                oldValue = "DV: ${payment.dvNo}, Gross: ${com.example.utils.CurrencyFormatter.formatPhp(payment.grossAmount)}",
                newValue = ""
            )
        }
    }

    fun addProjectIssue(issue: ProjectIssue) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.insertProjectIssue(issue.copy(loggedBy = currentUserAccount.value.name))
            logAuditAction(
                projectId = issue.projectId,
                actionType = "Issue Creation",
                details = "Logged issue: ${issue.description.take(30)}...",
                oldValue = "",
                newValue = "Description: ${issue.description}, Status: ${issue.status}, Critical: ${issue.isCritical}"
            )
        }
    }

    fun updateProjectIssue(issue: ProjectIssue) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.updateProjectIssue(issue)
            logAuditAction(
                projectId = issue.projectId,
                actionType = "Issue Creation",
                details = "Updated issue from ${issue.date}",
                oldValue = "Previous issue state",
                newValue = "Status: ${issue.status}, Remarks: ${issue.remarks}"
            )
        }
    }

    fun deleteProjectIssue(issue: ProjectIssue) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.deleteProjectIssue(issue.id)
            logAuditAction(
                projectId = issue.projectId,
                actionType = "Issue Deletion",
                details = "Deleted issue: ${issue.description.take(30)}...",
                oldValue = "Issue ID: ${issue.id}",
                newValue = ""
            )
        }
    }

    fun saveDeveloperRequest(request: com.example.data.model.DeveloperRequest) {
        viewModelScope.launch(missingProjectGuard) {
            if (request.id == 0L) projectDao.insertDeveloperRequest(request) else projectDao.updateDeveloperRequest(request)
            logAuditAction(
                projectId = request.projectId,
                actionType = "Developer Request",
                details = "${request.requestType} request received ${request.dateReceived}" + if (request.datePackaged.isNotBlank()) ", packaged ${request.datePackaged}" else "",
                oldValue = "",
                newValue = request.details
            )
        }
    }

    fun saveContractorLetter(letter: com.example.data.model.ContractorLetter) {
        viewModelScope.launch(missingProjectGuard) {
            val oldDocument = oldDocumentLetter(letter)
            if (letter.id != 0L) projectDao.updateContractorLetter(letter)
            // An edited old document letter becomes a letter under a new identity, so the document's deletion can't remove it.
            else if (oldDocument != null) projectDao.insertContractorLetter(letter.copy(uuid = java.util.UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis()))
            else projectDao.insertContractorLetter(letter)
            oldDocument?.let { repository.deletePendingDocument(it.id) }
            logAuditAction(
                projectId = letter.projectId,
                actionType = "Letter to Contractor",
                details = "Letter of ${letter.dateSent}: ${letter.subject}" + if (letter.dateReplied.isNotBlank()) ", replied ${letter.dateReplied}" else "",
                oldValue = "",
                newValue = letter.actionTaken
            )
        }
    }

    fun deleteContractorLetter(letter: com.example.data.model.ContractorLetter) {
        viewModelScope.launch(missingProjectGuard) {
            val oldDocument = oldDocumentLetter(letter)
            if (oldDocument != null) repository.deletePendingDocument(oldDocument.id) else projectDao.deleteContractorLetter(letter.id)
            logAuditAction(
                projectId = letter.projectId,
                actionType = "Letter to Contractor",
                details = "Deleted letter of ${letter.dateSent}: ${letter.subject}",
                oldValue = letter.actionTaken,
                newValue = ""
            )
        }
    }

    /** The document a letter from [com.example.util.ContractorLetters.asLetter] came from, or null for a real letter. */
    private suspend fun oldDocumentLetter(letter: com.example.data.model.ContractorLetter): PendingDocument? =
        if (letter.id != 0L) null
        else repository.getPendingDocuments(letter.projectId).first().find { it.uuid == letter.uuid && com.example.util.ContractorLetters.isLetter(it) }

    fun deleteDeveloperRequest(request: com.example.data.model.DeveloperRequest) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.deleteDeveloperRequest(request.id)
            logAuditAction(
                projectId = request.projectId,
                actionType = "Developer Request",
                details = "Deleted ${request.requestType} request received ${request.dateReceived}",
                oldValue = request.details,
                newValue = ""
            )
        }
    }

    fun updateMonthlySignoff(
        monthlyReport: MonthlyReport,
        roleType: String, // "ENGINEER", "DISTRICT", "REGIONAL"
        newStatus: String,
        notes: String
    ) {
        viewModelScope.launch(missingProjectGuard) {
            val currentAccount = currentUserAccount.value
            val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            val trail = runCatching { org.json.JSONArray(monthlyReport.auditTrailJson) }.getOrDefault(org.json.JSONArray())
            trail.put(
                org.json.JSONObject()
                    .put("role", roleType)
                    .put("name", currentAccount.name)
                    .put("action", "$newStatus ($notes)")
                    .put("timestamp", timestamp)
            )
            val updatedAudit = trail.toString()

            val updatedReport = when (roleType) {
                "ENGINEER" -> monthlyReport.copy(
                    preparedByName = currentAccount.name,
                    preparedByStatus = newStatus,
                    auditTrailJson = updatedAudit
                )
                "DISTRICT" -> monthlyReport.copy(
                    checkedByName = currentAccount.name,
                    checkedByStatus = newStatus,
                    auditTrailJson = updatedAudit
                )
                "REGIONAL" -> monthlyReport.copy(
                    notedByName = currentAccount.name,
                    notedByStatus = newStatus,
                    auditTrailJson = updatedAudit
                )
                else -> monthlyReport
            }
            reportDao.updateMonthlyReport(updatedReport)
        }
    }

    /** The empty dashboard's "Sync from Cloud": restart the cloud listener and send queued changes. */
    fun syncFromCloud(onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                firestoreSyncManager.startRealtimeSync()
                firestoreSyncManager.flushOutbox()
                logAuditAction(
                    projectId = null,
                    actionType = "Sync Event",
                    details = "Triggered cloud sync from empty dashboard",
                    oldValue = "N/A",
                    newValue = "Cloud Restore",
                    user = _currentUserAccount.value.name
                )
            } catch (e: Exception) {
                android.util.Log.w("ProjectViewModel", "Cloud sync from dashboard failed: ${e.message}")
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

}

private fun String.normalizeProjectKey(): String =
    trim().lowercase().replace('\u00A0', ' ').replace(Regex("\\s+"), " ")
