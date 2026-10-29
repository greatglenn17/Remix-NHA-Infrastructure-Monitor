package com.example.data.files

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

/** What a site photo shows, when it was taken and where. */
data class PhotoDetail(
    val title: String = "",
    val takenAt: String = "",
    val lat: Double? = null,
    val lng: Double? = null
) {
    val location: String get() = if (lat != null && lng != null) String.format(java.util.Locale.US, "%.6f, %.6f", lat, lng) else ""

    /** Lines printed on a geo-tagged photo. */
    val stamp: List<String> get() = listOfNotNull(takenAt.ifBlank { null }, location.ifBlank { null }?.let { "GPS $it" })
}

/** Weekly report photo details, kept as one JSON object keyed by the photo's attachment ref. */
object PhotoDetails {
    private val TAKEN_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    private val EXIF_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun parse(json: String): Map<String, PhotoDetail> = try {
        val obj = JSONObject(json.ifBlank { "{}" })
        obj.keys().asSequence().associateWith { ref ->
            val d = obj.getJSONObject(ref)
            PhotoDetail(
                title = d.optString("title"),
                takenAt = d.optString("takenAt"),
                lat = if (d.has("lat")) d.getDouble("lat") else null,
                lng = if (d.has("lng")) d.getDouble("lng") else null
            )
        }
    } catch (_: Exception) {
        emptyMap()
    }

    fun toJson(details: Map<String, PhotoDetail>): String = JSONObject().apply {
        details.forEach { (ref, d) ->
            put(ref, JSONObject().apply {
                put("title", d.title)
                put("takenAt", d.takenAt)
                d.lat?.let { put("lat", it) }
                d.lng?.let { put("lng", it) }
            })
        }
    }.toString()

    fun now(): String = LocalDateTime.now().format(TAKEN_FORMAT)

    /** Time taken and location from a picked photo's EXIF; blank when the photo has none. */
    fun fromExif(context: Context, uri: Uri): PhotoDetail = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val taken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val latLng = FloatArray(2).takeIf { exif.getLatLong(it) }
            PhotoDetail(
                takenAt = taken?.let { runCatching { LocalDateTime.parse(it, EXIF_FORMAT).format(TAKEN_FORMAT) }.getOrNull() }.orEmpty(),
                lat = latLng?.get(0)?.toDouble(),
                lng = latLng?.get(1)?.toDouble()
            )
        } ?: PhotoDetail()
    } catch (_: Exception) {
        PhotoDetail()
    }

    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** A fresh fix from GPS or the network within 15 seconds, else the last known one; null without permission or a fix. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }
        for (provider in providers) {
            val fix = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                }
            }
            if (fix != null) return fix
        }
        return providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }
}
