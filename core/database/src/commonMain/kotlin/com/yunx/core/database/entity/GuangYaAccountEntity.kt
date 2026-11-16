package com.yunx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunx.core.common.currentEpochMillis

@Entity(tableName = "guangya_account")
data class GuangYaAccountEntity(
    @PrimaryKey
    override val id: String = "guangya",
    val accessToken: String = "",
    val refreshToken: String = "",
    val deviceId: String = "",
    val deviceSign: String = "",
    val account: String = "",
    override val nickname: String = "",
    override val updatedAt: Long = currentEpochMillis(),
    override val isPrimary: Boolean = false,
    override val remark: String = ""
) : MultiAccountEntity
