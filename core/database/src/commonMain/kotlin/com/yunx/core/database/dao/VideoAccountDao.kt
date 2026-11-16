package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.VideoAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoAccountDao {
    @Query("SELECT * FROM video_account ORDER BY platform ASC, isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<VideoAccountEntity>>

    @Query("SELECT * FROM video_account ORDER BY platform ASC, isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<VideoAccountEntity>

    @Query("SELECT * FROM video_account WHERE platform = :platform ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccountsOf(platform: String): Flow<List<VideoAccountEntity>>

    @Query("SELECT * FROM video_account WHERE platform = :platform ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getActive(platform: String): VideoAccountEntity?

    @Query("SELECT COUNT(*) FROM video_account WHERE platform = :platform")
    suspend fun countOf(platform: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: VideoAccountEntity)

    @Query("UPDATE video_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END WHERE platform = :platform")
    suspend fun setPrimary(platform: String, id: String)

    @Query("UPDATE video_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM video_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM video_account WHERE platform = :platform")
    suspend fun clearPlatform(platform: String)
}
