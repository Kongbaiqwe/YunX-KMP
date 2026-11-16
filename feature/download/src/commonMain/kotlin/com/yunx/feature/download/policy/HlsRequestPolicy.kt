package com.yunx.feature.download.policy

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom

/**
 * HLS 请求与跨源敏感头脱敏策略（纯 KMP 实现）
 */
object HlsRequestPolicy {
    private val sensitiveHeaders = setOf(
        "authorization", "cookie", "origin", "proxy-authorization", "referer"
    )

    fun initialUrl(url: String): Url? = runCatching {
        val parsed = Url(url)
        if (parsed.protocol.name.equals("https", ignoreCase = true)) parsed else null
    }.getOrNull()

    fun resolve(base: Url, candidate: String): Url? = runCatching {
        val builder = URLBuilder(base)
        builder.takeFrom(candidate)
        val resolved = builder.build()
        if (resolved.protocol.name.equals("https", ignoreCase = true)) resolved else null
    }.getOrNull()

    fun headersFor(
        target: Url,
        credentialOrigin: Url,
        headers: Map<String, String>
    ): Map<String, String> {
        if (sameOrigin(target, credentialOrigin)) return headers
        return headers.filterKeys { it.lowercase() !in sensitiveHeaders }
    }

    fun sameOrigin(left: Url, right: Url): Boolean =
        left.protocol.name.equals(right.protocol.name, ignoreCase = true) &&
                left.host.equals(right.host, ignoreCase = true) &&
                left.port == right.port
}
