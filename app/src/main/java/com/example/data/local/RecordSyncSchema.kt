package com.example.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database side of cross-device record sync (weekly/monthly reports, documents,
 * issues, payments and the subdivision plan with its lots, roads, progress and inspections).
 *
 * Each synced table has a `uuid`, `revision` and `updatedAt` column. SQLite triggers
 * queue a `record:<table>` event in sync_outbox for every local insert, update and
 * delete, so no write path in the app can forget to sync. An update also bumps the
 * revision and keeps the row's uuid, even when the caller wrote a fresh object.
 * Room turns on recursive triggers, so the update trigger skips writes that already
 * raise the revision; its own bump is one of those.
 *
 * Changes that arrive from the cloud are written with `sync_state.applyingRemote = 1`
 * inside the same transaction, which switches the triggers off so they are not
 * uploaded straight back.
 */
object RecordSyncSchema {

    /** The first synced tables (database 22). */
    val REPORT_TABLES = listOf(
        "weekly_reports", "monthly_reports", "pending_documents", "project_issues", "project_payments"
    )

    /** Subdivision plan tables, synced from database 24. A plan comes before the lots that point at it. */
    val SDP_TABLES = listOf("sdp_plans", "sdp_lots", "sdp_roads", "sdp_lot_progress", "sdp_lot_inspections")

    /** Developer requests, synced from database 28; the table is created with its sync columns. */
    val REQUEST_TABLES = listOf("developer_requests")

    /** Tables synced as records, in the order a fresh device should apply them. */
    val TABLES = REPORT_TABLES + SDP_TABLES + REQUEST_TABLES

    /**
     * Columns holding another synced row's local id: table -> (column -> the table it points at).
     * The cloud copy carries the parent's uuid instead (see RecordSync).
     */
    val PARENT_REFS = mapOf(
        "sdp_lots" to mapOf("sdpPlanId" to "sdp_plans"),
        "sdp_roads" to mapOf("sdpPlanId" to "sdp_plans"),
        "sdp_lot_progress" to mapOf("sdpPlanId" to "sdp_plans", "sdpLotId" to "sdp_lots"),
        "sdp_lot_inspections" to mapOf("sdpPlanId" to "sdp_plans", "sdpLotId" to "sdp_lots")
    )

    const val ENTITY_PREFIX = "record:"

    private const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"
    private const val NEW_EVENT_ID = "lower(hex(randomblob(16)))"
    private const val LOCAL_WRITE = "(SELECT applyingRemote FROM sync_state) = 0"

    /** Adds the sync columns to [tables], gives existing rows an identity, and queues them for upload (21 -> 22, 23 -> 24). */
    fun migrate(db: SupportSQLiteDatabase, tables: List<String>) {
        for (table in tables) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `$table` SET `uuid` = lower(hex(randomblob(16))), `updatedAt` = $NOW_MS")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_uuid` ON `$table` (`uuid`)")
            db.execSQL(
                "INSERT INTO sync_outbox (eventId, entityType, entityUuid, operation, revision, payloadJson, createdAt, attemptCount, state, lastError) " +
                    "SELECT $NEW_EVENT_ID, '$ENTITY_PREFIX$table', uuid, 'UPSERT', revision, '{}', $NOW_MS, 0, 'PENDING', '' FROM `$table`"
            )
        }
    }

    /** Creates the trigger switch and (re)creates the triggers. Runs on every open, so fresh installs get them and fixes reach existing ones. */
    fun install(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (id INTEGER PRIMARY KEY CHECK (id = 1), applyingRemote INTEGER NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO sync_state (id, applyingRemote) VALUES (1, 0)")
        // A crash can't leave it on (the apply runs in a transaction), but never trust it across opens.
        db.execSQL("UPDATE sync_state SET applyingRemote = 0")

        for (table in TABLES) {
            for (op in listOf("insert", "update", "delete")) db.execSQL("DROP TRIGGER IF EXISTS `sync_${table}_$op`")
            val enqueue = "INSERT INTO sync_outbox (eventId, entityType, entityUuid, operation, revision, payloadJson, createdAt, attemptCount, state, lastError) VALUES "
            db.execSQL(
                "CREATE TRIGGER `sync_${table}_insert` AFTER INSERT ON `$table` WHEN $LOCAL_WRITE BEGIN " +
                    "$enqueue ($NEW_EVENT_ID, '$ENTITY_PREFIX$table', NEW.uuid, 'UPSERT', NEW.revision, '{}', $NOW_MS, 0, 'PENDING', ''); END"
            )
            db.execSQL(
                "CREATE TRIGGER `sync_${table}_update` AFTER UPDATE ON `$table` WHEN $LOCAL_WRITE AND NEW.revision <= OLD.revision BEGIN " +
                    "UPDATE `$table` SET uuid = CASE WHEN OLD.uuid = '' THEN NEW.uuid ELSE OLD.uuid END, " +
                    "revision = MAX(OLD.revision, NEW.revision) + 1, updatedAt = $NOW_MS WHERE id = NEW.id; " +
                    "$enqueue ($NEW_EVENT_ID, '$ENTITY_PREFIX$table', CASE WHEN OLD.uuid = '' THEN NEW.uuid ELSE OLD.uuid END, " +
                    "'UPSERT', MAX(OLD.revision, NEW.revision) + 1, '{}', $NOW_MS, 0, 'PENDING', ''); END"
            )
            // The payload keeps what the upload needs once the row is gone.
            db.execSQL(
                "CREATE TRIGGER `sync_${table}_delete` AFTER DELETE ON `$table` WHEN $LOCAL_WRITE BEGIN " +
                    "$enqueue ($NEW_EVENT_ID, '$ENTITY_PREFIX$table', OLD.uuid, 'DELETE', OLD.revision + 1, " +
                    "'{\"projectUuid\":\"' || IFNULL((SELECT projectUuid FROM projects WHERE id = OLD.projectId), '') || '\"}', " +
                    "$NOW_MS, 0, 'PENDING', ''); END"
            )
        }
    }
}
