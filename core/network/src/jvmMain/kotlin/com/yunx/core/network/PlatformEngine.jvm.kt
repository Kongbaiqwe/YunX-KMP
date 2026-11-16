package com.yunx.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformEngine(): HttpClientEngine = OkHttp.create {
    config {
        retryOnConnectionFailure(true)
    }
}
