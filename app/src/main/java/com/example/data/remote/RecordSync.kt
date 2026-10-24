package com.example.data.remote

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.data.files.Attachments
import com.example.data.local.AppDatabase
import com.example.data.local.RecordSyncSchema
import org.json.JSONArray
import org.json.JSONObject

/**
 * Moves synced records (see [RecordSyncSchema]) between Room and the Firestore
 * `records` collection. A cloud record is one document per row:
 *
 *   records/{uuid} = { uuid, type, projectUuid, assignedUids, revision, updatedAt, updatedByUid, isDeleted, data }
 *
 * `data` is the row as a JSON string without its local ids, so the format follows
 * the table's columns without per-type code. A weekly report also carries its
 * weather rows under `_weather`.
 */
class RecordSync(private val db: AppDatabase) {

    /** Columns that only mean something on this phone, plus the sync fields sent separately. */
    private val localOnly = setOf("id", "projectId", "weeklyReportId", "uuid", "revision", "updatedAt")
    private val sql get() = db.openHelper.writableDatabase
    private val columnCache = mutableMapOf<String, Set<String>>()

    /** Remote records whose project hasn't reached this phone yet, retried after each sync pass. */
    private val deferred = linkedMapOf<String, Map<String, Any?>>()

    /** The cloud document for a local row, or null when the row or its project no longer exists. */
    fun buildDocument(table: String, uuid: String, uid: String): Map<String, Any>? {
        require(table in RecordSyncSchema.TABLES)
        val row = sql.query("SELECT * FROM `$table` WHERE uuid = ? LIMIT 1", arrayOf(uuid)).use { c ->
            if (c.moveToFirst()) c.toJson() else null
        } ?: return null
        val (projectUuid, assignedUids) = projectFor(row.optLong("projectId")) ?: return null
        val data = JSONObject()
        row.keys().forEach { key -> if (key !in localOnly) data.put(key, row.get(key)) }
        if (table == "weekly_reports") {
            val weather = JSONArray()
            sql.query(
                "SELECT * FROM daily_hourly_weather WHERE weeklyReportId = ? ORDER BY date",
                arrayOf(row.getLong("id"))
            ).use { c ->
                while (c.moveToNext()) {
                    val w = c.toJson()
                    for (key in listOf("id", "projectId", "weeklyReportId")) w.remove(key)
                    weather.put(w)
                }
            }
            data.put("_weather", weather)
        }
        return hashMapOf<String, Any>(
            "uuid" to uuid,
            "type" to table,
            "projectUuid" to projectUuid,
            "revision" to row.getLong("revision"),
            "updatedAt" to row.getLong("updatedAt"),
            "updatedByUid" to uid,
            "isDeleted" to false,
            "data" to data.toString()
        ).apply {
            // Left out while the project's list is unknown here; the upload then waits for it.
            AssignmentPolicy.recordUploadList(assignedUids)?.let { put("assignedUids", it) }
        }
    }

    /**
     * Applies cloud records to Room. A record is skipped while this phone still has an
     * unsent change for it, or when the local copy is already at that revision or newer.
     */
    suspend fun applyRemote(docs: List<Map<String, Any?>>) {
        for (doc in docs) (doc["uuid"] as? String)?.let { deferred[it] = doc }
        if (deferred.isEmpty()) return

        val pending = deferred.values.sortedBy { RecordSyncSchema.TABLES.indexOf(it["type"]) }
        for (doc in pending) {
            val uuid = doc["uuid"] as String
            if (db.syncOutboxDao().hasPendingFor(uuid)) {
                // Both phones changed it: the unsent local edit wins, so lift its revision
                // above the cloud's or the other phones would ignore it.
                keepLocalAbove(doc)
                deferred.remove(uuid)
                continue
            }
            if (applyOne(doc)) deferred.remove(uuid)
        }
    }

    /** Re-tries records that were waiting for their project, e.g. after a new project arrives. */
    suspend fun retryDeferred() = applyRemote(emptyList())

    /** Returns false when the record has to wait for its project. */
    private fun applyOne(doc: Map<String, Any?>): Boolean {
        val table = doc["type"] as? String ?: return true
        if (table !in RecordSyncSchema.TABLES) return true
        val uuid = doc["uuid"] as String
        val revision = (doc["revision"] as? Number)?.toLong() ?: 1L
        val isDeleted = doc["isDeleted"] == true

        val remoteUpdatedAt = (doc["updatedAt"] as? Number)?.toLong() ?: 0L
        val local = sql.query("SELECT id, revision, updatedAt FROM `$table` WHERE uuid = ? LIMIT 1", arrayOf(uuid)).use { c ->
            if (c.moveToFirst()) Triple(c.getLong(0), c.getLong(1), c.getLong(2)) else null
        }

        if (isDeleted) {
            if (local != null) writeRemote { it.delete("`$table`", "id = ?", arrayOf(local.first)) }
            return true
        }
        if (local != null && !RemoteVersion.isNewer(local.second, local.third, revision, remoteUpdatedAt)) return true

        val projectId = sql.query(
            "SELECT id FROM projects WHERE projectUuid = ? LIMIT 1", arrayOf(doc["projectUuid"] as? String ?: "")
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return false

        val data = JSONObject(doc["data"] as? String ?: "{}")
        val values = data.toContentValues(columnsOf(table)).apply {
            put("projectId", projectId)
            put("uuid", uuid)
            put("revision", revision)
            put("updatedAt", remoteUpdatedAt)
        }

        writeRemote { w ->
            val rowId = if (local == null) {
                w.insert("`$table`", SQLiteDatabase.CONFLICT_ABORT, values)
            } else {
                w.update("`$table`", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf(local.first))
                local.first
            }
            if (table == "weekly_reports") {
                w.delete("daily_hourly_weather", "weeklyReportId = ?", arrayOf(rowId))
                val weather = data.optJSONArray("_weather") ?: JSONArray()
                for (i in 0 until weather.length()) {
                    val row = weather.getJSONObject(i).toContentValues(columnsOf("daily_hourly_weather")).apply {
                        put("projectId", projectId)
                        put("weeklyReportId", rowId)
                    }
                    w.insert("daily_hourly_weather", SQLiteDatabase.CONFLICT_ABORT, row)
                }
            }
        }
        return true
    }

    private fun keepLocalAbove(doc: Map<String, Any?>) {
        val table = doc["type"] as? String ?: return
        if (table !in RecordSyncSchema.TABLES) return
        val remoteRevision = (doc["revision"] as? Number)?.toLong() ?: return
        writeRemote {
            it.execSQL(
                "UPDATE `$table` SET revision = ? WHERE uuid = ? AND revision <= ?",
                arrayOf(remoteRevision + 1, doc["uuid"], remoteRevision)
            )
            // The upload already queued may have read the old revision; queue one more.
            it.execSQL(
                "INSERT INTO sync_outbox (eventId, entityType, entityUuid, operation, revision, payloadJson, createdAt, attemptCount, state, lastError) " +
                    "VALUES (lower(hex(randomblob(16))), ?, ?, 'UPSERT', ?, '{}', ?, 0, 'PENDING', '')",
                arrayOf(RecordSyncSchema.ENTITY_PREFIX + table, doc["uuid"], remoteRevision + 1, System.currentTimeMillis())
            )
        }
    }

    /** Runs a cloud write with the sync triggers switched off, so it isn't uploaded back. */
    private fun writeRemote(block: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit) {
        val w = sql
        db.runInTransaction {
            w.execSQL("UPDATE sync_state SET applyingRemote = 1")
            block(w)
            w.execSQL("UPDATE sync_state SET applyingRemote = 0")
        }
    }

    /** The project's uuid and assignedUids JSON, or null when it isn't on this phone. */
    private fun projectFor(projectId: Long): Pair<String, String>? =
        sql.query("SELECT projectUuid, assignedUids FROM projects WHERE id = ? LIMIT 1", arrayOf(projectId)).use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getString(1).orEmpty() else null
        }

    /** The assignedUids JSON of the local project with [projectUuid], or null when it isn't on this phone. */
    fun assignedUidsOf(projectUuid: String): String? =
        sql.query("SELECT assignedUids FROM projects WHERE projectUuid = ? LIMIT 1", arrayOf(projectUuid)).use { c ->
            if (c.moveToFirst()) c.getString(0).orEmpty() else null
        }

    /**
     * Stores the cloud's list of assigned accounts on the local project, without queuing an
     * upload. An identical list is not written again, so open screens aren't refreshed for nothing.
     */
    fun setAssignedUidsLocally(projectUuid: String, assignedUidsJson: String) = writeRemote {
        it.execSQL(
            "UPDATE projects SET assignedUids = ? WHERE projectUuid = ? AND assignedUids IS NOT ?",
            arrayOf(assignedUidsJson, projectUuid, assignedUidsJson)
        )
    }

    /**
     * Removes a project this account is no longer assigned to from this phone only. Its
     * records go with it through the foreign keys' ON DELETE CASCADE; the triggers are off,
     * so nothing is tombstoned in the cloud.
     */
    fun removeProjectLocally(projectId: Long) = writeRemote {
        it.delete("projects", "id = ?", arrayOf(projectId))
    }

    enum class Unassigned { KEPT, HIDDEN, REMOVED }

    /**
     * Takes a project the user is no longer assigned to off this phone, without touching
     * the cloud. The checks run in the same transaction as the write, so a change made
     * meanwhile can't be lost:
     * - something still to upload: nothing happens ([Unassigned.KEPT]);
     * - rows that only exist on this phone (inspections, time extensions, SDP data, audit
     *   logs...), or a legacy attachment (`nhafile:<id>`, whose cloud copy only the Super
     *   Admin can read, so the local file could never be downloaded again): the row stays
     *   and only gets the server's [serverAssignedUids], which hides it; a later
     *   re-assignment brings it back ([Unassigned.HIDDEN]). An identical list isn't rewritten;
     * - otherwise the project and its synced records are deleted ([Unassigned.REMOVED]).
     *   [deleteFiles] gets the project's attachment file names before the delete commits,
     *   so the garbage collector never sees them unreferenced (it would delete the cloud copies).
     */
    fun unassignLocally(
        projectId: Long,
        projectUuid: String,
        serverAssignedUids: String,
        deleteFiles: (Set<String>) -> Unit
    ): Unassigned {
        var result = Unassigned.KEPT
        writeRemote { w ->
            if (hasPendingUpload(projectId, projectUuid)) return@writeRemote
            val refs = attachmentRefsOf(projectId)
            if (hasLocalOnlyRows(projectId) || refs.any { Attachments.projectOf(it) == null }) {
                w.execSQL(
                    "UPDATE projects SET assignedUids = ? WHERE id = ? AND assignedUids IS NOT ?",
                    arrayOf(serverAssignedUids, projectId, serverAssignedUids)
                )
                result = Unassigned.HIDDEN
                return@writeRemote
            }
            deleteFiles(refs.map(::fileNameOf).toSet())
            w.delete("projects", "id = ?", arrayOf(projectId))
            result = Unassigned.REMOVED
        }
        return result
    }

    /**
     * Tables deleted with a project (a foreign key to `projects`) that the cloud doesn't
     * hold, read from the database's own foreign keys: table -> its column naming the project.
     * The synced record tables are left out, and so are weather rows, which travel inside
     * their weekly report.
     */
    fun localOnlyChildTables(): Map<String, String> = localOnlyTables ?: buildMap {
        val tables = sql.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        val synced = RecordSyncSchema.TABLES.toSet() + "daily_hourly_weather"
        for (table in tables) {
            if (table in synced) continue
            sql.query("PRAGMA foreign_key_list(`$table`)").use { c ->
                while (c.moveToNext()) {
                    if (c.getString(c.getColumnIndexOrThrow("table")) == "projects") {
                        put(table, c.getString(c.getColumnIndexOrThrow("from")))
                    }
                }
            }
        }
    }.also { localOnlyTables = it }

    @Volatile private var localOnlyTables: Map<String, String>? = null

    /** Whether the project has rows the cloud can't give back (see [localOnlyChildTables]). */
    fun hasLocalOnlyRows(projectId: Long): Boolean = localOnlyChildTables().any { (table, column) ->
        sql.query("SELECT EXISTS(SELECT 1 FROM `$table` WHERE `$column` = ?)", arrayOf(projectId)).use { it.moveToFirst() && it.getInt(0) == 1 }
    }

    /** Whether this phone still has to send something for the project, one of its records or one of its files. */
    fun hasPendingUpload(projectId: Long, projectUuid: String): Boolean {
        val open = "state IN ('PENDING', 'FAILED')"
        fun exists(query: String, args: Array<Any?>) = sql.query(query, args).use { it.moveToFirst() && it.getInt(0) == 1 }

        if (exists("SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE entityUuid = ? AND $open)", arrayOf(projectUuid))) return true
        for (table in RecordSyncSchema.TABLES) {
            val query = "SELECT EXISTS(SELECT 1 FROM sync_outbox o JOIN `$table` t ON t.uuid = o.entityUuid " +
                "WHERE t.projectId = ? AND o.entityType = ? AND o.$open)"
            if (exists(query, arrayOf(projectId, RecordSyncSchema.ENTITY_PREFIX + table))) return true
        }
        // A record delete outlives its row; its payload names the project.
        val deletes = "SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE entityType LIKE ? AND $open AND payloadJson LIKE ?)"
        if (exists(deletes, arrayOf(RecordSyncSchema.ENTITY_PREFIX + "%", "%\"projectUuid\":\"$projectUuid\"%"))) return true

        val names = attachmentNamesOf(projectId)
        sql.query("SELECT entityUuid FROM sync_outbox WHERE entityType = ? AND $open", arrayOf(Attachments.ENTITY_TYPE)).use { c ->
            while (c.moveToNext()) {
                val entity = c.getString(0).orEmpty()
                if (entity.startsWith("$projectUuid/") || entity.substringAfterLast('/') in names) return true
            }
        }
        return false
    }

    /** Local file names of the attachments the project and its records refer to. */
    fun attachmentNamesOf(projectId: Long): Set<String> = attachmentRefsOf(projectId).map(::fileNameOf).toSet()

    private fun fileNameOf(ref: String) = ref.removePrefix("${Attachments.SCHEME}:").substringAfterLast('/')

    /** The attachment references (`nhafile:...`) the project and its records hold. */
    fun attachmentRefsOf(projectId: Long): Set<String> {
        val refs = mutableSetOf<String>()
        fun collect(query: String) = sql.query(query, arrayOf(projectId)).use { c ->
            while (c.moveToNext()) {
                for (i in 0 until c.columnCount) {
                    if (c.getType(i) != Cursor.FIELD_TYPE_STRING) continue
                    refs.addAll(Attachments.refsIn(c.getString(i)))
                }
            }
        }
        collect("SELECT * FROM projects WHERE id = ?")
        for (table in RecordSyncSchema.TABLES) collect("SELECT * FROM `$table` WHERE projectId = ?")
        return refs
    }

    /** Only columns this version of the app has, so a newer phone's extra fields are ignored. */
    private fun columnsOf(table: String): Set<String> = columnCache.getOrPut(table) {
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }
    }

    private fun JSONObject.toContentValues(columns: Set<String>) = ContentValues().also { v ->
        keys().forEach { key ->
            if (key !in columns || key in localOnly) return@forEach
            when (val value = get(key)) {
                JSONObject.NULL -> v.putNull(key)
                is Int, is Long -> v.put(key, (value as Number).toLong())
                is Number -> v.put(key, value.toDouble())
                is Boolean -> v.put(key, if (value) 1L else 0L)
                else -> v.put(key, value.toString())
            }
        }
    }

    private fun Cursor.toJson() = JSONObject().also { o ->
        for (i in 0 until columnCount) {
            val name = getColumnName(i)
            when (getType(i)) {
                Cursor.FIELD_TYPE_NULL -> o.put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> o.put(name, getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> o.put(name, getDouble(i))
                else -> o.put(name, getString(i))
            }
        }
    }
}
