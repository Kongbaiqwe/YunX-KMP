package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.LanzouAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LanzouAccountDao {
    @Query("SELECT * FROM lanzou_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<LanzouAccountEntity?>

    @Query("SELECT * FROM lanzou_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): LanzouAccountEntity?

    @Query("SELECT * FROM lanzou_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<LanzouAccountEntity>>

    @Query("SELECT * FROM lanzou_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<LanzouAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: LanzouAccountEntity)

    @Query("UPDATE lanzou_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE lanzou_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM lanzou_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM lanzou_account")
    suspend fun clear()
}
