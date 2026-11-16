package com.yunx.core.network

data class ProxyConfig(
    val host: String,
    val port: Int
)

object GlobalNetworkSettings {
    var proxyConfig: ProxyConfig? = null
    var defaultUserAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 YunX/KMP"
}
