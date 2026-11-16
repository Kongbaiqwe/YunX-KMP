package com.yunx.feature.download.engine

class UrlSessionDownloadEngine : DownloadEngine {
    override val type: EngineType = EngineType.URL_SESSION_BACKGROUND

    private val tasks = mutableMapOf<String, EngineTaskStatus>()
    private var counter = 0L

    override suspend fun isAvailable(): Boolean = true

    override suspend fun startEngine(): Boolean = true

    override suspend fun stopEngine(): Boolean = true

    override suspend fun createTask(params: EngineTaskParams): String {
        val id = "urlsession-${++counter}"
        tasks[id] = EngineTaskStatus(
            engineTaskId = id,
            status = "wait",
            downloaded = 0L,
            total = params.totalSize,
            speed = 0L
        )
        return id
    }

    override suspend fun pauseTask(engineTaskId: String): Boolean {
        val curr = tasks[engineTaskId] ?: return false
        tasks[engineTaskId] = curr.copy(status = "paused")
        return true
    }

    override suspend fun resumeTask(engineTaskId: String): Boolean {
        val curr = tasks[engineTaskId] ?: return false
        tasks[engineTaskId] = curr.copy(status = "running")
        return true
    }

    override suspend fun deleteTask(engineTaskId: String): Boolean {
        return tasks.remove(engineTaskId) != null
    }

    override suspend fun getTaskStatus(engineTaskId: String): EngineTaskStatus? {
        return tasks[engineTaskId]
    }
}
