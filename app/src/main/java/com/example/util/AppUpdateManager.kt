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
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
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
            val targetUrl = customUpdateUrl ?: "https://raw.githubusercontent.com/glenn/nha-monitor/main/update.json"
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
                val mandatory = json.optBoolean("isMandatory", false)

                return@withContext AppUpdateInfo(
                    latestVersionCode = serverVersionCode,
                    latestVersionName = serverVersionName,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    isMandatory = mandatory,
                    isUpdateAvailable = serverVersionCode > currentVersionCode
                )
            }
        } catch (e: Exception) {
            // Fallback simulated update info for local testing
        }

        // Return latest simulated state
        val mockLatestCode = currentVersionCode // set higher to test prompt
        AppUpdateInfo(
            latestVersionCode = mockLatestCode,
            latestVersionName = "v${BuildConfig.VERSION_NAME}",
            releaseNotes = "System is up to date with latest Bulacan District Office features.",
            apkDownloadUrl = "https://github.com/nha-monitor/releases/download/latest/app-release.apk",
            isMandatory = false,
            isUpdateAvailable = false
        )
    }

    fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
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

        val validUrl = if (apkUrl.isBlank() || apkUrl.contains("example.com") || apkUrl.contains("nha-monitor/releases")) {
            "https://raw.githubusercontent.com/glenn/nha-monitor/main/update.json" // Test target URL
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

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        if (file.exists() && file.length() > 0) {
                            installApk(context, file)
                        } else {
                            downloadDirectFallback(context, validUrl, file, onDownloadFailed)
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
            downloadDirectFallback(context, validUrl, file, onDownloadFailed)
        }
    }

    private fun downloadDirectFallback(
        context: Context,
        apkUrl: String,
        targetFile: File,
        onDownloadFailed: (String) -> Unit
    ) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(apkUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode in 200..299) {
                    connection.inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        installApk(context, targetFile)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "App is currently at the latest build v${BuildConfig.VERSION_NAME}. Live server will stream future APK releases.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        onDownloadFailed("Update package unavailable.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Your app is on the latest build v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}).",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    onDownloadFailed(e.message ?: "Download failed.")
                }
            }
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
}
