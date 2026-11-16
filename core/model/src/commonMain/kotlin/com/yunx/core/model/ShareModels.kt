package com.yunx.core.model

import kotlinx.serialization.Serializable

/**
 * 分享解析会话（一次解析流程的凭证）。
 */
@Serializable
data class ShareSession(
    val shareId: String,
    val stoken: String,
    val title: String
)

/**
 * 分享 Token 响应。
 */
@Serializable
data class ShareToken(
    val stoken: String,
    val title: String,
    val firstFid: String
)

/**
 * 文件/目录项。
 */
@Serializable
data class ShareFile(
    val fid: String,
    val fname: String,
    val fsize: Long,
    val isdir: Boolean,
    val pdirFid: String = "",
    val fidToken: String = "",
    val modifyTime: String = "",
    val thumbnail: FileThumbnail? = null
)

/**
 * 列表行缩略图：图片地址 + 取图所需的请求头。
 */
@Serializable
data class FileThumbnail(
    val url: String,
    val headers: Map<String, String> = emptyMap()
) {
    companion object {
        fun of(url: String, headers: Map<String, String> = emptyMap()): FileThumbnail? {
            val trimmed = url.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return null
            return FileThumbnail(trimmed, headers)
        }

        fun of(
            url: String,
            headers: Map<String, String>,
            authHost: String,
            authHeaders: Map<String, String>
        ): FileThumbnail? {
            val trimmed = url.trim()
            val host = extractHost(trimmed)
            val picked = if (host.equals(authHost, ignoreCase = true)) authHeaders else headers
            return of(trimmed, picked)
        }

        private fun extractHost(url: String): String {
            val withoutScheme = if (url.contains("://")) url.substringAfter("://") else url
            return withoutScheme.substringBefore('/').substringBefore(':').substringBefore('?')
        }
    }
}

/**
 * 分享信息。
 */
@Serializable
data class ShareInfo(
    val shareUrl: String,
    val passcode: String,
    val pwdId: String,
    val title: String,
    val expiredType: Int,
    val warning: String? = null
)

/**
 * 网盘空间详情（总容量 / 已用，单位字节）。
 */
@Serializable
data class QuotaInfo(
    val used: Long,
    val total: Long,
    val usedInTrash: Long = 0L
)

/**
 * 下载直链。
 */
@Serializable
data class DownloadLink(
    val fid: String,
    val filename: String,
    val downloadUrl: String,
    val size: Long,
    val cleanupDirFid: String? = null,
    val isHls: Boolean = false,
    val guestCookie: String = ""
)

/**
 * UC 转码播放流
 */
@Serializable
data class PlayLink(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)
