package com.yunx.core.database

import androidx.room.RoomDatabase

expect fun getAppDatabaseBuilder(dbFilePath: String? = null): RoomDatabase.Builder<AppDatabase>
