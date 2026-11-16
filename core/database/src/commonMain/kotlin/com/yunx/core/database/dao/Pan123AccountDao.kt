package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.Pan123AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface Pan123AccountDao {
    @Query("SELECT * FROM pan123_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<Pan123AccountEntity?>

    @Query("SELECT * FROM pan123_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): Pan123AccountEntity?

    @Query("SELECT * FROM pan123_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<Pan123AccountEntity>>

    @Query("SELECT * FROM pan123_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<Pan123AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: Pan123AccountEntity)

    @Query("UPDATE pan123_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE pan123_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM pan123_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pan123_account")
    suspend fun clear()
}
