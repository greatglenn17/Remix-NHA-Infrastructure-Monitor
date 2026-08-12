package com.example.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
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

    suspend fun checkForUpdates(
        customUpdateUrl: String? = null
    ): AppUpdateInfo = withContext(Dispatchers.IO) {
        val currentVersionCode = BuildConfig.VERSION_CODE
        try {
            // Default mock/live update config check for demonstration & live sync
            val targetUrl = customUpdateUrl ?: "https://raw.githubusercontent.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/main/update.json"
            val connection = URL(targetUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val serverVersionCode = json.optInt("versionCode", currentVersionCode)
                val serverVersionName = json.optString("versionName", "v${BuildConfig.VERSION_NAME}")
                val releaseNotes = json.optString("releaseNotes", "New feature updates and stability improvements.")
                val apkUrl = json.optString("apkDownloadUrl", "")
                val apkSha256 = json.optString("apkSha256", "").trim().lowercase()
                val mandatory = json.optBoolean("isMandatory", false)

                return@withContext AppUpdateInfo(
                    latestVersionCode = serverVersionCode,
                    latestVersionName = serverVersionName,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    apkSha256 = apkSha256,
                    isMandatory = mandatory,
                    isUpdateAvailable = serverVersionCode > currentVersionCode
                )
            }
        } catch (e: Exception) {
            // Fallback simulated update info for local testing
        }

        // Return latest state
        val mockLatestCode = currentVersionCode
        AppUpdateInfo(
            latestVersionCode = mockLatestCode,
            latestVersionName = "v${BuildConfig.VERSION_NAME}",
            releaseNotes = "System is up to date with latest Bulacan District Office features.",
            apkDownloadUrl = "https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/releases",
            apkSha256 = "",
            isMandatory = false,
            isUpdateAvailable = false
        )
    }

    fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        expectedSha256: String = "",
        onDownloadStarted: () -> Unit = {},
        onDownloadFailed: (String) -> Unit = {}
    ) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "nha_monitor_update.apk")
        if (file.exists()) {
            file.delete()
        }

        android.widget.Toast.makeText(
            context,
            "Starting live update download...",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        onDownloadStarted()

        val validUrl = if (apkUrl.isBlank() || apkUrl.contains("example.com") || apkUrl.contains("update.json")) {
            "https://raw.githubusercontent.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/main/NHA_Monitor_v1.1.0_Build110.apk"
        } else {
            apkUrl
        }

        try {
            val request = DownloadManager.Request(Uri.parse(validUrl)).apply {
                setTitle("Downloading NHA Monitor Update")
                setDescription("Fetching latest release package...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(file))
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: run {
                    onDownloadFailed("Android Download Manager is unavailable.")
                    return
                }
            val downloadId = downloadManager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        if (!file.exists() || file.length() <= 500000) {
                            onDownloadFailed("The downloaded update is incomplete.")
                            openBrowserDownloadFallback(context)
                        } else if (expectedSha256.isNotBlank() && !file.sha256().equals(expectedSha256, ignoreCase = true)) {
                            onDownloadFailed("Update integrity verification failed.")
                            file.delete()
                        } else {
                            installApk(context, file)
                        }
                    }
                }
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(onComplete, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(onComplete, filter)
            }
        } catch (e: Exception) {
            onDownloadFailed(e.localizedMessage ?: "Unable to start update download.")
            openBrowserDownloadFallback(context)
        }
    }

    private fun openBrowserDownloadFallback(context: Context) {
        try {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/greatglenn17/Remix-NHA-Infrastructure-Monitor/releases")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Please visit GitHub releases to download the update.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
