package com.example.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSha256: String = "",
    val isMandatory: Boolean = false,
    val isUpdateAvailable: Boolean = false
)

object AppUpdateManager {

    private const val UPDATE_URL = "https://raw.githubusercontent.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/main/update.json"

    /** Reads update.json from main. Null when it could not be reached, so callers don't claim "up to date". */
    suspend fun checkForUpdates(customUpdateUrl: String? = null): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(customUpdateUrl ?: UPDATE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode != 200) return@withContext null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val serverVersionCode = json.optInt("versionCode", BuildConfig.VERSION_CODE)
            AppUpdateInfo(
                latestVersionCode = serverVersionCode,
                latestVersionName = json.optString("versionName", BuildConfig.VERSION_NAME),
                releaseNotes = json.optString("releaseNotes", ""),
                apkDownloadUrl = json.optString("apkDownloadUrl", ""),
                apkSha256 = json.optString("apkSha256", "").trim().lowercase(),
                isMandatory = json.optBoolean("isMandatory", false),
                isUpdateAvailable = serverVersionCode > BuildConfig.VERSION_CODE
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downloads the APK inside the app, checks its SHA-256 and hands it to Android's own installer,
     * which asks the user to confirm once (and, the first time, to allow installs from this app).
     * Throws with a readable message when anything fails.
     */
    suspend fun downloadAndInstall(context: Context, info: AppUpdateInfo, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (!info.apkDownloadUrl.startsWith("https://")) throw IOException("The update link is missing.")
        val apk = File(context.cacheDir, "update.apk")
        try {
            // GitHub's release link redirects to its file host; HttpURLConnection follows https-to-https redirects.
            val connection = URL(info.apkDownloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode != 200) throw IOException("Download failed (HTTP ${connection.responseCode}).")
            val total = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                apk.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.apkSha256.isNotBlank() && sha != info.apkSha256) {
                throw IOException("The download was damaged. Please try again.")
            }

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName(context.packageName) }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                // The installer fills in the result, so the PendingIntent must be mutable on Android 12+.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val result = PendingIntent.getBroadcast(context, sessionId, Intent(context, UpdateInstallReceiver::class.java), flags)
                session.commit(result.intentSender)
            }
        } finally {
            apk.delete()
        }
    }
}

/** Gets the installer's answer: shows Android's confirm screen, or says why the update didn't install. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Toast.makeText(context, "Update cancelled.", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(
                context,
                "Update not installed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "error $status"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
