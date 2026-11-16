package com.yunx.feature.download.engine

actual object GopeedBridge {
    private var callbackHandler: ((String, String, String, String, Long, (Long, Boolean, String?) -> Unit) -> Unit)? = null
    private var isLoaded: Boolean = false

    fun registerNativeInvoker(
        invoker: (String, String, String, String, Long, (Long, Boolean, String?) -> Unit) -> Unit
    ) {
        callbackHandler = invoker
        isLoaded = true
    }

    actual fun isLibraryLoaded(): Boolean = isLoaded

    actual fun start(configJson: String): Int {
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
        val handler = callbackHandler
        if (handler != null) {
            handler(method, path, query, body, requestId, callback)
        } else {
            callback(requestId, false, "Gopeed framework not registered in iOS runtime")
        }
    }
}
