package com.yunx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunx.core.common.currentEpochMillis

@Entity(tableName = "pan123_account")
data class Pan123AccountEntity(
    @PrimaryKey
    override val id: String = "pan123",
    val accessToken: String = "",
    val account: String = "",
    override val nickname: String = "",
    override val updatedAt: Long = currentEpochMillis(),
    override val isPrimary: Boolean = false,
    override val remark: String = ""
) : MultiAccountEntity
