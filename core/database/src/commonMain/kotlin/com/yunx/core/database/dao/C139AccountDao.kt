package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.C139AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface C139AccountDao {
    @Query("SELECT * FROM c139_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<C139AccountEntity?>

    @Query("SELECT * FROM c139_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): C139AccountEntity?

    @Query("SELECT * FROM c139_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<C139AccountEntity>>

    @Query("SELECT * FROM c139_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<C139AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: C139AccountEntity)

    @Query("UPDATE c139_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE c139_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM c139_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM c139_account")
    suspend fun clear()
}
