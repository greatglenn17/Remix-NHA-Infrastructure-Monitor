package com.example.data.files

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.model.SyncOutboxEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Photos and scans attached to records.
 *
 * Attaching a file copies it into the app's own storage straight away (photos and image
 * scans shrunk, PDFs as they are), so it keeps working after Android withdraws access to
 * the picked original. The record stores a reference `nhafile:<id>.jpg|.pdf` instead of
 * the picked uri, which syncs with the record like any other text. The file itself is
 * queued in sync_outbox as a `file` event, uploaded to Cloud Storage at
 * `attachments/<id>.<ext>`, and downloaded by other phones the first time it is shown.
 */
object Attachments {
    const val SCHEME = "nhafile"
    const val ENTITY_TYPE = "file"
    const val MAX_BYTES = 20L * 1024 * 1024

    private const val TAG = "Attachments"
    private const val DIR = "attachments"
    private const val JPEG_QUALITY = 85
    /** Files must be at least this old before an unreferenced one is cleaned up, so a form still open keeps its picks. */
    private const val ORPHAN_AGE_MS = 24L * 60 * 60 * 1000

    private val validName = Regex("[0-9a-f-]{36}\\.(jpg|pdf)")
    private val refPattern = Regex("$SCHEME:[0-9a-f-]{36}\\.(?:jpg|pdf)")
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()

    /** Longest side in pixels: photos look the same on screen and in print; scans keep small text readable. */
    enum class Kind(val maxEdge: Int) { PHOTO(2048), SCAN(2500) }

    class UnsupportedFileException : Exception("Only photos and PDF files can be attached.")
    class FileTooLargeException : Exception("The file is larger than 20 MB.")

    fun isRef(value: String) = value.startsWith("$SCHEME:")

    /** The file name inside a reference, or null when it isn't one of ours (never a path from elsewhere). */
    private fun nameOf(ref: String): String? = ref.removePrefix("$SCHEME:").takeIf { isRef(ref) && validName.matches(it) }

    fun isPdf(ref: String) = ref.endsWith(".pdf")

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun fileForName(context: Context, name: String): File? = if (validName.matches(name)) File(dir(context), name) else null

    fun storagePath(name: String) = "$DIR/$name"

    fun contentTypeOf(name: String) = if (name.endsWith(".pdf")) "application/pdf" else "image/jpeg"

    /** Every reference in a text column, e.g. a weekly report's photo list. */
    fun refsIn(text: String): List<String> = refPattern.findAll(text).map { it.value }.toList()

    /**
     * The photo list stored on a weekly report. Older versions saved it in several shapes
     * (a JSON array, an array nested inside a string, a bare uri), so this reads all of them.
     */
    fun parseList(json: String): List<String> {
        val out = mutableListOf<String>()
        fun walk(value: String) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return
            if (trimmed.startsWith("[")) {
                val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return
                for (i in 0 until arr.length()) walk(arr.optString(i))
            } else {
                out.add(trimmed)
            }
        }
        walk(json)
        return out
    }

    fun toJson(list: List<String>): String = JSONArray(list).toString()

    /** Copies a picked file into the app, shrinking images, and queues it for upload. Returns its reference. */
    suspend fun import(context: Context, uri: Uri, kind: Kind): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES * 3) throw FileTooLargeException()
                buffer.write(chunk, 0, n)
            }
            buffer.toByteArray()
        } ?: throw UnsupportedFileException()
        store(context, bytes, kind)
    }

    private suspend fun store(context: Context, bytes: ByteArray, kind: Kind): String {
        val isPdf = bytes.size > 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "%PDF"
        val id = UUID.randomUUID().toString()
        val name: String
        if (isPdf) {
            if (bytes.size > MAX_BYTES) throw FileTooLargeException()
            name = "$id.pdf"
            File(dir(context), name).writeBytes(bytes)
        } else {
            val bitmap = decodeScaled(bytes, kind.maxEdge) ?: throw UnsupportedFileException()
            name = "$id.jpg"
            File(dir(context), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
        }
        AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).syncOutboxDao().enqueue(
            SyncOutboxEvent(entityType = ENTITY_TYPE, entityUuid = name, operation = "UPSERT", revision = 1, payloadJson = "{}")
        )
        return "$SCHEME:$name"
    }

    /** Decodes an image no larger than [maxEdge] on its longest side, turned the way the camera held it. */
    private fun decodeScaled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null

        val longest = maxOf(decoded.width, decoded.height)
        val scale = if (longest > maxEdge) maxEdge.toFloat() / longest else 1f
        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (scale == 1f && rotation == 0f) return decoded

        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    /** A cache file the camera app writes the full photo into, shared through the FileProvider. */
    fun newCameraFile(context: Context): Pair<File, Uri> {
        val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Keeps the full-quality camera photo in the phone's Gallery (Pictures/NHA Monitor), then
     * attaches the shrunk copy. Older Android versions need a storage permission for the
     * Gallery copy, so there only the attachment is kept.
     */
    suspend fun importCameraPhoto(context: Context, file: File): String = withContext(Dispatchers.IO) {
        try {
            val bytes = file.readBytes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "NHA_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NHA Monitor")
                    }
                    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { target ->
                        context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                    }
                }.onFailure { Log.w(TAG, "Could not save the original photo to the Gallery", it) }
            }
            store(context, bytes, Kind.PHOTO)
        } finally {
            file.delete()
        }
    }

    /** The local copy of an attachment, downloading it first when another phone added it. Null while it isn't available. */
    suspend fun ensureLocal(context: Context, ref: String): File? = withContext(Dispatchers.IO) {
        val name = nameOf(ref) ?: return@withContext null
        val file = File(dir(context), name)
        if (file.exists()) return@withContext file
        if (FirebaseAuth.getInstance().currentUser == null) return@withContext null
        downloadLocks.getOrPut(name) { Mutex() }.withLock {
            if (file.exists()) return@withLock file
            val part = File(dir(context), "$name.part")
            try {
                FirebaseStorage.getInstance().reference.child(storagePath(name)).getFile(part).await()
                if (part.renameTo(file)) file else null
            } catch (e: Exception) {
                // Usually not uploaded yet by the phone that added it; the next view tries again.
                Log.d(TAG, "Attachment $name not available: ${e.message}")
                part.delete()
                null
            }
        }
    }

    /** Opens an attachment (or an older plain uri) in a viewer app. Returns false when it can't be opened yet. */
    suspend fun open(context: Context, value: String): Boolean {
        val intent = if (isRef(value)) {
            val file = ensureLocal(context, value) ?: return false
            Intent(Intent.ACTION_VIEW).setDataAndType(
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
                contentTypeOf(file.name)
            )
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(value))
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Uploads or deletes the cloud copy for one `file` outbox event. */
    suspend fun upload(context: Context, event: SyncOutboxEvent) {
        val name = event.entityUuid.takeIf { validName.matches(it) } ?: return
        val cloud = FirebaseStorage.getInstance().reference.child(storagePath(name))
        if (event.operation == "DELETE") {
            try {
                cloud.delete().await()
            } catch (e: StorageException) {
                if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
            }
        } else {
            val file = File(dir(context), name)
            if (!file.exists()) return
            cloud.putFile(Uri.fromFile(file), storageMetadata { contentType = contentTypeOf(name) }).await()
        }
    }

    /** Columns that hold attachment references. */
    private val referencingColumns = listOf(
        "weekly_reports" to "attachedPhotoUrlsJson",
        "pending_documents" to "fileUrl",
        "project_payments" to "fileUrl",
        "projects" to "latestUpdatePhotoUrl"
    )

    /**
     * Deletes local files, and their cloud copies, that no record refers to any more,
     * e.g. after a report was deleted or a photo removed from it.
     */
    suspend fun collectGarbage(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val sql = db.openHelper.writableDatabase
        val referenced = mutableSetOf<String>()
        for ((table, column) in referencingColumns) {
            sql.query("SELECT `$column` FROM `$table` WHERE `$column` LIKE '%$SCHEME:%'").use { c ->
                while (c.moveToNext()) refsIn(c.getString(0).orEmpty()).forEach { referenced.add(it.removePrefix("$SCHEME:")) }
            }
        }
        val cutoff = System.currentTimeMillis() - ORPHAN_AGE_MS
        dir(context).listFiles().orEmpty().forEach { file ->
            if (file.lastModified() > cutoff) return@forEach
            if (file.name.endsWith(".part")) { file.delete(); return@forEach }
            if (!validName.matches(file.name) || file.name in referenced) return@forEach
            file.delete()
            db.syncOutboxDao().enqueue(
                SyncOutboxEvent(entityType = ENTITY_TYPE, entityUuid = file.name, operation = "DELETE", revision = 1, payloadJson = "{}")
            )
        }
    }

    /**
     * One-time move of attachments saved by older versions as picked uris, which stop
     * working after a restart, into app storage. Sample stock pictures the app used to
     * attach are removed. A uri that can no longer be read is left as it was.
     */
    suspend fun migrateLegacy(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("attachments", Context.MODE_PRIVATE)
        if (prefs.getBoolean("legacy_migrated", false)) return@withContext
        val sql = db.openHelper.writableDatabase

        suspend fun convert(value: String, kind: Kind): String? = when {
            isRef(value) -> value
            value.contains("images.unsplash.com") -> null
            value.startsWith("content:") || value.startsWith("file:") ->
                runCatching { import(context, Uri.parse(value), kind) }.getOrDefault(value)
            else -> value
        }

        for ((table, column) in referencingColumns) {
            val rows = sql.query("SELECT id, `$column` FROM `$table` WHERE `$column` != '' AND `$column` != '[]'").use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1).orEmpty()) }
            }
            for ((id, value) in rows) {
                val updated = if (table == "weekly_reports") {
                    toJson(parseList(value).mapNotNull { convert(it, Kind.PHOTO) })
                } else {
                    // A project's tile photo was sometimes saved as the whole photo list.
                    val single = if (table == "projects") parseList(value).firstOrNull().orEmpty() else value
                    convert(single, if (table == "projects") Kind.PHOTO else Kind.SCAN).orEmpty()
                }
                if (updated != value) sql.execSQL("UPDATE `$table` SET `$column` = ? WHERE id = ?", arrayOf(updated, id))
            }
        }
        prefs.edit().putBoolean("legacy_migrated", true).apply()
    }
}
