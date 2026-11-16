package com.yunx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunx.core.common.currentEpochMillis

@Entity(tableName = "xunlei_account")
data class XunleiAccountEntity(
    @PrimaryKey
    override val id: String = "xunlei",
    val accessToken: String = "",
    val refreshToken: String = "",
    val deviceId: String = "",
    val captchaToken: String = "",
    override val nickname: String = "",
    val authType: String = "",
    override val updatedAt: Long = currentEpochMillis(),
    override val isPrimary: Boolean = false,
    override val remark: String = ""
) : MultiAccountEntity
