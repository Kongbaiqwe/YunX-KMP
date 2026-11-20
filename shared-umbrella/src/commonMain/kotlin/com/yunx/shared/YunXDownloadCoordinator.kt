package com.yunx.shared

import com.yunx.core.common.PlatformLock
import com.yunx.core.database.AppDatabase
import kotlin.concurrent.Volatile
import com.yunx.core.database.dao.DownloadTaskDao
import com.yunx.core.database.entity.DownloadTaskEntity
import com.yunx.core.database.getAppDatabaseBuilder
import com.yunx.core.model.DownloadLink
import com.yunx.core.model.ParsedShare
import com.yunx.core.model.SharePlatform
import com.yunx.feature.download.core.DownloadManager
import com.yunx.feature.download.engine.DownloadEngine
import com.yunx.feature.download.engine.GopeedDownloadEngine
import com.yunx.feature.download.storage.StoragePlatform
import com.yunx.feature.download.util.DownloadFileNames
import com.yunx.feature.parser.ShareLinkParser
import com.yunx.feature.parser.lanzou.LanzouApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 端到端全链路协调器（KMP 统一门面）
 * 串联：分享链接解析 -> Room 任务持久化 -> 下载调度 (Gopeed / 内置分片) -> 响应式进度 Flow
 */
class YunXDownloadCoordinator(
    val database: AppDatabase = getAppDatabaseBuilder().build(),
    val downloadEngine: DownloadEngine = GopeedDownloadEngine(),
    val customDownloadDir: String? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settingsRepository = DownloadSettingsRepository()

    val taskDao: DownloadTaskDao = database.downloadTaskDao()

    val downloadManager: DownloadManager = DownloadManager(
        dao = taskDao,
        engine = downloadEngine,
        saveDirProvider = { customDownloadDir ?: StoragePlatform.getDefaultDownloadDir() },
        concurrencyProvider = { settingsRepository.maxConcurrentDownloads },
        engineProvider = {
            if (settingsRepository.downloadEngine == DownloadSettingsRepository.ENGINE_GOPEED) {
                downloadEngine
            } else {
                null
            }
        }
    )

    init {
        // App 启动或中断恢复时，将先前未完成的任务标为暂停
        scope.launch {
            taskDao.markInterruptedAsPaused()
        }
    }

    /**
     * 1. 解析分享链接文本
     */
    fun parseShareLink(input: String): ParsedShare? {
        return ShareLinkParser.parse(input)
    }

    /**
     * 2. 将输入解析为可直接下载的链接模型
     * 优先走真实网盘分享解析器（如蓝奏云等），兜底支持纯直链 URL
     */
    suspend fun resolveDownloadLink(input: String): DownloadLink? {
        val trimmed = input.trim()
        val parsed = ShareLinkParser.parse(trimmed)
        if (parsed != null) {
            // 真实网盘分享解析分发
            when (parsed.platform) {
                SharePlatform.LANZOU -> {
                    try {
                        println("[Coordinator] Starting LanzouApi.resolveDownloadLink for ${parsed.originalUrl} (pwd=${parsed.pwd.orEmpty()})")
                        val lanzouLink = LanzouApi().resolveDownloadLink(parsed.originalUrl, parsed.pwd.orEmpty())
                        if (lanzouLink != null) {
                            println("[Coordinator] LanzouApi resolved successfully: filename=${lanzouLink.filename}, url=${lanzouLink.downloadUrl}, size=${lanzouLink.size}")
                            return lanzouLink
                        } else {
                            println("[-] [Coordinator] LanzouApi returned null")
                        }
                    } catch (e: Throwable) {
                        println("[-] [Coordinator] LanzouApi failed with exception: ${e.message}")
                        e.printStackTrace()
                    }
                }
                else -> {
                    // 其他网盘暂未提供公开直链提取器时，返回结构化模型
                }
            }
            return DownloadLink(
                fid = parsed.shareId,
                filename = "${parsed.platform.code}_${parsed.shareId}",
                downloadUrl = parsed.originalUrl,
                size = -1L
            )
        }

        // 普通 HTTP/HTTPS 直链兜底处理
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val cleanUrl = trimmed.lines().firstOrNull { it.startsWith("http") } ?: trimmed
            val urlWithoutQuery = cleanUrl.substringBefore('?')
            val rawName = urlWithoutQuery.substringAfterLast('/').ifBlank { "download_file" }
            val fileName = DownloadFileNames.normalize(rawName)
            val isHls = cleanUrl.contains(".m3u8", ignoreCase = true)
            return DownloadLink(
                fid = "direct_${cleanUrl.hashCode()}",
                filename = fileName,
                downloadUrl = cleanUrl,
                size = -1L,
                isHls = isHls
            )
        }
        return null
    }

    /**
     * 3. 创建任务并启动下载
     * 写入 Room 数据库，调度 DownloadManager 启动
     */
    suspend fun createAndStartDownload(
        url: String,
        fileName: String = "",
        platform: String = "direct",
        totalSize: Long = -1L,
        headers: Map<String, String> = emptyMap()
    ): Long {
        return downloadManager.enqueue(
            url = url,
            platform = platform,
            headers = headers,
            fileName = fileName,
            totalSize = totalSize,
            autoStart = true
        )
    }

    /**
     * 4. 暂停任务（支持断点续传）
     */
    suspend fun pauseTask(taskId: Long) {
        downloadManager.pause(taskId)
    }

    /**
     * 5. 恢复任务（从断点继续下载）
     */
    suspend fun resumeTask(taskId: Long, headers: Map<String, String> = emptyMap()) {
        downloadManager.resume(taskId, headers)
    }

    /**
     * 6. 删除任务
     */
    suspend fun removeTask(taskId: Long, deleteLocalFile: Boolean = false) {
        downloadManager.remove(taskId, deleteLocalFile)
    }

    /**
     * 7. 重新下载
     */
    suspend fun redownloadTask(taskId: Long): Boolean {
        return downloadManager.redownload(taskId)
    }

    /**
     * 8. 响应式任务列表（Room 原生 Flow）
     */
    fun observeTasks(): Flow<List<DownloadTaskEntity>> {
        return taskDao.observeAll()
    }

    /**
     * 9. 获取单个任务
     */
    suspend fun getTask(taskId: Long): DownloadTaskEntity? {
        return taskDao.get(taskId)
    }

    /**
     * 10. 获取 Gopeed 引擎信息（替代任务 2 独立 POC 检查）
     */
    suspend fun getEngineInfo(): String {
        return if (downloadEngine is GopeedDownloadEngine) {
            downloadEngine.getEngineInfo() ?: "{\"status\":\"running\",\"engine\":\"gopeed\"}"
        } else {
            "{\"status\":\"running\",\"engine\":\"builtin\"}"
        }
    }

    fun getMaxConcurrentDownloads(): Int = settingsRepository.maxConcurrentDownloads

    fun setMaxConcurrentDownloads(value: Int) {
        settingsRepository.maxConcurrentDownloads = value
    }

    fun getDownloadEngine(): String = settingsRepository.downloadEngine

    fun setDownloadEngine(value: String) {
        settingsRepository.downloadEngine = value
    }

    fun getDownloadDirectory(): String =
        customDownloadDir ?: StoragePlatform.getDefaultDownloadDir()

    companion object {
        @Volatile
        private var instance: YunXDownloadCoordinator? = null
        private val lock = PlatformLock()

        fun shared(): YunXDownloadCoordinator {
            val existing = instance
            if (existing != null) return existing
            return lock.withLock {
                instance ?: YunXDownloadCoordinator().also { instance = it }
            }
        }

        fun initialize(
            database: AppDatabase = getAppDatabaseBuilder().build(),
            downloadEngine: DownloadEngine = GopeedDownloadEngine(),
            customDownloadDir: String? = null
        ): YunXDownloadCoordinator {
            val coord = YunXDownloadCoordinator(database, downloadEngine, customDownloadDir)
            instance = coord
            return coord
        }
    }
}
