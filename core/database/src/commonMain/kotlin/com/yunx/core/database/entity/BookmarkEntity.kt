package com.yunx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunx.core.common.currentEpochMillis

@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val link: String,
    val title: String = "",
    val platform: String = "",
    val pwd: String = "",
    val category: String = DEFAULT_CATEGORY,
    val homePinned: Boolean = false,
    val homeLabel: String = "",
    val createTime: Long = currentEpochMillis()
) {
    companion object {
        const val DEFAULT_CATEGORY = "未分类"
        val PRESET_CATEGORIES = listOf(
            DEFAULT_CATEGORY, "视频", "文档", "软件", "音乐", "图片", "压缩包", "其他"
        )
    }
}
