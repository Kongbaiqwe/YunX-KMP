package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.Pan115AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface Pan115AccountDao {
    @Query("SELECT * FROM pan115_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<Pan115AccountEntity?>

    @Query("SELECT * FROM pan115_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): Pan115AccountEntity?

    @Query("SELECT * FROM pan115_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<Pan115AccountEntity>>

    @Query("SELECT * FROM pan115_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<Pan115AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: Pan115AccountEntity)

    @Query("UPDATE pan115_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE pan115_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM pan115_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pan115_account")
    suspend fun clear()
}
