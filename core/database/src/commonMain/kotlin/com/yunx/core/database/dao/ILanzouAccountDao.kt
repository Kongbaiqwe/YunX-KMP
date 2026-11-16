package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yunx.core.database.entity.ILanzouAccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ILanzouAccountDao {
    @Query("SELECT * FROM ilanzou_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    fun observeAccount(): Flow<ILanzouAccountEntity?>

    @Query("SELECT * FROM ilanzou_account ORDER BY isPrimary DESC, updatedAt DESC LIMIT 1")
    suspend fun getAccount(): ILanzouAccountEntity?

    @Query("SELECT * FROM ilanzou_account ORDER BY isPrimary DESC, updatedAt DESC")
    fun observeAccounts(): Flow<List<ILanzouAccountEntity>>

    @Query("SELECT * FROM ilanzou_account ORDER BY isPrimary DESC, updatedAt DESC")
    suspend fun getAccounts(): List<ILanzouAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: ILanzouAccountEntity)

    @Query("UPDATE ilanzou_account SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setPrimary(id: String)

    @Query("UPDATE ilanzou_account SET remark = :remark WHERE id = :id")
    suspend fun updateRemark(id: String, remark: String)

    @Query("DELETE FROM ilanzou_account WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ilanzou_account")
    suspend fun clear()
}
