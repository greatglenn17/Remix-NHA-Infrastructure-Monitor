package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey

@Database(
    entities = [
        Project::class,
        TimeExtension::class,
        VariationOrder::class,
        WorkSuspensionOrder::class,
        WorkResumptionLog::class,
        PendingDocument::class,
        WeeklyReport::class,
        DailyHourlyWeather::class,
        MonthlyReport::class,
        ProjectIssue::class,
        ProjectPayment::class,
        AuditLog::class,
        ProjectInspection::class,
        ProjectImage::class,
        AppNotification::class,
        SdpPlan::class,
        SdpLot::class,
        SdpRoad::class,
        SdpLotProgress::class,
        SdpLotInspection::class
    ],
    version = 20,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun reportDao(): ReportDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audit_logs` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL, " +
                            "`userEmail` TEXT NOT NULL, " +
                            "`userName` TEXT NOT NULL, " +
                            "`userRole` TEXT NOT NULL, " +
                            "`actionType` TEXT NOT NULL, " +
                            "`details` TEXT NOT NULL, " +
                            "`oldValue` TEXT NOT NULL, " +
                            "`newValue` TEXT NOT NULL, " +
                            "`projectId` INTEGER)"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `project_inspections` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`projectId` INTEGER NOT NULL, " +
                            "`inspectionDate` TEXT NOT NULL, " +
                            "`inspectorName` TEXT NOT NULL, " +
                            "`status` TEXT NOT NULL, " +
                            "`findings` TEXT NOT NULL, " +
                            "`recommendations` TEXT NOT NULL, " +
                            "`photoUri` TEXT NOT NULL, " +
                            "`createdDate` TEXT NOT NULL, " +
                            "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE)"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `project_images` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`projectId` INTEGER NOT NULL, " +
                            "`imageUri` TEXT NOT NULL, " +
                            "`caption` TEXT NOT NULL, " +
                            "`category` TEXT NOT NULL, " +
                            "`uploadedBy` TEXT NOT NULL, " +
                            "`uploadDate` TEXT NOT NULL, " +
                            "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_notifications` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`message` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL, " +
                            "`isRead` INTEGER NOT NULL, " +
                            "`projectId` INTEGER, " +
                            "`actionUrl` TEXT NOT NULL)"
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sdp_plans` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`projectId` INTEGER NOT NULL, " +
                            "`version` INTEGER NOT NULL, " +
                            "`planName` TEXT NOT NULL, " +
                            "`pdfFileUrl` TEXT NOT NULL, " +
                            "`description` TEXT NOT NULL, " +
                            "`isActive` INTEGER NOT NULL, " +
                            "`uploadedBy` TEXT NOT NULL, " +
                            "`uploadedDate` TEXT NOT NULL, " +
                            "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_plans_projectId` ON `sdp_plans` (`projectId`)")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sdp_lots` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`projectId` INTEGER NOT NULL, " +
                            "`sdpPlanId` INTEGER NOT NULL, " +
                            "`blockNumber` TEXT NOT NULL, " +
                            "`lotNumber` TEXT NOT NULL, " +
                            "`housingUnitNumber` TEXT NOT NULL, " +
                            "`lotAreaSqM` REAL NOT NULL, " +
                            "`polygonNormalizedJson` TEXT NOT NULL, " +
                            "`description` TEXT NOT NULL, " +
                            "`isActive` INTEGER NOT NULL, " +
                            "`createdBy` TEXT NOT NULL, " +
                            "`createdDate` TEXT NOT NULL, " +
                            "`lastModifiedBy` TEXT NOT NULL, " +
                            "`lastModifiedDate` TEXT NOT NULL, " +
                            "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`sdpPlanId`) REFERENCES `sdp_plans`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_lots_projectId` ON `sdp_lots` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_lots_sdpPlanId` ON `sdp_lots` (`sdpPlanId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sdp_roads` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`projectId` INTEGER NOT NULL, " +
                            "`sdpPlanId` INTEGER NOT NULL, " +
                            "`roadName` TEXT NOT NULL, " +
                            "`roadType` TEXT NOT NULL, " +
                            "`polylineNormalizedJson` TEXT NOT NULL, " +
                            "`isActive` INTEGER NOT NULL, " +
                            "`createdBy` TEXT NOT NULL, " +
                            "`createdDate` TEXT NOT NULL, " +
                            "`lastModifiedBy` TEXT NOT NULL, " +
                            "`lastModifiedDate` TEXT NOT NULL, " +
                            "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`sdpPlanId`) REFERENCES `sdp_plans`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_roads_projectId` ON `sdp_roads` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_roads_sdpPlanId` ON `sdp_roads` (`sdpPlanId`)")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sdp_lot_progress` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`projectId` INTEGER NOT NULL, " +
                            "`sdpPlanId` INTEGER NOT NULL, " +
                            "`sdpLotId` INTEGER NOT NULL, " +
                            "`physicalProgress` INTEGER NOT NULL, " +
                            "`constructionStatus` TEXT NOT NULL, " +
                            "`currentActivity` TEXT NOT NULL, " +
                            "`startDate` TEXT NOT NULL, " +
                            "`targetCompletionDate` TEXT NOT NULL, " +
                            "`contractor` TEXT NOT NULL, " +
                            "`remarks` TEXT NOT NULL, " +
                            "`billingStatus` TEXT NOT NULL, " +
                            "`billingDate` TEXT NOT NULL, " +
                            "`billedBy` TEXT NOT NULL, " +
                            "`billingReference` TEXT NOT NULL, " +
                            "`billingRemarks` TEXT NOT NULL, " +
                            "`createdBy` TEXT NOT NULL, " +
                            "`createdDate` TEXT NOT NULL, " +
                            "`lastModifiedBy` TEXT NOT NULL, " +
                            "`lastModifiedDate` TEXT NOT NULL, " +
                            "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`sdpPlanId`) REFERENCES `sdp_plans`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`sdpLotId`) REFERENCES `sdp_lots`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_lot_progress_projectId` ON `sdp_lot_progress` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_lot_progress_sdpPlanId` ON `sdp_lot_progress` (`sdpPlanId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sdp_lot_progress_sdpLotId` ON `sdp_lot_progress` (`sdpLotId`)")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sdp_lot_inspections` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`projectId` INTEGER NOT NULL, " +
                            "`sdpPlanId` INTEGER NOT NULL, " +
                            "`sdpLotId` INTEGER NOT NULL, " +
                            "`inspectionTimestamp` INTEGER NOT NULL, " +
                            "`inspectionDate` TEXT NOT NULL, " +
                            "`inspectedBy` TEXT NOT NULL, " +
                            "`physicalProgress` INTEGER NOT NULL, " +
                            "`constructionStatus` TEXT NOT NULL, " +
                            "`currentActivity` TEXT NOT NULL, " +
                            "`contractor` TEXT NOT NULL, " +
                            "`remarks` TEXT NOT NULL, " +
                            "`billingStatus` TEXT NOT NULL, " +
                            "`billingReference` TEXT NOT NULL, " +
                            "`createdDate` TEXT NOT NULL, " +
                            "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`sdpPlanId`) REFERENCES `sdp_plans`(`id`) ON DELETE CASCADE, " +
                            "FOREIGN KEY(`sdpLotId`) REFERENCES `sdp_lots`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_lot_inspections_projectId` ON `sdp_lot_inspections` (`projectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_lot_inspections_sdpPlanId` ON `sdp_lot_inspections` (`sdpPlanId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sdp_lot_inspections_sdpLotId` ON `sdp_lot_inspections` (`sdpLotId`)")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                performPreMigrationBackup(context.applicationContext, "nha_construction_db_10")
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nha_construction_db_10"
                )
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(context.applicationContext, scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun performPreMigrationBackup(context: Context, dbName: String) {
            try {
                val currentDbFile = context.getDatabasePath(dbName)
                if (currentDbFile.exists()) {
                    val backupFile = File(currentDbFile.parentFile, "${dbName}_pre_migration_backup.db.enc")
                    if (backupFile.exists()) backupFile.delete()

                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    val encryptedBackup = EncryptedFile.Builder(
                        context,
                        backupFile,
                        masterKey,
                        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                    ).build()

                    val bytes = currentDbFile.readBytes()
                    encryptedBackup.openFileOutput().use { output ->
                        output.write(bytes)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private class DatabaseCallback(
            private val context: Context,
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("PRAGMA foreign_keys=ON;")
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys=ON;")
            }
        }
    }
}

