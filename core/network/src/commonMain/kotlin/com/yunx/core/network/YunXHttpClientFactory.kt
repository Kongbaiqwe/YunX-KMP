package com.yunx.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

object YunXHttpClientFactory {

    fun createHttpClient(
        engine: HttpClientEngine = createPlatformEngine(),
        defaultUserAgent: String = GlobalNetworkSettings.defaultUserAgent,
        requestTimeoutMillis: Long = 30_000,
        connectTimeoutMillis: Long = 15_000,
        socketTimeoutMillis: Long = 30_000,
        enableRetry: Boolean = true
    ): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(YunXJson)
            }

            install(HttpTimeout) {
                this.requestTimeoutMillis = requestTimeoutMillis
                this.connectTimeoutMillis = connectTimeoutMillis
                this.socketTimeoutMillis = socketTimeoutMillis
            }

            if (enableRetry) {
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 2)
                    exponentialDelay()
                }
            }

            defaultRequest {
                if (headers[HttpHeaders.UserAgent] == null) {
                    header(HttpHeaders.UserAgent, defaultUserAgent)
                }
            }
        }
    }
}
