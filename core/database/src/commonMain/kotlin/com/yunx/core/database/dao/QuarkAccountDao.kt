package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.QuarkAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuarkAccountDao {
    @Query("SELECT * FROM quark_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<QuarkAccountEntity?>

    @Query("SELECT * FROM quark_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): QuarkAccountEntity?

    @Query("SELECT * FROM quark_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<QuarkAccountEntity>>

    @Query("SELECT * FROM quark_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<QuarkAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: QuarkAccountEntity)

    @Query("UPDATE quark_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE quark_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM quark_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM quark_account")
    suspend fun clear()
}
