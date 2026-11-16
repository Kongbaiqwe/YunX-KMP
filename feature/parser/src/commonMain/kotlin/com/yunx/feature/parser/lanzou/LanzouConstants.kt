package com.yunx.feature.parser.lanzou

import kotlin.random.Random

object LanzouConstants {
    const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val MYDISK_URL = "https://pc.woozooo.com/mydisk.php"
    const val DOUPLOAD_URL = "https://pc.woozooo.com/doupload.php"
    const val DOWN_IP_COOKIE = "down_ip=1"

    private val HOST_REGEX =
        Regex(
            """^(?:[a-z0-9-]+\.)*(?:lanzou[a-z]?|lan[zs]o[ux]|lanzn)\.(?:com|net|org|cn)$""",
            RegexOption.IGNORE_CASE
        )

    fun isLanzouHost(host: String): Boolean = HOST_REGEX.matches(host)

    fun normalizeShareUrl(url: String): String {
        var u = url.trim()
        u = u.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        u = u.substringBefore('#')
        return u.trimEnd('/')
    }
}

object ILanzouConstants {
    const val API_BASE = "https://apis.ilanzou.com"
    const val SHARE_API_BASE = "https://apix.ilanzou.com"
    const val WEB_SITE = "https://www.ilanzou.com"
    const val DOWNLOAD_REFERER = "$WEB_SITE/"

    const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val SHARE_LIST = "$SHARE_API_BASE/unproved/share/list"
    const val SHARE_REDIRECT = "$SHARE_API_BASE/unproved/file/redirect"
    const val SHARE_REFERER_PREFIX = "$WEB_SITE/s/"
    const val SHARE_DEV_MODEL = "Chrome"
    const val DEV_TYPE = "6"
    const val DEV_VERSION = "125"

    fun newUuid(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(16) { repeat(16) { append(chars[Random.nextInt(chars.length)]) } }
    }
}
