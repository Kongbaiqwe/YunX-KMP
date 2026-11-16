package com.yunx.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

actual fun getAppDatabaseBuilder(dbFilePath: String?): RoomDatabase.Builder<AppDatabase> {
    val builder = if (dbFilePath == null || dbFilePath.isEmpty()) {
        val tempFile = File.createTempFile("yunx_db", ".db")
        tempFile.deleteOnExit()
        Room.databaseBuilder<AppDatabase>(name = tempFile.absolutePath)
    } else {
        Room.databaseBuilder<AppDatabase>(name = dbFilePath)
    }
    return builder.setDriver(BundledSQLiteDriver())
}
