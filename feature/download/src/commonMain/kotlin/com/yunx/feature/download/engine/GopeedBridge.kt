package com.yunx.feature.download.engine

/**
 * Gopeed 跨平台原生调用门面
 */
expect object GopeedBridge {
    fun isLibraryLoaded(): Boolean
    fun start(configJson: String): Int
    fun stop()
    fun invokeAsync(
        method: String,
        path: String,
        query: String,
        body: String,
        requestId: Long,
        callback: (reqId: Long, success: Boolean, payload: String?) -> Unit
    )
}
