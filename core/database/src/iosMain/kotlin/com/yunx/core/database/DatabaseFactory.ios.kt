package com.yunx.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun getAppDatabaseBuilder(dbFilePath: String?): RoomDatabase.Builder<AppDatabase> {
    val path = dbFilePath ?: run {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null
        )
        requireNotNull(documentDirectory?.path) + "/yunx.db"
    }
    return Room.databaseBuilder<AppDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
}
