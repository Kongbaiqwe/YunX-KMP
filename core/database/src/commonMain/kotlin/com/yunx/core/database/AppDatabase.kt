package com.yunx.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.yunx.core.database.dao.BaiduAccountDao
import com.yunx.core.database.dao.BookmarkDao
import com.yunx.core.database.dao.C139AccountDao
import com.yunx.core.database.dao.DownloadTaskDao
import com.yunx.core.database.dao.GuangYaAccountDao
import com.yunx.core.database.dao.ILanzouAccountDao
import com.yunx.core.database.dao.LanzouAccountDao
import com.yunx.core.database.dao.Pan115AccountDao
import com.yunx.core.database.dao.Pan123AccountDao
import com.yunx.core.database.dao.QuarkAccountDao
import com.yunx.core.database.dao.UCAccountDao
import com.yunx.core.database.dao.VideoAccountDao
import com.yunx.core.database.dao.XunleiAccountDao
import com.yunx.core.database.entity.BaiduAccountEntity
import com.yunx.core.database.entity.BookmarkEntity
import com.yunx.core.database.entity.C139AccountEntity
import com.yunx.core.database.entity.DownloadTaskEntity
import com.yunx.core.database.entity.GuangYaAccountEntity
import com.yunx.core.database.entity.ILanzouAccountEntity
import com.yunx.core.database.entity.LanzouAccountEntity
import com.yunx.core.database.entity.Pan115AccountEntity
import com.yunx.core.database.entity.Pan123AccountEntity
import com.yunx.core.database.entity.QuarkAccountEntity
import com.yunx.core.database.entity.UCAccountEntity
import com.yunx.core.database.entity.VideoAccountEntity
import com.yunx.core.database.entity.XunleiAccountEntity
import com.yunx.core.database.secure.SecureAccountDaos
import com.yunx.core.database.security.CredentialCipher
import com.yunx.core.database.security.getPlatformCredentialCipher

@Database(
    entities = [
        QuarkAccountEntity::class,
        DownloadTaskEntity::class,
        UCAccountEntity::class,
        XunleiAccountEntity::class,
        BaiduAccountEntity::class,
        C139AccountEntity::class,
        Pan123AccountEntity::class,
        Pan115AccountEntity::class,
        GuangYaAccountEntity::class,
        ILanzouAccountEntity::class,
        LanzouAccountEntity::class,
        BookmarkEntity::class,
        VideoAccountEntity::class
    ],
    version = 24,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rawQuarkAccountDao(): QuarkAccountDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun rawUcAccountDao(): UCAccountDao
    abstract fun rawXunleiAccountDao(): XunleiAccountDao
    abstract fun rawBaiduAccountDao(): BaiduAccountDao
    abstract fun rawC139AccountDao(): C139AccountDao
    abstract fun rawPan123AccountDao(): Pan123AccountDao
    abstract fun rawPan115AccountDao(): Pan115AccountDao
    abstract fun rawGuangYaAccountDao(): GuangYaAccountDao
    abstract fun rawILanzouAccountDao(): ILanzouAccountDao
    abstract fun rawLanzouAccountDao(): LanzouAccountDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun rawVideoAccountDao(): VideoAccountDao

    private var credentialCipher: CredentialCipher = getPlatformCredentialCipher()

    fun setCredentialCipher(cipher: CredentialCipher) {
        this.credentialCipher = cipher
    }

    fun quarkAccountDao(): QuarkAccountDao = SecureAccountDaos.quark(rawQuarkAccountDao(), credentialCipher)
    fun ucAccountDao(): UCAccountDao = SecureAccountDaos.uc(rawUcAccountDao(), credentialCipher)
    fun xunleiAccountDao(): XunleiAccountDao = SecureAccountDaos.xunlei(rawXunleiAccountDao(), credentialCipher)
    fun baiduAccountDao(): BaiduAccountDao = SecureAccountDaos.baidu(rawBaiduAccountDao(), credentialCipher)
    fun c139AccountDao(): C139AccountDao = SecureAccountDaos.c139(rawC139AccountDao(), credentialCipher)
    fun pan123AccountDao(): Pan123AccountDao = SecureAccountDaos.pan123(rawPan123AccountDao(), credentialCipher)
    fun pan115AccountDao(): Pan115AccountDao = SecureAccountDaos.pan115(rawPan115AccountDao(), credentialCipher)
    fun guangyaAccountDao(): GuangYaAccountDao = SecureAccountDaos.guangya(rawGuangYaAccountDao(), credentialCipher)
    fun ilanzouAccountDao(): ILanzouAccountDao = SecureAccountDaos.ilanzou(rawILanzouAccountDao(), credentialCipher)
    fun lanzouAccountDao(): LanzouAccountDao = SecureAccountDaos.lanzou(rawLanzouAccountDao(), credentialCipher)
    fun videoAccountDao(): VideoAccountDao = SecureAccountDaos.videoAccount(rawVideoAccountDao(), credentialCipher)

    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE download_task ADD COLUMN requestHeadersJson TEXT NOT NULL DEFAULT '{}'")
                connection.execSQL("ALTER TABLE download_task ADD COLUMN chunkCount INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE download_task ADD COLUMN plannedTotalSize INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE download_task ADD COLUMN cleanupId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE download_task ADD COLUMN platform TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE download_task ADD COLUMN avgSpeed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bookmark` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`link` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`platform` TEXT NOT NULL, " +
                        "`pwd` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`createTime` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE bookmark ADD COLUMN homePinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE bookmark ADD COLUMN homeLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pan115_account` (" +
                        "`id` TEXT NOT NULL, " +
                        "`cookie` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE `download_task` ADD COLUMN `engineTaskId` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `guangya_account` (" +
                        "`id` TEXT NOT NULL, " +
                        "`accessToken` TEXT NOT NULL, " +
                        "`refreshToken` TEXT NOT NULL, " +
                        "`deviceId` TEXT NOT NULL, " +
                        "`deviceSign` TEXT NOT NULL, " +
                        "`account` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ilanzou_account` (" +
                        "`id` TEXT NOT NULL, " +
                        "`appToken` TEXT NOT NULL, " +
                        "`uuid` TEXT NOT NULL, " +
                        "`account` TEXT NOT NULL, " +
                        "`password` TEXT NOT NULL, " +
                        "`userId` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lanzou_account` (" +
                        "`id` TEXT NOT NULL, " +
                        "`cookie` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE `xunlei_account` ADD COLUMN `authType` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val ACCOUNT_TABLES = listOf(
            "quark_account", "uc_account", "xunlei_account", "baidu_account", "c139_account",
            "pan123_account", "pan115_account", "guangya_account", "ilanzou_account", "lanzou_account"
        )

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(connection: SQLiteConnection) {
                ACCOUNT_TABLES.forEach { table ->
                    connection.execSQL("ALTER TABLE `$table` ADD COLUMN `isPrimary` INTEGER NOT NULL DEFAULT 0")
                    connection.execSQL("ALTER TABLE `$table` ADD COLUMN `remark` TEXT NOT NULL DEFAULT ''")
                }
                ACCOUNT_TABLES.forEach { table ->
                    connection.execSQL("UPDATE `$table` SET `isPrimary` = 1")
                }
                connection.execSQL(
                    "ALTER TABLE `download_task` ADD COLUMN `altSourcesJson` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `video_account` (" +
                        "`id` TEXT NOT NULL, " +
                        "`platform` TEXT NOT NULL, " +
                        "`cookie` TEXT NOT NULL, " +
                        "`nickname` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`isPrimary` INTEGER NOT NULL, " +
                        "`remark` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `download_task` ADD COLUMN `mergeAudioUrl` TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE `download_task` ADD COLUMN `mergeAudioHeadersJson` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `download_task` ADD COLUMN `motionUrl` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `download_task` ADD COLUMN `audioOnly` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getAllMigrations(): Array<Migration> = arrayOf(
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24
        )
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
