package com.yunx.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunx.core.common.currentEpochMillis

@Entity(tableName = "download_task")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val fileName: String,
    val totalSize: Long = 0L,
    val downloadedSize: Long = 0L,
    val status: Int = STATUS_PENDING,
    val errorMsg: String = "",
    val savePath: String = "",
    @ColumnInfo(defaultValue = "'{}'")
    val requestHeadersJson: String = "{}",
    @ColumnInfo(defaultValue = "0")
    val chunkCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val plannedTotalSize: Long = 0L,
    @ColumnInfo(defaultValue = "''")
    val cleanupId: String = "",
    @ColumnInfo(defaultValue = "''")
    val platform: String = "",
    @ColumnInfo(defaultValue = "''")
    val engineTaskId: String = "",
    @ColumnInfo(defaultValue = "''")
    val altSourcesJson: String = "",
    @ColumnInfo(defaultValue = "''")
    val mergeAudioUrl: String = "",
    @ColumnInfo(defaultValue = "''")
    val mergeAudioHeadersJson: String = "",
    @ColumnInfo(defaultValue = "''")
    val motionUrl: String = "",
    @ColumnInfo(defaultValue = "0")
    val audioOnly: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val avgSpeed: Long = 0,
    val createTime: Long = currentEpochMillis()
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_PAUSED = 2
        const val STATUS_COMPLETED = 3
        const val STATUS_FAILED = 4
    }
}
