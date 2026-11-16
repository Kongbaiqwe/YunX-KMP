package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.XunleiAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface XunleiAccountDao {
    @Query("SELECT * FROM xunlei_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<XunleiAccountEntity?>

    @Query("SELECT * FROM xunlei_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): XunleiAccountEntity?

    @Query("SELECT * FROM xunlei_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<XunleiAccountEntity>>

    @Query("SELECT * FROM xunlei_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<XunleiAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: XunleiAccountEntity)

    @Query("UPDATE xunlei_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE xunlei_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM xunlei_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM xunlei_account")
    suspend fun clear()
}
