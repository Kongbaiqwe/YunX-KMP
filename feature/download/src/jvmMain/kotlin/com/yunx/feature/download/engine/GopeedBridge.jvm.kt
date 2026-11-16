package com.yunx.feature.download.engine

import java.util.concurrent.atomic.AtomicBoolean

actual object GopeedBridge {
    private val loaded = AtomicBoolean(false)
    private var mockMode = false

    fun enableMockMode(enabled: Boolean) {
        mockMode = enabled
        if (enabled) loaded.set(true)
    }

    actual fun isLibraryLoaded(): Boolean = loaded.get()

    actual fun start(configJson: String): Int {
        if (mockMode) return 9999
        return 0
    }

    actual fun stop() {
    }

    actual fun invokeAsync(
        method: String,
        path: String,
        query: String,
        body: String,
        requestId: Long,
        callback: (reqId: Long, success: Boolean, payload: String?) -> Unit
    ) {
        if (mockMode) {
            when {
                path.endsWith("/status") -> {
                    val statusJson = """{"code":0,"msg":"ok","data":{"id":"$requestId","status":"running","downloaded":1024,"total":2048,"speed":512}}"""
                    callback(requestId, true, statusJson)
                }
                method == "POST" && path == "/api/v1/tasks" -> {
                    val resp = """{"code":0,"msg":"ok","data":{"id":"task-$requestId"}}"""
                    callback(requestId, true, resp)
                }
                path == "/api/v1/info" -> {
                    val resp = """{"code":0,"msg":"ok","data":{"version":"v1.9.4"}}"""
                    callback(requestId, true, resp)
                }
                else -> {
                    val resp = """{"code":0,"msg":"ok","data":{}}"""
                    callback(requestId, true, resp)
                }
            }
            return
        }
        callback(requestId, false, "Gopeed native library not loaded in JVM environment")
    }
}
