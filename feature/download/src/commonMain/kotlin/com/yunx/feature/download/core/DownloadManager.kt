package com.yunx.feature.download.core

import com.yunx.core.common.PlatformLock
import com.yunx.core.common.currentEpochMillis
import com.yunx.core.database.dao.DownloadTaskDao
import com.yunx.core.database.entity.DownloadTaskEntity
import com.yunx.core.database.entity.DownloadTaskEntity.Companion.STATUS_COMPLETED
import com.yunx.core.database.entity.DownloadTaskEntity.Companion.STATUS_DOWNLOADING
import com.yunx.core.database.entity.DownloadTaskEntity.Companion.STATUS_FAILED
import com.yunx.core.database.entity.DownloadTaskEntity.Companion.STATUS_PAUSED
import com.yunx.core.database.entity.DownloadTaskEntity.Companion.STATUS_PENDING
import com.yunx.feature.download.engine.DownloadEngine
import com.yunx.feature.download.engine.EngineTaskParams
import com.yunx.feature.download.model.ChunkDebug
import com.yunx.feature.download.model.DownloadPlatform
import com.yunx.feature.download.model.DownloadResourceDebug
import com.yunx.feature.download.policy.DownloadPathPolicy
import com.yunx.feature.download.storage.StoragePlatform
import com.yunx.feature.download.util.DownloadFileNames
import com.yunx.feature.download.util.MagnetLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 跨平台下载调度管理器（纯 KMP 核心）
 */
class DownloadManager(
    private val dao: DownloadTaskDao,
    private val downloader: ChunkDownloader = ChunkDownloader(),
    private val hlsDownloader: HlsDownloader = HlsDownloader(),
    private val engine: DownloadEngine? = null,
    private val threadProvider: (String) -> Int = { 32 },
    private val saveDirProvider: () -> String? = { null },
    private val concurrencyProvider: () -> Int = { 3 },
    private val speedLimitProvider: () -> Long = { 0L },
    private val retryCountProvider: () -> Int = { 3 },
    private val engineProvider: (() -> DownloadEngine?)? = null
) {
    companion object {
        const val BUFFER_SIZE = 64 * 1024
        private const val BUFFER_BUDGET_DIVISOR = 8
        private const val INFLIGHT_MIN = 8
        private const val INFLIGHT_MAX = 512

        fun inflightChunksFor(maxHeapBytes: Long, bufferSize: Int = BUFFER_SIZE): Int =
            (maxHeapBytes / BUFFER_BUDGET_DIVISOR / bufferSize.coerceAtLeast(1))
                .toInt()
                .coerceIn(INFLIGHT_MIN, INFLIGHT_MAX)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _activeTaskCount = MutableStateFlow(0)
    val activeTaskCount: StateFlow<Int> = _activeTaskCount.asStateFlow()

    private val activeJobs = mutableMapOf<Long, CompletableDeferred<Job>>()
    private val jobsLock = PlatformLock()

    private val taskEngineIds = mutableMapOf<Long, String>()
    private val taskEngines = mutableMapOf<Long, DownloadEngine>()
    private val engineLock = PlatformLock()
    private val inflightLimiter = Semaphore(INFLIGHT_MAX)

    fun resourceDebugSnapshot(): DownloadResourceDebug {
        return DownloadResourceDebug(
            inflightChunks = INFLIGHT_MAX - inflightLimiter.availablePermits,
            maxInflightChunks = INFLIGHT_MAX,
            activeTaskCount = _activeTaskCount.value,
            speedLimitBytesPerSec = speedLimitProvider()
        )
    }

    fun chunkDebugSnapshot(taskId: Long): List<ChunkDebug> {
        return emptyList()
    }

    suspend fun enqueue(
        url: String,
        platform: String,
        headers: Map<String, String> = emptyMap(),
        fileName: String = "",
        totalSize: Long = -1L,
        autoStart: Boolean = true
    ): Long {
        val safeFileName = DownloadFileNames.normalize(
            if (fileName.isNotBlank()) fileName
            else if (MagnetLink.isMagnet(url)) MagnetLink.displayName(url)
            else "download_${currentEpochMillis()}"
        )
        val defaultDir = saveDirProvider() ?: StoragePlatform.getDefaultDownloadDir()
        val pathPolicy = DownloadPathPolicy.sanitize(safeFileName, safeFileName)
        val finalFileName = pathPolicy?.fileName ?: safeFileName
        val finalDir = if (pathPolicy != null && pathPolicy.relativeDirectory.isNotBlank()) {
            "$defaultDir/${pathPolicy.relativeDirectory}"
        } else defaultDir

        val entity = DownloadTaskEntity(
            url = url,
            platform = platform,
            fileName = finalFileName,
            savePath = "$finalDir/$finalFileName",
            totalSize = totalSize,
            downloadedSize = 0L,
            status = STATUS_PENDING,
            createTime = currentEpochMillis()
        )
        val id = dao.insert(entity)
        if (autoStart) {
            start(id, headers)
        }
        return id
    }

    fun start(id: Long, headers: Map<String, String> = emptyMap()) {
        val deferred = CompletableDeferred<Job>()
        val shouldStart = jobsLock.withLock {
            if (activeJobs.containsKey(id)) {
                false
            } else {
                activeJobs[id] = deferred
                true
            }
        }
        if (!shouldStart) return

        val job = scope.launch {
            var slotAcquired = false
            try {
                acquireTaskSlot()
                slotAcquired = true
                runTask(id, headers)
            } finally {
                jobsLock.withLock {
                    activeJobs.remove(id)
                }
                if (slotAcquired) {
                    jobsLock.withLock {
                        _activeTaskCount.value = max(0, _activeTaskCount.value - 1)
                    }
                }
            }
        }
        deferred.complete(job)
    }

    private suspend fun acquireTaskSlot() {
        while (true) {
            val acquired = jobsLock.withLock {
                if (_activeTaskCount.value < concurrencyProvider().coerceIn(1, 10)) {
                    _activeTaskCount.value += 1
                    true
                } else {
                    false
                }
            }
            if (acquired) return
            delay(200)
        }
    }

    private suspend fun runTask(id: Long, headers: Map<String, String>) = withContext(Dispatchers.IO) {
        val task = dao.get(id) ?: return@withContext
        if (task.status == STATUS_PAUSED) return@withContext
        val updated = dao.updateStatusIfNot(id, STATUS_DOWNLOADING, STATUS_PAUSED)
        println("[DownloadManager] taskId=$id updateStatusIfNot returned $updated")
        if (updated == 0) return@withContext

        val parentDir = task.savePath.substringBeforeLast('/', "")
        if (parentDir.isNotEmpty()) {
            StoragePlatform.mkdirs(parentDir)
        }

        try {
            // 磁力链接（BT）：直接走 DownloadEngine（Gopeed）
            if (MagnetLink.isMagnet(task.url) || task.platform == DownloadPlatform.MAGNET) {
                val selectedEngine = if (engineProvider != null) engineProvider.invoke() else engine
                if (selectedEngine != null && selectedEngine.isAvailable()) {
                    val engineId = selectedEngine.createTask(
                        EngineTaskParams(
                            taskId = id,
                            url = task.url,
                            saveDir = StoragePlatform.getDefaultDownloadDir(),
                            fileName = task.fileName,
                            headers = headers
                        )
                    )
                    taskEngineIds[id] = engineId
                    engineLock.withLock {
                        taskEngines[id] = selectedEngine
                    }
                    dao.updateEngineTaskId(id, engineId)
                    return@withContext
                }
            }

            // HLS 下载 (.m3u8)
            if (task.url.contains(".m3u8", ignoreCase = true)) {
                val ok = hlsDownloader.download(task.url, headers, task.savePath) { bytes ->
                    val curr = dao.get(id)
                    if (curr != null) {
                        dao.updateProgress(id, STATUS_DOWNLOADING, curr.downloadedSize + bytes, curr.totalSize)
                    }
                }
                if (ok) {
                    val finalLen = StoragePlatform.fileLength(task.savePath)
                    dao.updateProgress(id, STATUS_COMPLETED, finalLen, finalLen)
                    dao.complete(id, STATUS_COMPLETED, task.savePath)
                } else {
                    dao.updateStatus(id, STATUS_FAILED)
                }
                return@withContext
            }

            // 普通文件分片下载
            val total = if (task.totalSize > 0) task.totalSize
            else downloader.getTotalSize(task.url, headers) ?: -1L

            if (total <= 0) {
                // 回退单流整文件
                val ok = downloader.downloadFull(task.url, task.savePath, headers, total) { bytes ->
                    val curr = dao.get(id)
                    if (curr != null) {
                        dao.updateProgress(id, STATUS_DOWNLOADING, curr.downloadedSize + bytes, total)
                    }
                }
                if (ok) {
                    val len = StoragePlatform.fileLength(task.savePath)
                    dao.updateProgress(id, STATUS_COMPLETED, len, len)
                    dao.complete(id, STATUS_COMPLETED, task.savePath)
                } else {
                    dao.updateStatus(id, STATUS_FAILED)
                }
            } else {
                // 分片下载（支持断点续传）
                val partFile = "${task.savePath}.part0"
                val existingBytes = StoragePlatform.fileLength(partFile)
                val initialDownloaded = if (existingBytes > 0) existingBytes else task.downloadedSize
                dao.updateProgress(id, STATUS_DOWNLOADING, initialDownloaded, total)

                val res = inflightLimiter.withPermit {
                    downloader.downloadChunk(id, task.url, 0L, total - 1, partFile, headers) { bytes ->
                        val curr = dao.get(id)
                        if (curr != null) {
                            dao.updateProgress(id, STATUS_DOWNLOADING, curr.downloadedSize + bytes, total)
                        }
                    }
                }
                if (res == ChunkResult.OK) {
                    downloader.mergeParts(listOf(partFile), task.savePath)
                    val len = StoragePlatform.fileLength(task.savePath)
                    dao.updateProgress(id, STATUS_COMPLETED, len, len)
                    dao.complete(id, STATUS_COMPLETED, task.savePath)
                } else if (res == ChunkResult.RANGE_IGNORED) {
                    // 回退单流
                    val ok = downloader.downloadFull(task.url, task.savePath, headers, total) { bytes ->
                        val curr = dao.get(id)
                        if (curr != null) {
                            dao.updateProgress(id, STATUS_DOWNLOADING, curr.downloadedSize + bytes, total)
                        }
                    }
                    if (ok) {
                        dao.complete(id, STATUS_COMPLETED, task.savePath)
                    } else {
                        dao.updateStatus(id, STATUS_FAILED)
                    }
                } else {
                    dao.updateStatus(id, STATUS_FAILED)
                }
            }
        } catch (e: Throwable) {
            println("[-] [DownloadManager] task $id execution failed: ${e.message}")
            e.printStackTrace()
            dao.updateStatus(id, STATUS_FAILED)
        }
    }

    suspend fun pause(id: Long) {
        val deferred = jobsLock.withLock {
            activeJobs.remove(id)
        }
        deferred?.cancel()
        val engineId = taskEngineIds.remove(id)
        val taskEngine = engineLock.withLock { taskEngines[id] }
        val selectedEngine = taskEngine ?: engine
        if (engineId != null && selectedEngine != null) {
            selectedEngine.pauseTask(engineId)
        }
        dao.updateStatus(id, STATUS_PAUSED)
    }

    suspend fun resume(id: Long, headers: Map<String, String> = emptyMap()) {
        dao.updateStatus(id, STATUS_DOWNLOADING)
        val engineId = taskEngineIds[id]
        val taskEngine = engineLock.withLock { taskEngines[id] }
        val selectedEngine = taskEngine ?: engine
        if (engineId != null && selectedEngine != null) {
            selectedEngine.resumeTask(engineId)
        } else {
            start(id, headers)
        }
    }

    suspend fun remove(id: Long, deleteLocal: Boolean = false) {
        pause(id)
        engineLock.withLock {
            taskEngines.remove(id)
        }
        val task = dao.get(id)
        if (deleteLocal && task != null) {
            StoragePlatform.deleteFile(task.savePath)
            StoragePlatform.deleteFile("${task.savePath}.part0")
        }
        dao.delete(id)
    }

    suspend fun redownload(id: Long): Boolean {
        pause(id)
        val task = dao.get(id) ?: return false
        StoragePlatform.deleteFile(task.savePath)
        StoragePlatform.deleteFile("${task.savePath}.part0")
        dao.updateProgress(id, STATUS_PENDING, 0L, task.totalSize)
        start(id)
        return true
    }
}
