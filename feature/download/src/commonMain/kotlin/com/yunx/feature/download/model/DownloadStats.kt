package com.yunx.feature.download.model

import kotlinx.serialization.Serializable

/**
 * 下载统计快照
 */
@Serializable
data class DownloadStats(
    val taskId: Long,
    val speedBytesPerSec: Long = 0L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val activeChunks: Int = 0,
    val state: String = "IDLE"
)

/**
 * 分片调试信息
 */
@Serializable
data class ChunkDebug(
    val key: String,
    val start: Long,
    val end: Long,
    val downloaded: Long,
    val speed: Long = 0L,
    val status: String = "ACTIVE"
)

/**
 * 资源调试快照
 */
@Serializable
data class DownloadResourceDebug(
    val inflightChunks: Int,
    val maxInflightChunks: Int,
    val activeTaskCount: Int,
    val speedLimitBytesPerSec: Long
)
