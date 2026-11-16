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

    val taskDao: DownloadTaskDao = database.downloadTaskDao()

    val downloadManager: DownloadManager = DownloadManager(
        dao = taskDao,
        engine = downloadEngine,
        saveDirProvider = { customDownloadDir ?: StoragePlatform.getDefaultDownloadDir() }
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
     * 支持纯直链 URL 以及从网盘分享链接提取出的目标直链
     */
    suspend fun resolveDownloadLink(input: String): DownloadLink? {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            // 如果是已识别的直链或包含参数的直链，直接提取出文件名
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
        val parsed = ShareLinkParser.parse(input) ?: return null
        return DownloadLink(
            fid = parsed.shareId,
            filename = "${parsed.platform.code}_${parsed.shareId}",
            downloadUrl = parsed.originalUrl,
            size = -1L
        )
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
