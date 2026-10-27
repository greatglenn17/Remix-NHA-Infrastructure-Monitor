package com.example.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {

    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nha_user_session_encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("nha_user_session_prefs", Context.MODE_PRIVATE)
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseAuth.getInstance()
        } catch (_: Exception) {
            null
        }
    }

    val currentFirebaseUser: FirebaseUser?
        get() = auth?.currentUser

    /** True when the session was restored from the local fallback and has no Firebase user, so nothing syncs. */
    val isOfflineOnlySession: Boolean
        get() = auth != null && auth?.currentUser == null && prefs.getBoolean("is_session_active", false)

    val isUserSignedIn: Boolean
        get() = auth?.currentUser != null || prefs.getBoolean("is_session_active", false)

    private fun hashPassword(password: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.trim().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun saveRegisteredUser(
        email: String,
        pass: String,
        displayName: String?,
        role: UserRole,
        position: String?,
        office: String?
    ) {
        val cleanEmail = email.trim().lowercase()
        val hashedPass = hashPassword(pass)
        val registeredEmails = prefs.getStringSet("registered_emails_set", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        registeredEmails.add(cleanEmail)

        prefs.edit()
            .putString("reg_pass_$cleanEmail", hashedPass)
            .putString("reg_name_$cleanEmail", displayName?.trim() ?: cleanEmail.substringBefore("@").capitalizeWords())
            .putString("reg_role_$cleanEmail", role.name)
            .putString("reg_pos_$cleanEmail", position?.trim() ?: "")
            .putString("reg_office_$cleanEmail", office?.trim() ?: "Bulacan District Office")
            .putStringSet("registered_emails_set", registeredEmails)
            .apply()
    }

    fun getAllRegisteredUsersJson(): org.json.JSONArray {
        val registeredEmails = prefs.getStringSet("registered_emails_set", emptySet()) ?: emptySet()
        val array = org.json.JSONArray()
        registeredEmails.forEach { email ->
            val obj = org.json.JSONObject().apply {
                put("email", email)
                put("pass", prefs.getString("reg_pass_$email", ""))
                put("name", prefs.getString("reg_name_$email", ""))
                put("role", prefs.getString("reg_role_$email", UserRole.FIELD_ENGINEER.name))
                put("position", prefs.getString("reg_pos_$email", ""))
                put("office", prefs.getString("reg_office_$email", ""))
            }
            array.put(obj)
        }
        return array
    }

    fun restoreRegisteredUsersFromJson(array: org.json.JSONArray) {
        val registeredEmails = prefs.getStringSet("registered_emails_set", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val editor = prefs.edit()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val email = obj.optString("email", "").trim().lowercase()
            if (email.isNotBlank()) {
                registeredEmails.add(email)
                editor.putString("reg_pass_$email", obj.optString("pass", ""))
                editor.putString("reg_name_$email", obj.optString("name", email.substringBefore("@")))
                editor.putString("reg_role_$email", obj.optString("role", UserRole.FIELD_ENGINEER.name))
                editor.putString("reg_pos_$email", obj.optString("position", ""))
                editor.putString("reg_office_$email", obj.optString("office", "Bulacan District Office"))
            }
        }
        editor.putStringSet("registered_emails_set", registeredEmails).apply()
    }

    fun isLocalRegisteredUser(email: String, pass: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val inputHash = hashPassword(pass)

        val savedPass = prefs.getString("reg_pass_$cleanEmail", null)
        return savedPass != null && (savedPass == inputHash || savedPass == pass.trim())
    }

    fun getRegisteredUserAccount(
        email: String,
        fallbackRole: UserRole,
        displayName: String?,
        position: String?,
        office: String?
    ): UserAccount {
        val cleanEmail = email.trim().lowercase()
        val regName = prefs.getString("reg_name_$cleanEmail", null) ?: displayName
        val regRoleStr = prefs.getString("reg_role_$cleanEmail", null) ?: fallbackRole.name
        val regRole = try { UserRole.valueOf(regRoleStr) } catch (_: Exception) { fallbackRole }
        val regPos = prefs.getString("reg_pos_$cleanEmail", null) ?: position
        val regOffice = prefs.getString("reg_office_$cleanEmail", null) ?: office

        return mapToUserAccount(
            email = cleanEmail,
            displayName = regName,
            selectedRole = regRole,
            position = regPos,
            office = regOffice
        )
    }

    suspend fun signIn(email: String, pass: String): Result<FirebaseUser?> {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()

        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Invalid email format. Please enter a valid email address (e.g. officer@nha.gov.ph)."))
        }

        if (cleanPass.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters long."))
        }

        val savedPass = prefs.getString("reg_pass_$cleanEmail", null)
        val inputHash = hashPassword(cleanPass)
        val localPasswordMatches = savedPass != null && (savedPass == inputHash || savedPass == cleanPass)

        // 1. Firebase Auth is the source of truth. Without a Firebase user nothing syncs,
        //    so the local fallback below is only for when Firebase is unreachable.
        val firebaseAuth = auth
        if (firebaseAuth != null) {
            try {
                val result = firebaseAuth.signInWithEmailAndPassword(cleanEmail, cleanPass).await()
                return Result.success(result.user)
            } catch (e: FirebaseNetworkException) {
                android.util.Log.w("AuthManager", "Firebase unreachable, using offline sign-in: ${e.message}")
            } catch (e: Exception) {
                android.util.Log.w("AuthManager", "Firebase remote sign-in exception: ${e.message}")
                if (!localPasswordMatches) {
                    return Result.failure(IllegalArgumentException("Incorrect email or password for $cleanEmail."))
                }
                // The account exists only on this phone (registered before the move to the
                // current Firebase project). The user proved the password locally, so create
                // the matching Firebase account and they sync from now on.
                return migrateLocalAccountToFirebase(firebaseAuth, cleanEmail, cleanPass)
            }
        }

        // 2. Offline fallback: Firebase is unavailable, verify against the local registration.
        if (savedPass != null) {
            if (localPasswordMatches) {
                return Result.success(auth?.currentUser)
            } else {
                return Result.failure(IllegalArgumentException("Incorrect password for $cleanEmail. Please re-type your password."))
            }
        }

        // 3. Account not found
        return Result.failure(IllegalArgumentException("No account found for $cleanEmail. Please click 'Create Account' tab to register first."))
    }

    private suspend fun migrateLocalAccountToFirebase(
        firebaseAuth: FirebaseAuth,
        email: String,
        pass: String
    ): Result<FirebaseUser?> {
        return try {
            val created = firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
            android.util.Log.i("AuthManager", "Migrated local account $email to Firebase Auth")
            Result.success(created.user)
        } catch (_: FirebaseAuthUserCollisionException) {
            // A cloud account already exists with a different password.
            Result.failure(IllegalArgumentException("Incorrect password for $email. Your cloud account uses a different password; tap 'Forgot Password' to reset it."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("No internet connection. Please connect and sign in again.", e))
        } catch (e: Exception) {
            android.util.Log.w("AuthManager", "Firebase account migration failed: ${e.message}", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Unable to create your cloud account.", e))
        }
    }

    suspend fun signUp(
        email: String,
        pass: String,
        displayName: String?,
        role: UserRole,
        position: String?,
        office: String?
    ): Result<FirebaseUser?> {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = pass.trim()

        // Store the local profile only. Firebase Auth remains the source of truth
        // for credentials; local storage is not synchronized to other devices.
        saveRegisteredUser(cleanEmail, cleanPass, displayName, role, position, office)

        // 2. Sync registration to Firebase remote Auth if enabled
        val firebaseAuth = auth
        if (firebaseAuth != null) {
            try {
                val result = firebaseAuth.createUserWithEmailAndPassword(cleanEmail, cleanPass).await()
                return Result.success(result.user)
            } catch (e: Exception) {
                android.util.Log.w("AuthManager", "Firebase remote sign-up exception: ${e.message}", e)
                try {
                    val signInResult = firebaseAuth.signInWithEmailAndPassword(cleanEmail, cleanPass).await()
                    return Result.success(signInResult.user)
                } catch (_: Exception) {
                    val msg = e.localizedMessage ?: e.message ?: "Account creation failed"
                    return Result.failure(IllegalStateException(msg, e))
                }
            }
        }
        return Result.success(null)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        val firebaseAuth = auth
        if (firebaseAuth != null) {
            try {
                firebaseAuth.sendPasswordResetEmail(cleanEmail).await()
                return Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.w("AuthManager", "Firebase password reset failed: ${e.message}")
                return Result.failure(e)
            }
        }
        return Result.failure(IllegalStateException("Firebase Auth service unavailable or unconfigured."))
    }

    fun resetUserPassword(email: String, newPass: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = newPass.trim()

        // Security: Only allow password reset for already-registered emails
        val registeredEmails = prefs.getStringSet("registered_emails_set", emptySet()) ?: emptySet()
        if (!registeredEmails.contains(cleanEmail)) {
            return false // Email not registered — block auto-creation of accounts
        }

        val savedName = prefs.getString("reg_name_$cleanEmail", null)
        val savedRoleStr = prefs.getString("reg_role_$cleanEmail", UserRole.FIELD_ENGINEER.name)
        val savedRole = try { UserRole.valueOf(savedRoleStr ?: UserRole.FIELD_ENGINEER.name) } catch (_: Exception) { UserRole.FIELD_ENGINEER }
        val savedPos = prefs.getString("reg_pos_$cleanEmail", null)
        val savedOffice = prefs.getString("reg_office_$cleanEmail", null)

        saveRegisteredUser(cleanEmail, cleanPass, savedName, savedRole, savedPos, savedOffice)
        return true
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (_: Exception) {}
        clearUserSession()
    }

    /**
     * Persist user account session to SharedPreferences so user role and profile persist across restarts.
     */
    fun saveRegisteredRole(email: String, role: UserRole) {
        prefs.edit().putString("reg_role_${email.trim().lowercase()}", role.name).apply()
    }

    fun saveUserSession(userAccount: UserAccount, email: String) {
        prefs.edit()
            .putBoolean("is_session_active", true)
            .putString("user_id", userAccount.id)
            .putString("user_name", userAccount.name)
            .putString("user_title", userAccount.title)
            .putString("user_office", userAccount.office)
            .putString("user_role", userAccount.role.name)
            .putLong("assigned_project_id", userAccount.assignedProjectId ?: -1L)
            .putString("user_email", email)
            .apply()
    }

    /**
     * Restore saved user account session upon application startup.
     */
    fun getSavedUserSession(): UserAccount? {
        if (!prefs.getBoolean("is_session_active", false)) return null
        val id = prefs.getString("user_id", null) ?: return null
        val name = prefs.getString("user_name", "Engr. Glenn C. Aprovechado") ?: "Engr. Glenn C. Aprovechado"
        val title = prefs.getString("user_title", "Principal Engineer C") ?: "Principal Engineer C"
        val office = prefs.getString("user_office", "Bulacan District Office") ?: "Bulacan District Office"
        val roleStr = prefs.getString("user_role", UserRole.FIELD_ENGINEER.name) ?: UserRole.FIELD_ENGINEER.name
        val role = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.FIELD_ENGINEER }
        val projId = prefs.getLong("assigned_project_id", -1L).let { if (it == -1L) null else it }

        return UserAccount(
            id = id,
            name = name,
            title = title,
            office = office,
            role = role,
            assignedProjectId = projId
        )
    }

    /**
     * Clear all session data on logout while preserving theme preferences.
     */
    fun clearUserSession() {
        prefs.edit()
            .remove("is_session_active")
            .remove("user_id")
            .remove("user_name")
            .remove("user_title")
            .remove("user_office")
            .remove("user_role")
            .remove("assigned_project_id")
            .remove("user_email")
            .apply()
    }

    /**
     * Persist selected theme mode (System, Light, Dark) to SharedPreferences.
     */
    fun saveAppThemeMode(mode: com.example.data.model.AppThemeMode) {
        prefs.edit().putString("app_theme_mode", mode.name).apply()
    }

    /**
     * Get persisted theme mode or default to System (forms are not light-safe until v1.9.0).
     */
    fun getSavedAppThemeMode(): com.example.data.model.AppThemeMode {
        val savedName = prefs.getString("app_theme_mode", com.example.data.model.AppThemeMode.SYSTEM.name)
        return try {
            com.example.data.model.AppThemeMode.valueOf(savedName ?: com.example.data.model.AppThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            com.example.data.model.AppThemeMode.SYSTEM
        }
    }

    /**
     * Map Firebase User or manual input to a UserAccount model.
     */
    fun mapToUserAccount(
        email: String,
        displayName: String?,
        selectedRole: UserRole,
        position: String? = null,
        office: String? = null,
        assignedProjectId: Long? = null
    ): UserAccount {
        val name = when {
            !displayName.isNullOrBlank() -> displayName
            email.contains("@") -> email.substringBefore("@").replace(".", " ").capitalizeWords()
            else -> "NHA Officer"
        }
        val defaultTitle = when (selectedRole) {
            UserRole.SUPER_ADMIN -> "Principal Engineer C"
            UserRole.ENGINEER_ADMIN -> "Supervising Engineer"
            UserRole.FIELD_ENGINEER -> "Senior Engineer A"
            UserRole.VIEWER -> "Contractor"
        }
        val userTitle = if (!position.isNullOrBlank()) position else defaultTitle
        val userOffice = if (!office.isNullOrBlank()) office else "Bulacan District Office"

        val account = UserAccount(
            id = auth?.currentUser?.uid ?: "user_${System.currentTimeMillis()}",
            name = name,
            title = userTitle,
            office = userOffice,
            role = selectedRole,
            assignedProjectId = assignedProjectId
        )
        saveUserSession(account, email)
        return account
    }

    /**
     * Update user profile, title/position, and office in session.
     */
    fun updateUserProfile(name: String, position: String, office: String): UserAccount {
        val currentSession = getSavedUserSession()
        val email = prefs.getString("user_email", "officer@nha.gov.ph") ?: "officer@nha.gov.ph"
        val updated = (currentSession ?: UserAccount(
            id = "user_${System.currentTimeMillis()}",
            name = name,
            title = position,
            office = office,
            role = UserRole.ENGINEER_ADMIN
        )).copy(
            name = name,
            title = position,
            office = office
        )
        saveUserSession(updated, email)
        return updated
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }
}
