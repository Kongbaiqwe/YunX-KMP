package com.yunx.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yunx.core.common.currentEpochMillis

@Entity(tableName = "c139_account")
data class C139AccountEntity(
    @PrimaryKey
    override val id: String = "c139",
    val cookie: String = "",
    override val nickname: String = "",
    val authorization: String = "",
    override val updatedAt: Long = currentEpochMillis(),
    override val isPrimary: Boolean = false,
    override val remark: String = ""
) : MultiAccountEntity
