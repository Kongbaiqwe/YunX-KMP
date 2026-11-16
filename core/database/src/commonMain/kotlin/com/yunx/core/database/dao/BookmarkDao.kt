package com.yunx.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yunx.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmark ORDER BY createTime DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark WHERE homePinned = 1 ORDER BY createTime DESC")
    fun observeHomePinned(): Flow<List<BookmarkEntity>>

    @Query("SELECT DISTINCT category FROM bookmark ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("UPDATE bookmark SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE bookmark SET homePinned = :pinned WHERE id = :id")
    suspend fun updateHomePinned(id: Long, pinned: Boolean)

    @Query("UPDATE bookmark SET homeLabel = :label WHERE id = :id")
    suspend fun updateHomeLabel(id: Long, label: String)

    @Query("DELETE FROM bookmark WHERE id = :id")
    suspend fun delete(id: Long)
}
