package com.yunx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunx.core.common.currentEpochMillis

@Entity(tableName = "video_account")
data class VideoAccountEntity(
    @PrimaryKey
    override val id: String,
    val platform: String,
    val cookie: String = "",
    override val nickname: String = "",
    override val updatedAt: Long = currentEpochMillis(),
    override val isPrimary: Boolean = false,
    override val remark: String = ""
) : MultiAccountEntity
