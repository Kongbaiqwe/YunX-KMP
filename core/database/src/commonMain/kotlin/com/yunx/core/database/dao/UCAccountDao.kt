package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.UCAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UCAccountDao {
    @Query("SELECT * FROM uc_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<UCAccountEntity?>

    @Query("SELECT * FROM uc_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): UCAccountEntity?

    @Query("SELECT * FROM uc_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<UCAccountEntity>>

    @Query("SELECT * FROM uc_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<UCAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: UCAccountEntity)

    @Query("UPDATE uc_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE uc_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM uc_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM uc_account")
    suspend fun clear()
}
