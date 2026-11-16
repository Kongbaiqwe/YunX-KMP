package com.yunx.feature.download.util

import io.ktor.http.decodeURLQueryComponent

/**
 * 磁力链接（magnet:?xt=urn:btih:...）的识别与信息提取（纯 KMP 零外部依赖实现）。
 */
object MagnetLink {

    private const val SCHEME = "magnet:"

    /** 磁力的 info hash：xt=urn:btih:<40 位 hex 或 32 位 base32> */
    private val BTIH = Regex("(?i)xt=urn:btih:([A-Za-z0-9]+)")

    /** dn 里的换行/制表符：显示名要进通知栏与列表，先压成空格 */
    private val BLANK_CHARS = Regex("""[\r\n\t]+""")

    /** 显示名上限 */
    private const val MAX_NAME_CHARS = 80

    /** 是不是磁力链接（大小写不敏感，允许前后空白；只有 magnet: 光杆不算） */
    fun isMagnet(url: String): Boolean {
        val text = url.trim()
        return text.length > SCHEME.length && text.regionMatches(0, SCHEME, 0, SCHEME.length, ignoreCase = true)
    }

    /**
     * 任务的显示名：优先 dn=（磁力自带的显示名），没有就用 info hash 前 8 位占位。
     */
    fun displayName(url: String): String {
        val dn = param(url, "dn").replace(BLANK_CHARS, " ").trim()
        if (dn.isNotEmpty()) return dn.take(MAX_NAME_CHARS)
        val prefix = placeholderHash(url)
        return if (prefix.isNotEmpty()) "Magnet ($prefix)" else "Magnet Task"
    }

    /**
     * 没有 dn 时的占位依据：info hash 前 8 位，取不到返回空串。
     */
    fun placeholderHash(url: String): String =
        infoHash(url).takeIf { it.length >= 8 }?.take(8).orEmpty()

    /** info hash（统一大写，取不到返回空串） */
    fun infoHash(url: String): String =
        BTIH.find(url)?.groupValues?.get(1)?.uppercase().orEmpty()

    /** 取第一个 key=value 参数的值 */
    private fun param(url: String, key: String): String {
        val query = url.substringAfter('?', "")
        if (query.isBlank()) return ""
        for (part in query.split('&')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            if (!part.substring(0, eq).equals(key, ignoreCase = true)) continue
            return decode(part.substring(eq + 1)).trim()
        }
        return ""
    }

    private fun decode(value: String): String = runCatching {
        value.decodeURLQueryComponent(plusIsSpace = false)
    }.getOrDefault(value)
}
