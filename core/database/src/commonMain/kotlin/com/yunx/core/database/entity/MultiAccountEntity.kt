package com.yunx.core.database.entity

import com.yunx.core.common.currentEpochMillis

/**
 * 多账号：各平台账号表的凭证字段各不相同（cookie / access token / 设备号…），
 * 但「账号目录」公共操作只关心以下公共字段。
 */
interface MultiAccountEntity {
    /** 账号行主键：老数据沿用平台常量（如 "quark"），新增账号为 "<platform>-<时间戳36进制>" */
    val id: String

    /** 平台侧昵称（可能为空串） */
    val nickname: String

    /** 用户自定义备注，空串 = 界面回退显示昵称 */
    val remark: String

    /** 该平台的主账号标记 */
    val isPrimary: Boolean

    val updatedAt: Long
}

/** 新账号主键：<平台>-<时间戳36进制> */
fun newAccountId(platform: String): String =
    "$platform-${currentEpochMillis().toString(36)}"

/** resolveAccountSlot 的结果：本次登录要写入的账号行（主键 / 是否主账号 / 备注） */
data class AccountSlot(val id: String, val isPrimary: Boolean, val remark: String)

/**
 * 一次登录应该落到哪一行。
 */
fun resolveAccountSlot(
    platform: String,
    existing: MultiAccountEntity?,
    newAccount: Boolean
): AccountSlot = when {
    existing == null -> AccountSlot(platform, isPrimary = true, remark = "")
    newAccount -> AccountSlot(newAccountId(platform), isPrimary = false, remark = "")
    else -> AccountSlot(existing.id, isPrimary = existing.isPrimary, remark = existing.remark)
}
