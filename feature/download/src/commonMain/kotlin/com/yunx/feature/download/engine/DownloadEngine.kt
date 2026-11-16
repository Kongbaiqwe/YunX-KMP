package com.yunx.feature.download.engine

import kotlinx.serialization.Serializable

/**
 * 下载引擎类型
 */
enum class EngineType {
    /** 内置基于 Range / HTTP 流式的分片下载器 */
    BUILTIN_CHUNK,
    /** Gopeed 进程内直调核心（支持 HTTP/HLS/BT 磁力多协议） */
    GOPEED,
    /** iOS URLSession / 系统后台下载调度（后台保活兜底） */
    URL_SESSION_BACKGROUND
}

/**
 * 引擎任务创建参数
 */
@Serializable
data class EngineTaskParams(
    val taskId: Long,
    val url: String,
    val saveDir: String,
    val fileName: String,
    val headers: Map<String, String> = emptyMap(),
    val connections: Int = 0,
    val totalSize: Long = -1L
)

/**
 * 引擎运行态快照
 */
@Serializable
data class EngineTaskStatus(
    val engineTaskId: String,
    /** "running", "paused", "completed", "error", "wait" */
    val status: String,
    val downloaded: Long,
    val total: Long,
    val speed: Long,
    val errorMessage: String? = null
)

/**
 * 跨平台下载引擎统一定义
 */
interface DownloadEngine {
    val type: EngineType

    suspend fun isAvailable(): Boolean
    suspend fun startEngine(): Boolean
    suspend fun stopEngine(): Boolean

    suspend fun createTask(params: EngineTaskParams): String
    suspend fun pauseTask(engineTaskId: String): Boolean
    suspend fun resumeTask(engineTaskId: String): Boolean
    suspend fun deleteTask(engineTaskId: String): Boolean
    suspend fun getTaskStatus(engineTaskId: String): EngineTaskStatus?
}
