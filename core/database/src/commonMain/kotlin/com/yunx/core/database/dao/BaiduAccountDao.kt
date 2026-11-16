package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.BaiduAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BaiduAccountDao {
    @Query("SELECT * FROM baidu_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<BaiduAccountEntity?>

    @Query("SELECT * FROM baidu_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): BaiduAccountEntity?

    @Query("SELECT * FROM baidu_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<BaiduAccountEntity>>

    @Query("SELECT * FROM baidu_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<BaiduAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: BaiduAccountEntity)

    @Query("UPDATE baidu_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE baidu_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM baidu_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM baidu_account")
    suspend fun clear()
}
