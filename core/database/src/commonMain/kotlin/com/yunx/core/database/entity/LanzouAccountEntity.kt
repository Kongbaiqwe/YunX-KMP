package com.yunx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunx.core.common.currentEpochMillis

@Entity(tableName = "lanzou_account")
data class LanzouAccountEntity(
    @PrimaryKey
    override val id: String = "lanzou",
    val cookie: String = "",
    override val nickname: String = "",
    override val updatedAt: Long = currentEpochMillis(),
    override val isPrimary: Boolean = false,
    override val remark: String = ""
) : MultiAccountEntity
