package com.yunx.core.model

import kotlinx.serialization.Serializable

/**
 * 网盘与云存储平台枚举（解耦纯净版，不包含任何 Android StringRes / UI 依赖）。
 */
@Serializable
enum class SharePlatform(val code: String, val defaultName: String) {
    QUARK("quark", "夸克网盘"),
    UC("uc", "UC网盘"),
    XUNLEI("xunlei", "迅雷云盘"),
    BAIDU("baidu", "百度网盘"),
    C139("c139", "中国移动云盘"),
    PAN123("pan123", "123云盘"),
    PAN115("pan115", "115生活"),
    GUANGYA("guangya", "光涯云盘"),
    ILANZOU("ilanzou", "i蓝奏"),
    LANZOU("lanzou", "蓝奏云"),
    GITHUB("github", "GitHub");

    companion object {
        fun fromCode(code: String): SharePlatform? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

@Serializable
data class ParsedShare(
    val shareId: String,
    val pwd: String? = null,
    val platform: SharePlatform,
    val originalUrl: String = ""
)
