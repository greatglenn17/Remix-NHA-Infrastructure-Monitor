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
 * `attachments/<projectUuid>/<id>.<ext>`, and downloaded by other phones the first time it is shown.
 * The reference carries the project: `nhafile:<projectUuid>/<id>.<ext>`. References made by
 * 1.4.x have no project (`nhafile:<id>.<ext>`) and keep the Storage path `attachments/<id>.<ext>`.
 * Locally the file is always `attachments/<id>.<ext>`.
 */
object Attachments {
    const val SCHEME = "nhafile"
    const val ENTITY_TYPE = "file"
    /** Files must be smaller than this: the same limit as storage.rules (`size < 20 * 1024 * 1024`). */
    const val MAX_BYTES = 20L * 1024 * 1024

    private const val TAG = "Attachments"
    private const val MOVED_FROM = "movedFrom"
    private const val DIR = "attachments"
    private const val JPEG_QUALITY = 85
    /** Files must be at least this old before an unreferenced one is cleaned up, so a form still open keeps its picks. */
    private const val ORPHAN_AGE_MS = 24L * 60 * 60 * 1000

    private val validName = Regex("[0-9a-f-]{36}\\.(jpg|pdf)")
    private const val UUID_PATTERN = "[0-9a-f-]{36}"
    private val refPattern = Regex("$SCHEME:(?:$UUID_PATTERN/)?$UUID_PATTERN\\.(?:jpg|pdf)")
    private val projectUuidPattern = Regex(UUID_PATTERN)
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()

    /** Longest side in pixels: photos look the same on screen and in print; scans keep small text readable. */
    enum class Kind(val maxEdge: Int) { PHOTO(2048), SCAN(2500) }

    class UnsupportedFileException : Exception("Only photos and PDF files can be attached.")
    class FileTooLargeException : Exception("The file must be smaller than 20 MB.")

    fun isRef(value: String) = value.startsWith("$SCHEME:")

    /** The file name inside a reference, or null when it isn't one of ours (never a path from elsewhere). */
    private fun nameOf(ref: String): String? = split(ref)?.second

    /** The project uuid (null for a legacy reference) and file name of a valid reference, or null. */
    private fun split(ref: String): Pair<String?, String>? {
        if (!isRef(ref)) return null
        val parts = ref.removePrefix("$SCHEME:").split('/')
        return when (parts.size) {
            1 -> if (validName.matches(parts[0])) null to parts[0] else null
            2 -> if (projectUuidPattern.matches(parts[0]) && validName.matches(parts[1])) parts[0] to parts[1] else null
            else -> null
        }
    }

    /** The project a reference belongs to; null for a legacy reference or one that isn't ours. */
    fun projectOf(ref: String): String? = split(ref)?.first

    /** Where the file lives in Cloud Storage, or null when [ref] isn't one of ours. */
    fun storagePathOf(ref: String): String? = split(ref)?.let { (project, name) -> storagePath(project, name) }

    private fun storagePath(project: String?, name: String) = if (project == null) "$DIR/$name" else "$DIR/$project/$name"

    /** The outbox `entityUuid` of a file: `<projectUuid>/<fileName>`, or just the file name when legacy. */
    private fun entityOf(project: String?, name: String) = if (project == null) name else "$project/$name"

    fun isPdf(ref: String) = ref.endsWith(".pdf")

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    fun fileForName(context: Context, name: String): File? = if (validName.matches(name)) File(dir(context), name) else null

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
    suspend fun import(context: Context, uri: Uri, kind: Kind, projectUuid: String, stamp: List<String> = emptyList()): String =
        copyIn(context, uri, kind, requireProject(projectUuid), stamp)

    private fun requireProject(projectUuid: String): String {
        if (projectUuid.isBlank()) throw IllegalStateException("No project selected")
        if (!projectUuidPattern.matches(projectUuid)) throw IllegalStateException("Invalid project")
        return projectUuid
    }

    /** A null [projectUuid] makes a legacy reference; only the one-time migration of old uris does that. */
    private suspend fun copyIn(context: Context, uri: Uri, kind: Kind, projectUuid: String?, stamp: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
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
        store(context, bytes, kind, projectUuid, stamp)
    }

    /** [stamp] lines are printed on the bottom of a photo, e.g. its block and lot; PDFs are kept as they are. */
    private suspend fun store(context: Context, bytes: ByteArray, kind: Kind, projectUuid: String?, stamp: List<String> = emptyList()): String {
        val isPdf = bytes.size > 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "%PDF"
        val id = UUID.randomUUID().toString()
        val name: String
        if (isPdf) {
            if (bytes.size >= MAX_BYTES) throw FileTooLargeException()
            name = "$id.pdf"
            File(dir(context), name).writeBytes(bytes)
        } else {
            val bitmap = decodeScaled(bytes, kind.maxEdge)?.let { if (stamp.isEmpty()) it else PhotoStamp.draw(it, stamp) } ?: throw UnsupportedFileException()
            name = "$id.jpg"
            File(dir(context), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
        }
        AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO)).syncOutboxDao().enqueue(
            SyncOutboxEvent(entityType = ENTITY_TYPE, entityUuid = entityOf(projectUuid, name), operation = "UPSERT", revision = 1, payloadJson = "{}")
        )
        return if (projectUuid == null) "$SCHEME:$name" else "$SCHEME:$projectUuid/$name"
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
    suspend fun importCameraPhoto(context: Context, file: File, projectUuid: String, kind: Kind = Kind.PHOTO, stamp: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        requireProject(projectUuid)
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
            store(context, bytes, kind, projectUuid, stamp)
        } finally {
            file.delete()
        }
    }

    /** The local copy of an attachment, downloading it first when another phone added it. Null while it isn't available. */
    suspend fun ensureLocal(context: Context, ref: String): File? = withContext(Dispatchers.IO) {
        val (project, name) = split(ref) ?: return@withContext null
        val file = File(dir(context), name)
        if (file.exists()) return@withContext file
        if (FirebaseAuth.getInstance().currentUser == null) return@withContext null
        downloadLocks.getOrPut(name) { Mutex() }.withLock {
            if (file.exists()) return@withLock file
            val part = File(dir(context), "$name.part")
            try {
                FirebaseStorage.getInstance().reference.child(storagePath(project, name)).getFile(part).await()
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
        val (project, name) = split("$SCHEME:${event.entityUuid}") ?: return
        val cloud = FirebaseStorage.getInstance().reference.child(storagePath(project, name))
        if (event.operation == "DELETE") {
            try {
                cloud.delete().await()
            } catch (e: StorageException) {
                // Gone already, or not this account's to delete (another project, or the legacy
                // path only the Super Admin may touch): a retry could never succeed.
                if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND && e.errorCode != StorageException.ERROR_NOT_AUTHORIZED) throw e
            }
        } else {
            val file = File(dir(context), name)
            if (!file.exists() && !fetchMovedCopy(event, name, file)) return
            cloud.putFile(Uri.fromFile(file), storageMetadata { contentType = contentTypeOf(name) }).await()
        }
    }

    /**
     * For a file moved to another project ([moveProjectRefs]) that this phone has no copy of,
     * downloads the copy at the old project's path into [file]. False when there is none to get
     * (never uploaded, or not readable by this account), so the event is dropped, not retried.
     */
    private suspend fun fetchMovedCopy(event: SyncOutboxEvent, name: String, file: File): Boolean {
        val oldProject = runCatching { org.json.JSONObject(event.payloadJson).optString(MOVED_FROM) }.getOrNull()
        if (oldProject.isNullOrEmpty() || !projectUuidPattern.matches(oldProject)) return false
        val part = File(file.parentFile, "$name.part")
        return try {
            FirebaseStorage.getInstance().reference.child(storagePath(oldProject, name)).getFile(part).await()
            part.renameTo(file)
        } catch (e: StorageException) {
            part.delete()
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND && e.errorCode != StorageException.ERROR_NOT_AUTHORIZED) throw e
            false
        }
    }

    /** Columns that hold attachment references. */
    val referencingColumns = listOf(
        "weekly_reports" to "attachedPhotoUrlsJson",
        "pending_documents" to "fileUrl",
        "project_payments" to "fileUrl",
        "projects" to "latestUpdatePhotoUrl",
        "sdp_plans" to "pdfFileUrl"
    )

    /**
     * Points every attachment reference of project [oldUuid] at [newUuid], after the local
     * project took over the cloud's uuid or was merged into another row. Runs inside the
     * caller's transaction, with the sync triggers on: every changed record is queued, so
     * the cloud copies (and through them other phones) get the new references. The project's
     * own photo column has no trigger and is only rewritten here.
     *
     * The files follow: uploads not done yet are re-keyed to the new project, and every other
     * moved file is queued once more, so a copy exists at the new Storage path. That includes
     * files another phone uploaded; [upload] fetches the old copy first when this phone has none
     * (the old copy is left where it is).
     */
    fun moveProjectRefs(sql: androidx.sqlite.db.SupportSQLiteDatabase, oldUuid: String, newUuid: String) {
        if (oldUuid == newUuid || !projectUuidPattern.matches(oldUuid) || !projectUuidPattern.matches(newUuid)) return
        val from = "$SCHEME:$oldUuid/"
        val to = "$SCHEME:$newUuid/"
        val movedNames = linkedSetOf<String>()
        for ((table, column) in referencingColumns) {
            sql.query("SELECT `$column` FROM `$table` WHERE instr(`$column`, ?) > 0", arrayOf<Any>(from)).use { c ->
                while (c.moveToNext()) refsIn(c.getString(0).orEmpty()).forEach { ref ->
                    split(ref)?.let { (project, name) -> if (project == oldUuid) movedNames.add(name) }
                }
            }
            sql.execSQL(
                "UPDATE `$table` SET `$column` = REPLACE(`$column`, ?, ?) WHERE instr(`$column`, ?) > 0",
                arrayOf(from, to, from)
            )
        }

        val oldPrefix = "$oldUuid/"
        val newPrefix = "$newUuid/"
        val rest = oldPrefix.length + 1
        val isOld = "entityType = '$ENTITY_TYPE' AND operation = 'UPSERT' AND substr(entityUuid, 1, ${oldPrefix.length}) = ?"
        // Every moved file needs a copy at the new path, including ones another phone uploaded:
        // the payload names the old project so [upload] can fetch that copy when this phone has none.
        val payload = org.json.JSONObject().put(MOVED_FROM, oldUuid).toString()
        for (name in movedNames) {
            sql.execSQL(
                "INSERT INTO sync_outbox (eventId, entityType, entityUuid, operation, revision, payloadJson, createdAt, attemptCount, state, lastError) " +
                    "SELECT lower(hex(randomblob(16))), '$ENTITY_TYPE', ?, 'UPSERT', 1, ?, ?, 0, '${SyncOutboxEvent.STATE_PENDING}', '' " +
                    "WHERE NOT EXISTS (SELECT 1 FROM sync_outbox WHERE entityType = '$ENTITY_TYPE' AND operation = 'UPSERT' " +
                    "AND state != '${SyncOutboxEvent.STATE_SYNCED}' AND entityUuid IN (?, ?))",
                arrayOf<Any>(newPrefix + name, payload, System.currentTimeMillis(), newPrefix + name, oldPrefix + name)
            )
        }
        sql.execSQL(
            "UPDATE sync_outbox SET entityUuid = ? || substr(entityUuid, $rest) WHERE $isOld AND state != '${SyncOutboxEvent.STATE_SYNCED}'",
            arrayOf<Any>(newPrefix, oldPrefix)
        )
    }

    /**
     * Deletes local files, and their cloud copies, that no record refers to any more,
     * e.g. after a report was deleted or a photo removed from it.
     */
    suspend fun collectGarbage(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val sql = db.openHelper.writableDatabase
        val referenced = mutableSetOf<String>()
        for ((table, column) in referencingColumns) {
            sql.query("SELECT `$column` FROM `$table` WHERE `$column` LIKE '%$SCHEME:%'").use { c ->
                while (c.moveToNext()) refsIn(c.getString(0).orEmpty()).forEach { ref -> nameOf(ref)?.let { referenced.add(it) } }
            }
        }
        val cutoff = System.currentTimeMillis() - ORPHAN_AGE_MS
        dir(context).listFiles().orEmpty().forEach { file ->
            if (file.lastModified() > cutoff) return@forEach
            if (file.name.endsWith(".part")) { file.delete(); return@forEach }
            if (!validName.matches(file.name) || file.name in referenced) return@forEach
            file.delete()
            // Only a file this phone uploaded has a cloud copy whose path it knows; a downloaded
            // one is left to the phone that added it (a guessed path would fail on every retry).
            val entity = db.syncOutboxDao().fileEntityFor(file.name) ?: return@forEach
            db.syncOutboxDao().enqueue(
                SyncOutboxEvent(entityType = ENTITY_TYPE, entityUuid = entity, operation = "DELETE", revision = 1, payloadJson = "{}")
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
                runCatching { copyIn(context, Uri.parse(value), kind, null) }.getOrDefault(value)
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
