package com.yunx.feature.download.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class GopeedDownloadEngine(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : DownloadEngine {
    override val type: EngineType = EngineType.GOPEED

    private var reqCounter = 0L

    override suspend fun isAvailable(): Boolean = GopeedBridge.isLibraryLoaded()

    override suspend fun startEngine(): Boolean {
        return try {
            GopeedBridge.start("{}")
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun stopEngine(): Boolean {
        return try {
            GopeedBridge.stop()
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun createTask(params: EngineTaskParams): String {
        val reqId = ++reqCounter
        val body = buildString {
            append("""{"req":{"url":"${params.url}"""")
            if (params.headers.isNotEmpty()) {
                val headerItems = params.headers.entries.joinToString(",") { (k, v) ->
                    """"${k}":"${v.replace("\"", "\\\"")}""""
                }
                append(""", "extra":{"header":{$headerItems}}""")
            }
            append("""},"opt":{"path":"${params.saveDir.replace("\\", "/")}"""")
            if (params.fileName.isNotBlank()) {
                append(""", "name":"${params.fileName}"""")
            }
            if (params.connections > 0) {
                append(""", "extra":{"connections":${params.connections}}""")
            }
            append("""}}""")
        }

        val deferred = CompletableDeferred<Pair<Boolean, String?>>()
        GopeedBridge.invokeAsync("POST", "/api/v1/tasks", "", body, reqId) { id, ok, payload ->
            deferred.complete(ok to payload)
        }

        val result = withTimeoutOrNull(30_000) { deferred.await() }
            ?: error("Gopeed createTask timeout")
        if (!result.first || result.second == null) {
            error("Gopeed createTask failed: ${result.second}")
        }

        val root = json.parseToJsonElement(result.second!!).jsonObject
        val data = root["data"]?.jsonObject ?: error("No data in response")
        return data["id"]?.jsonPrimitive?.content ?: error("No taskId in response")
    }

    override suspend fun pauseTask(engineTaskId: String): Boolean {
        val reqId = ++reqCounter
        val deferred = CompletableDeferred<Pair<Boolean, String?>>()
        GopeedBridge.invokeAsync("PUT", "/api/v1/tasks/$engineTaskId/pause", "", "", reqId) { _, ok, payload ->
            deferred.complete(ok to payload)
        }
        val result = withTimeoutOrNull(10_000) { deferred.await() } ?: return false
        return result.first
    }

    override suspend fun resumeTask(engineTaskId: String): Boolean {
        val reqId = ++reqCounter
        val deferred = CompletableDeferred<Pair<Boolean, String?>>()
        GopeedBridge.invokeAsync("PUT", "/api/v1/tasks/$engineTaskId/continue", "", "", reqId) { _, ok, payload ->
            deferred.complete(ok to payload)
        }
        val result = withTimeoutOrNull(10_000) { deferred.await() } ?: return false
        return result.first
    }

    override suspend fun deleteTask(engineTaskId: String): Boolean {
        val reqId = ++reqCounter
        val deferred = CompletableDeferred<Pair<Boolean, String?>>()
        GopeedBridge.invokeAsync("DELETE", "/api/v1/tasks/$engineTaskId", "", "", reqId) { _, ok, payload ->
            deferred.complete(ok to payload)
        }
        val result = withTimeoutOrNull(10_000) { deferred.await() } ?: return false
        return result.first
    }

    override suspend fun getTaskStatus(engineTaskId: String): EngineTaskStatus? {
        val reqId = ++reqCounter
        val deferred = CompletableDeferred<Pair<Boolean, String?>>()
        GopeedBridge.invokeAsync("GET", "/api/v1/tasks/$engineTaskId/status", "", "", reqId) { _, ok, payload ->
            deferred.complete(ok to payload)
        }
        val result = withTimeoutOrNull(5_000) { deferred.await() } ?: return null
        if (!result.first || result.second == null) return null

        return try {
            val root = json.parseToJsonElement(result.second!!).jsonObject
            val data = root["data"]?.jsonObject ?: return null
            EngineTaskStatus(
                engineTaskId = engineTaskId,
                status = data["status"]?.jsonPrimitive?.content ?: "unknown",
                downloaded = data["downloaded"]?.jsonPrimitive?.longOrNull ?: 0L,
                total = data["total"]?.jsonPrimitive?.longOrNull ?: 0L,
                speed = data["speed"]?.jsonPrimitive?.longOrNull ?: 0L
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun getEngineInfo(): String? {
        val reqId = ++reqCounter
        val deferred = CompletableDeferred<Pair<Boolean, String?>>()
        GopeedBridge.invokeAsync("GET", "/api/v1/info", "", "", reqId) { _, ok, payload ->
            deferred.complete(ok to payload)
        }
        val result = withTimeoutOrNull(5_000) { deferred.await() } ?: return null
        return if (result.first) result.second else null
    }
}
