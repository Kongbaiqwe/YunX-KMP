package com.yunx.feature.parser.quark

/**
 * 夸克 Cookie 工具：合并/剥离 __puus、__pus。
 * __puus 约 3 小时过期，是下载直链签名校验的关键字段。
 */
object QuarkCookieUtil {
    private val TRACKED = setOf("__puus", "__pus")

    /** 把响应 Set-Cookie 列表里的最新 __puus/__pus 合并回原 Cookie 串 */
    fun mergeFromSetCookies(original: String, setCookies: List<String>): String {
        var cookie = original
        for (sc in setCookies) {
            val kv = sc.substringBefore(';').trim()
            val eq = kv.indexOf('=')
            if (eq <= 0) continue
            val name = kv.substring(0, eq)
            if (name in TRACKED) cookie = setOrReplace(cookie, name, kv.substring(eq + 1))
        }
        return cookie
    }

    /** 去掉 __puus，用于触发服务端重新下发 */
    fun withoutPuus(cookie: String): String =
        cookie.split(";").map { it.trim() }
            .filter { !it.startsWith("__puus=") }
            .joinToString("; ")

    private fun setOrReplace(cookie: String, name: String, value: String): String {
        val parts = cookie.split(";").map { it.trim() }.toMutableList()
        val idx = parts.indexOfFirst { it.startsWith("$name=") }
        val kv = "$name=$value"
        if (idx >= 0) parts[idx] = kv else parts.add(kv)
        return parts.joinToString("; ")
    }
}
