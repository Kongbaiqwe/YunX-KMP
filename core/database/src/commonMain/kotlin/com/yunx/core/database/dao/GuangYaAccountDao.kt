package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.GuangYaAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GuangYaAccountDao {
    @Query("SELECT * FROM guangya_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<GuangYaAccountEntity?>

    @Query("SELECT * FROM guangya_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): GuangYaAccountEntity?

    @Query("SELECT * FROM guangya_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<GuangYaAccountEntity>>

    @Query("SELECT * FROM guangya_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<GuangYaAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: GuangYaAccountEntity)

    @Query("UPDATE guangya_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE guangya_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM guangya_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM guangya_account")
    suspend fun clear()
}
