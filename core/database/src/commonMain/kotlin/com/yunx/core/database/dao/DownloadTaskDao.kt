package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yunx.core.database.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_task ORDER BY createTime DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Insert
    suspend fun insert(task: DownloadTaskEntity): Long

    @Query("SELECT * FROM download_task WHERE id = :id")
    suspend fun get(id: Long): DownloadTaskEntity?

    @Query("UPDATE download_task SET status = :status, downloadedSize = :downloadedSize, totalSize = :totalSize WHERE id = :id")
    suspend fun updateProgress(id: Long, status: Int, downloadedSize: Long, totalSize: Long)

    @Query("UPDATE download_task SET chunkCount = :chunkCount, plannedTotalSize = :totalSize WHERE id = :id")
    suspend fun updatePlan(id: Long, chunkCount: Int, totalSize: Long)

    @Query("UPDATE download_task SET requestHeadersJson = :encryptedHeaders WHERE id = :id")
    suspend fun updateRequestHeaders(id: Long, encryptedHeaders: String)

    @Query("UPDATE download_task SET status = 2 WHERE status = 1 OR status = 0")
    suspend fun markInterruptedAsPaused()

    @Query("UPDATE download_task SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)

    @Query("UPDATE download_task SET errorMsg = :errorMsg WHERE id = :id")
    suspend fun updateError(id: Long, errorMsg: String)

    @Query("UPDATE download_task SET status = :status, savePath = :savePath, avgSpeed = :avgSpeed WHERE id = :id")
    suspend fun complete(id: Long, status: Int, savePath: String, avgSpeed: Long = 0L)

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE download_task SET engineTaskId = :engineTaskId WHERE id = :id")
    suspend fun updateEngineTaskId(id: Long, engineTaskId: String)

    @Query("UPDATE download_task SET fileName = :name WHERE id = :id")
    suspend fun updateFileName(id: Long, name: String)

    @Query("SELECT * FROM download_task WHERE engineTaskId != '' AND status != 3 AND status != 4")
    suspend fun listSyncableEngineTasks(): List<DownloadTaskEntity>
}
