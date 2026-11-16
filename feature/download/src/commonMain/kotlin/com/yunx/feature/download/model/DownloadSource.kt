package com.yunx.feature.download.model

import kotlinx.serialization.Serializable

/**
 * 一个下载源：直链 + 它专属的请求头。
 *
 * 单文件多源分片下，同一个文件的多条直链分属不同账号，
 * 请求头里的 Cookie 不能混用 —— 所以「直链 + 请求头」必须成对携带。
 */
@Serializable
data class DownloadSource(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)
