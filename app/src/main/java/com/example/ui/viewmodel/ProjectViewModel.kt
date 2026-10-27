package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.data.auth.AuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.WeeklyReportWeatherWriter
import com.example.data.model.*
import kotlinx.coroutines.tasks.await
import com.example.data.remote.AssignmentPolicy
import com.example.data.remote.DriveSyncResult
import com.example.data.remote.FirestoreSyncManager
import com.example.data.remote.GoogleDriveSyncManager
import com.example.data.repository.ProjectRepository
import com.example.util.AppNotificationManager
import com.example.util.AttentionFlag
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
        val EMPTY = PortfolioUiState(PortfolioSummary(0, 0, 0, 0), emptyList(), emptyList(), null, 0)
    }
}

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val projectDao: com.example.data.local.ProjectDao
    private val reportDao: com.example.data.local.ReportDao
    private val syncOutboxDao: com.example.data.local.SyncOutboxDao
    private lateinit var database: AppDatabase
    private lateinit var weeklyReportWeatherWriter: WeeklyReportWeatherWriter
    private val repository: ProjectRepository
    private val googleDriveSyncManager: GoogleDriveSyncManager
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

    // Google Drive Sync State
    val isDriveSyncing: StateFlow<Boolean>
    val lastDriveSyncTime: StateFlow<String?>
    val isAutoSyncEnabled: StateFlow<Boolean>
    val driveAccountEmail: StateFlow<String>

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

    fun elevateUserRole(targetRole: UserRole, positionTitle: String? = null, officeRegion: String? = null) {
        // Role changes are server-only. A mobile client must never elevate a
        // cached account using a PIN, preferences, or UI state.
        _authError.value = "Role changes require an authorized backend administrator."
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

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
        googleDriveSyncManager = GoogleDriveSyncManager(application, database)
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

        isDriveSyncing = googleDriveSyncManager.isSyncing
        lastDriveSyncTime = googleDriveSyncManager.lastSyncTime
        isAutoSyncEnabled = googleDriveSyncManager.isAutoSyncEnabled
        driveAccountEmail = googleDriveSyncManager.driveAccountEmail

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

        userScopedProjects = combine(rawProjects, currentUserAccount) { projects, user ->
            // Super Admin sees everything; everyone else only the projects naming their account.
            val isSuperAdmin = user.role == UserRole.SUPER_ADMIN
            val myUid = authManager.currentFirebaseUser?.uid
            projects
                .filter { AssignmentPolicy.isVisible(isSuperAdmin, myUid, it.assignedUids, it.createdByUid) }
                .distinctBy { project ->
                    "${project.name.normalizeProjectKey()}|${project.location.normalizeProjectKey()}"
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Close the open project when it leaves this phone or is hidden (e.g. the account was
        // unassigned), so its screen can't keep writing into a row that is gone.
        viewModelScope.launch {
            combine(_selectedProjectId, repository.allProjects, currentUserAccount) { id, projects, user ->
                Triple(id, projects.firstOrNull { it.id == id }, user.role == UserRole.SUPER_ADMIN)
            }.collect { (id, project, isSuperAdmin) ->
                if (id == null) return@collect
                // The list may predate a project selected right after it was saved; ask the database.
                val current = project ?: database.projectDao().getProjectByIdOnce(id)
                val visible = current != null && AssignmentPolicy.isVisible(
                    isSuperAdmin, authManager.currentFirebaseUser?.uid, current.assignedUids, current.createdByUid
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
                list = list.filter { it.status.equals(params.status, ignoreCase = true) }
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
                val result = authManager.signUp(cleanEmail, cleanPass, displayName, role, position, office)
                _authLoading.value = false
                if (result.isSuccess) {
                    _authSuccessMessage.value = "Registration Successful! Account created for $cleanEmail. Please type your password to sign in."
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
                    val userAcc = authManager.getRegisteredUserAccount(
                        email = cleanEmail,
                        fallbackRole = role,
                        displayName = displayName,
                        position = position,
                        office = office
                    )
                    _currentUserAccount.value = userAcc
                    _isLoggedIn.value = true
                    _authLoading.value = false
                    authManager.saveUserSession(userAcc, cleanEmail)

                    // Trigger realtime Firestore sync, flush outbox, and sync profile/FCM token
                    firestoreSyncManager.startRealtimeSync()
                    firestoreSyncManager.flushOutbox()
                    val currentFirebaseUser = authManager.currentFirebaseUser
                    if (currentFirebaseUser != null) {
                        firestoreSyncManager.syncUserProfile(
                            uid = currentFirebaseUser.uid,
                            email = cleanEmail,
                            displayName = userAcc.name,
                            role = userAcc.role.name,
                            position = position.orEmpty(),
                            office = office.orEmpty()
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

    fun sendPasswordReset(email: String, newPassword: String? = null) {
        viewModelScope.launch {
            val cleanEmail = email.trim().lowercase()
            if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                _authError.value = "Please enter a valid email address."
                return@launch
            }

            if (!newPassword.isNullOrBlank()) {
                if (newPassword.length < 6) {
                    _authError.value = "New password must be at least 6 characters long."
                    return@launch
                }
                val success = authManager.resetUserPassword(cleanEmail, newPassword)
                if (success) {
                    _authSuccessMessage.value = "Password updated successfully for $cleanEmail! Please sign in with your new password."
                    _authError.value = null
                } else {
                    _authError.value = "Failed to update password for $cleanEmail."
                }
                return@launch
            }

            val result = authManager.sendPasswordReset(cleanEmail)
            if (result.isSuccess) {
                _authSuccessMessage.value = "Password reset email sent to $cleanEmail. Please check your inbox and spam folder."
                _authError.value = null
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Firebase Auth email delivery unconfigured."
                _authError.value = "Firebase Reset Email Error: $err. Please use Instant Password Reset option below."
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
        logAuditAction(
            projectId = null,
            actionType = "Update Profile",
            details = "Updated user profile & position: ${updated.name}",
            oldValue = "",
            newValue = "Position: $position, Office: $office",
            user = updated.name
        )
    }

    fun signOutUser() {
        val previousUser = currentUserAccount.value.name
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
        if (id == null) flowOf(null) else repository.getProjectById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    /** Total gross billed per project id, for the dashboard billing bars. */
    val billedTotalsByProject: StateFlow<Map<Long, Double>> = projectDao.getAllPayments()
        .map { payments -> payments.groupBy { it.projectId }.mapValues { (_, p) -> p.sumOf { it.grossAmount } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _portfolioFilter = MutableStateFlow<PortfolioFilter?>(null)

    /** Tapping the active count again clears it. */
    fun setPortfolioFilter(filter: PortfolioFilter?) {
        _portfolioFilter.value = if (_portfolioFilter.value == filter) null else filter
    }

    private val portfolioItems: Flow<List<PortfolioItem>> = combine(
        userScopedProjects, allPendingDocuments, billedTotalsByProject, reportDao.getLatestWeeklyReportTimes()
    ) { projects, docs, billed, latest ->
        val today = LocalDate.now()
        val docsByProject = docs.groupBy { it.projectId }
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
                flags = ProjectAttention.flags(project, projectDocs, billedTotal, lastUpdate, today),
                billedFraction = BillingProgress.billedFraction(billedTotal, cost),
                billedTotal = billedTotal,
                contractCost = cost,
                pendingDocsCount = ProjectAttention.waitingApproval(projectDocs),
                lastFieldUpdate = lastUpdate
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSdpPlan: StateFlow<SdpPlan?> = _selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.getActiveSdpPlanForProject(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
        remarks: String
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

    fun updateLotBillingStatus(
        projectId: Long,
        sdpPlanId: Long,
        sdpLotId: Long,
        isBilled: Boolean,
        billingReference: String = "",
        billingRemarks: String = ""
    ) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val currentDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
            val existing = activeSdpLotProgressMap.value[sdpLotId]
            
            val newStatus = if (isBilled) "BILLED" else "NOT BILLED"
            val progressRecord = SdpLotProgress(
                id = existing?.id ?: 0,
                projectId = projectId,
                sdpPlanId = sdpPlanId,
                sdpLotId = sdpLotId,
                physicalProgress = existing?.physicalProgress ?: 0,
                constructionStatus = existing?.constructionStatus ?: "Not Started",
                currentActivity = existing?.currentActivity ?: "",
                startDate = existing?.startDate ?: "",
                targetCompletionDate = existing?.targetCompletionDate ?: "",
                contractor = existing?.contractor ?: "",
                remarks = existing?.remarks ?: "",
                billingStatus = newStatus,
                billingDate = if (isBilled) currentDate else "",
                billedBy = if (isBilled) user else "",
                billingReference = if (isBilled) billingReference else "",
                billingRemarks = if (isBilled) billingRemarks else "",
                createdBy = existing?.createdBy?.ifBlank { user } ?: user,
                createdDate = existing?.createdDate?.ifBlank { currentDate } ?: currentDate,
                lastModifiedBy = user,
                lastModifiedDate = currentDate
            )
            
            repository.insertOrUpdateLotProgress(progressRecord, currentUserAccount.value.role)
            
            val oldVal = existing?.billingStatus ?: "NOT BILLED"
            logAuditAction(
                projectId = projectId,
                actionType = "Developer Billing Update",
                details = "Updated Billing Status for Lot ID $sdpLotId to $newStatus (Ref: $billingReference)",
                oldValue = oldVal,
                newValue = "Status: $newStatus, Billed By: $user, Date: $currentDate, Ref: $billingReference"
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

    fun addSdpPlan(
        projectId: Long,
        planName: String,
        pdfFileUrl: String,
        description: String = ""
    ) {
        viewModelScope.launch {
            val user = currentUserAccount.value.name
            val existingPlans = repository.getSdpPlansForProject(projectId).first()
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
                description = description
            )
            repository.insertSdpPlan(plan, currentUserAccount.value.role)
            logAuditAction(
                projectId = projectId,
                actionType = "Document Upload",
                details = "Uploaded Approved SDP Plan Version $nextVersion: $planName",
                oldValue = "",
                newValue = "Plan: $planName, Version: $nextVersion, File: $pdfFileUrl"
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
        description: String = ""
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
                newValue = "Block $blockNumber, Lot $lotNumber, Unit: $housingUnitNumber, Area: ${lotAreaSqM}sqm"
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
        assignedStaff: String = currentUserAccount.value.name
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
            )
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
                newValue = "Title: $name, Location: $location, Cost: ₱$contractCostOriginal, Duration: $contractDurationDays days"
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
                    RegisteredUser(uid = doc.id, name = name)
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
            val previousStatus = selectedProject.value?.status ?: "Unknown"
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
                newValue = "Status: ${updatedProject.status}, Revised Cost: ₱${updatedProject.contractCostRevised}"
            )
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
    private suspend fun saveProjectChange(updated: Project) {
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
                payloadJson = "{\"projectUuid\":\"${versioned.projectUuid}\"}"
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
                computedRevisedDate = start.plusDays(revisedDurationDays.toLong()).toString()
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

            // Notify Super Admin on ALL changes by Engineer Admin, Field Engineer, or User Registrations/Elevations!
            val isRegistrationOrElevation = actionType.contains("Registration", ignoreCase = true) || actionType.contains("Elevation", ignoreCase = true)
            val isNonSuperAdminAction = currentAccount.role != UserRole.SUPER_ADMIN && actionType != "User Login" && actionType != "User Logout"

            if (isRegistrationOrElevation || isNonSuperAdminAction) {
                var pName = ""
                if (projectId != null) {
                    try {
                        pName = projectDao.getProjectById(projectId).first()?.name ?: ""
                    } catch (e: Exception) {}
                }

                val notificationTitle = when {
                    actionType.contains("Registration", ignoreCase = true) -> "New User Registration Alert"
                    actionType.contains("Elevation", ignoreCase = true) -> "Role Elevation Alert"
                    actionType.contains("Creation", ignoreCase = true) || actionType.contains("Project", ignoreCase = true) -> "Project Activity by ${currentAccount.role.label}"
                    actionType.contains("Report", ignoreCase = true) -> "Report Submitted by ${currentAccount.role.label}"
                    actionType.contains("Payment", ignoreCase = true) -> "Disbursement Added by ${currentAccount.role.label}"
                    actionType.contains("Weather", ignoreCase = true) -> "Weather Entry by ${currentAccount.role.label}"
                    actionType.contains("Issue", ignoreCase = true) -> "Site Issue Logged by ${currentAccount.role.label}"
                    else -> "System Alert ($actionType) by ${currentAccount.role.label}"
                }

                val appNotification = AppNotification(
                    title = notificationTitle,
                    message = "$userName: $details",
                    actorName = userName,
                    actorRole = currentAccount.role.name,
                    targetRole = "SUPER_ADMIN",
                    projectId = projectId,
                    projectName = pName,
                    actionType = actionType,
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
                notificationDao.insertNotification(appNotification)

                // Send Native Android Status Bar Notification Alert
                com.example.util.AppNotificationManager.sendAdminNotification(
                    context = getApplication(),
                    title = notificationTitle,
                    message = "$userName: $details"
                )
            }
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
                    submittedByStaff = currentUserAccount.value.name
                )
                weeklyReportWeatherWriter.create(report, weatherDrafts)

                // Update Project Master target & actual accomplishment & latest update photo.
                val current = projectDao.getProjectById(projectId).first()
                if (current != null) {
                    val updated = current.copy(
                        targetAccomplishment = targetAccomplishmentPct,
                        actualAccomplishment = actualAccomplishmentPct,
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
                if (current.id == report.projectId) {
                    val updated = current.copy(
                        targetAccomplishment = report.targetAccomplishmentPct,
                        actualAccomplishment = report.actualAccomplishmentPct
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
                if (current != null && (current.targetAccomplishment != report.targetAccomplishmentPct ||
                        current.actualAccomplishment != report.actualAccomplishmentPct)) {
                    saveProjectChange(current.copy(
                        targetAccomplishment = report.targetAccomplishmentPct,
                        actualAccomplishment = report.actualAccomplishmentPct
                    ))
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

    /** Points the project's card photo at the newest report photo left, after an edit or delete. */
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


    fun addProjectPayment(projectId: Long, name: String, dvNo: String, date: String, periodCovered: String, grossAmount: Double, percentage: Double, balanceAmount: Double, balancePercentage: Double, fileUrl: String = "") {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.insertProjectPayment(
                ProjectPayment(
                    projectId = projectId,
                    name = name,
                    dvNo = dvNo,
                    date = date,
                    periodCovered = periodCovered,
                    grossAmount = grossAmount,
                    percentage = percentage,
                    balanceAmount = balanceAmount,
                    balancePercentage = balancePercentage,
                    fileUrl = fileUrl
                )
            )
            logAuditAction(
                projectId = projectId,
                actionType = "Payment Creation",
                details = "Created payment billing: $name ($dvNo)",
                oldValue = "",
                newValue = "Billing: $name, DV: $dvNo, Gross: ₱$grossAmount"
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
    
    fun deleteProjectPayment(id: Long) {
        viewModelScope.launch(missingProjectGuard) {
            projectDao.deleteProjectPayment(id)
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

    val driveSyncStatusMessage: StateFlow<String?> = googleDriveSyncManager.syncStatusMessage

    // Google Drive Sync Operations
    fun backupToGoogleDrive(accessToken: String? = null, onResult: (DriveSyncResult) -> Unit = {}) {
        viewModelScope.launch(missingProjectGuard) {
            if (currentUserAccount.value.isDemoAccount) {
                onResult(DriveSyncResult.Error("Google Drive Sync is disabled for Quick Demo Access. Please sign in with your official NHA account to upload to Google Drive."))
                return@launch
            }
            val result = googleDriveSyncManager.backupToGoogleDrive(accessToken)
            val success = result is DriveSyncResult.Success
            val msg = when (result) {
                is DriveSyncResult.Success -> result.message
                is DriveSyncResult.Error -> result.message
            }
            logAuditAction(
                projectId = null,
                actionType = "Backup",
                details = "Google Drive Database Backup: $msg",
                oldValue = "",
                newValue = if (success) "Backup Completed" else "Backup Failed: $msg"
            )
            logAuditAction(
                projectId = null,
                actionType = "Sync Event",
                details = "Google Drive Sync event triggered",
                oldValue = "",
                newValue = msg
            )
            onResult(result)
        }
    }

    fun restoreFromGoogleDrive(accessToken: String? = null, onResult: (DriveSyncResult) -> Unit = {}) {
        viewModelScope.launch(missingProjectGuard) {
            val result = googleDriveSyncManager.restoreFromGoogleDrive(accessToken)
            val success = result is DriveSyncResult.Success
            val msg = when (result) {
                is DriveSyncResult.Success -> result.message
                is DriveSyncResult.Error -> result.message
            }
            logAuditAction(
                projectId = null,
                actionType = "Restore",
                details = "Google Drive Database Restore: $msg",
                oldValue = "Previous local database state",
                newValue = if (success) "Restore Succeeded" else "Restore Failed: $msg"
            )
            logAuditAction(
                projectId = null,
                actionType = "Sync Event",
                details = "Google Drive Sync event triggered",
                oldValue = "",
                newValue = msg
            )
            onResult(result)
        }
    }

    fun toggleDriveAutoSync(enabled: Boolean) {
        googleDriveSyncManager.setAutoSync(enabled)
        logAuditAction(
            projectId = null,
            actionType = "Sync Event",
            details = "Google Drive Auto-Sync set to $enabled",
            oldValue = "AutoSync: ${!enabled}",
            newValue = "AutoSync: $enabled"
        )
    }

    // Sync from Cloud (replaces destructive importSampleData)
    fun importSampleData(onComplete: () -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                firestoreSyncManager.startRealtimeSync()
                firestoreSyncManager.flushOutbox()
                restoreFromGoogleDrive()
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
