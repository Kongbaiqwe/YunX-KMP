package com.yunx.feature.parser.lanzou

import com.yunx.core.model.DownloadLink
import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.core.network.YunXJson
import com.yunx.feature.parser.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

private const val PRIMARY_PROBE_TIMEOUT_MS = 10_000L
private const val MIRROR_PROBE_TIMEOUT_MS = 15_000L

private val MIRROR_BASE_URLS = listOf(
    "https://raw.githubusercontent.com/Kongbaiqwe/YunX-KMP/main/ci-mirror/",
    "https://cdn.jsdelivr.net/gh/Kongbaiqwe/YunX-KMP@main/ci-mirror/"
)

class LanzouApi(
    private val client: HttpClient = YunXHttpClientFactory.createHttpClient(),
    private val verifyDelayMillis: Long = 2_000L
) {
    /**
     * 按 host 记录的 Cookie。蓝奏下载节点会先下发 acw_tc/down_ip 等 Cookie，
     * 后续 ajax.php 校验必须带上同一份，否则只拿得到 ?SignError。
     */
    private val cookies = mutableMapOf<String, MutableMap<String, String>>()

    suspend fun resolveDownloadLink(
        shareUrl: String,
        password: String = ""
    ): DownloadLink? {
        val targetUrl = LanzouConstants.normalizeShareUrl(shareUrl)
        println("[LanzouApi] Requesting share page: $targetUrl")
        val html = fetchText(targetUrl, targetUrl) ?: return null
        println("[LanzouApi] Share page html length=${html.length}")

        val filename = extractFilename(html)
        val shareSize = parseShareSize(html)
        println("[LanzouApi] filename='$filename', shareSize=$shareSize")

        val sign = extractSign(html)
        println("[LanzouApi] Extracted sign: '$sign'")

        if (sign.isNotBlank()) {
            val ajaxmLink = resolveViaAjaxm(html, targetUrl, password, filename, shareSize)
            if (ajaxmLink != null) return ajaxmLink
            println("[-] [LanzouApi] ajaxm flow failed, trying iframe/ajaxfile flow")
        } else {
            println("[LanzouApi] No sign on top page, trying iframe/ajaxfile flow")
        }

        return resolveViaIframe(html, targetUrl, filename, shareSize)
    }

    // ---------- 旧版顶层 ajaxm.php 流程 ----------

    private suspend fun resolveViaAjaxm(
        html: String,
        targetUrl: String,
        password: String,
        filename: String,
        shareSize: Long
    ): DownloadLink? {
        val sign = extractSign(html)
        val ajaxPathMatch = Regex("""url\s*:\s*['"](/ajaxm\.php\?[^'"]+)['"]""").find(html)
            ?: Regex("""var\s+url\s*=\s*['"](/ajaxm\.php\?[^'"]+)['"]""").find(html)
            ?: Regex("""['"](/ajaxm\.php)['"]""").find(html)
        val ajaxPath = ajaxPathMatch?.groupValues?.get(1) ?: "/ajaxm.php"

        val domain = originOf(targetUrl)
        val ajaxUrl = if (ajaxPath.startsWith("http")) ajaxPath else "$domain$ajaxPath"
        println("[LanzouApi] POST ajaxUrl: $ajaxUrl (password length=${password.length})")

        val response = try {
            client.post(ajaxUrl) {
                header("User-Agent", LanzouConstants.WEB_UA)
                header("Referer", targetUrl)
                header("X-Requested-With", "XMLHttpRequest")
                val cookie = cookieHeader(ajaxUrl)
                if (cookie.isNotEmpty()) header("Cookie", cookie)
                setBody(FormDataContent(Parameters.build {
                    append("action", "downprocess")
                    append("sign", sign)
                    append("p", password)
                }))
            }
        } catch (e: Throwable) {
            println("[-] [LanzouApi] ajaxm POST failed: ${e.message}")
            return null
        }
        absorbCookies(ajaxUrl, response)

        val text = response.bodyAsText()
        println("[LanzouApi] Ajax response status=${response.status}, body preview: ${text.take(200)}")
        val json = decodeJson(text) ?: return null
        if (json["zt"].intOr(0) != 1) {
            println("[-] [LanzouApi] Ajaxm unsuccessful: zt=${json["zt"].intOr(0)}, inf=${json["inf"].strOrNull()}")
            return null
        }

        val dom = json["dom"].strOrNull().orEmpty()
        val url = json["url"].strOrNull().orEmpty()
        if (dom.isBlank() || url.isBlank()) return null
        val downloadUrl = if (url.startsWith("http")) url else "$dom/file/$url"
        val name = filename.ifBlank { json["inf"].strOrNull().orEmpty() }
        println("[+] [LanzouApi] ajaxm success! filename=$name, downloadUrl=$downloadUrl")

        return DownloadLink(
            fid = sign,
            filename = name,
            downloadUrl = downloadUrl,
            size = shareSize,
            guestCookie = LanzouConstants.DOWN_IP_COOKIE
        )
    }

    // ---------- 新版 iframe → ajaxfile.php 流程 ----------

    private suspend fun resolveViaIframe(
        shareHtml: String,
        targetUrl: String,
        filename: String,
        shareSize: Long
    ): DownloadLink? {
        val domain = originOf(targetUrl)
        val iframeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""").find(shareHtml)?.groupValues?.get(1)
        if (iframeSrc.isNullOrBlank()) {
            println("[-] [LanzouApi] No iframe found on share page")
            return null
        }
        val iframeUrl = absolutize(domain, iframeSrc)
        println("[LanzouApi] iframe page: $iframeUrl")
        val iframeHtml = fetchText(iframeUrl, targetUrl) ?: return null

        val wpSign = jsVar(iframeHtml, "wp_sign")
        if (wpSign.isNullOrBlank()) {
            println("[-] [LanzouApi] No wp_sign in iframe page")
            return null
        }
        val ajaxData = jsVar(iframeHtml, "ajaxdata").orEmpty()
        val kdns = Regex("""var\s+kdns\s*=\s*(\d+)""").find(iframeHtml)?.groupValues?.get(1) ?: "1"
        println("[LanzouApi] iframe params: wp_sign length=${wpSign.length}, ajaxdata=$ajaxData, kdns=$kdns")

        val fileId = Regex("""ajaxfile\.php\?file=(\d+)""").find(iframeHtml)?.groupValues?.get(1)
            ?: Regex("""var\s+fid\s*=\s*(\d+)""").find(shareHtml)?.groupValues?.get(1)
        if (fileId.isNullOrBlank()) {
            println("[-] [LanzouApi] No file id in iframe/share page")
            return null
        }

        val declaredApi = jsVar(iframeHtml, "domain1")?.takeIf { it.contains("ajaxfile.php") }
            ?: jsVar(iframeHtml, "domain2")?.takeIf { it.contains("ajaxfile.php") }
        val apiUrl = declaredApi ?: "$domain/ajaxfile.php?file=$fileId"
        val postUrl = if (apiUrl.contains("file=")) apiUrl else "$apiUrl?file=$fileId"
        println("[LanzouApi] POST ajaxfile: $postUrl")

        val response = try {
            client.post(postUrl) {
                header("User-Agent", LanzouConstants.WEB_UA)
                header("Referer", iframeUrl)
                header("Origin", domain)
                header("X-Requested-With", "XMLHttpRequest")
                val cookie = cookieHeader(postUrl)
                if (cookie.isNotEmpty()) header("Cookie", cookie)
                setBody(FormDataContent(Parameters.build {
                    append("action", "downprocess")
                    append("websignkey", ajaxData)
                    append("signs", ajaxData)
                    append("sign", wpSign)
                    append("websign", "")
                    append("kd", kdns)
                    append("ves", "1")
                }))
            }
        } catch (e: Throwable) {
            println("[-] [LanzouApi] ajaxfile POST failed: ${e.message}")
            return null
        }
        absorbCookies(postUrl, response)

        val text = response.bodyAsText()
        println("[LanzouApi] ajaxfile response status=${response.status}, body preview: ${text.take(240)}")
        val json = decodeJson(text) ?: return null
        if (json["zt"].intOr(0) != 1) {
            println("[-] [LanzouApi] ajaxfile unsuccessful: zt=${json["zt"].intOr(0)}, inf=${json["inf"].strOrNull()}")
            return null
        }

        val dom = json["dom"].strOrNull().orEmpty()
        val rel = json["url"].strOrNull().orEmpty()
        if (dom.isBlank() || rel.isBlank() || rel.startsWith("/") || rel.contains("://")) {
            println("[-] [LanzouApi] ajaxfile returned invalid dom/url: dom=$dom, urlLength=${rel.length}")
            return null
        }
        val jumpUrl = "${originOf(dom)}/file/$rel"
        setCookie(jumpUrl, "down_ip", "1")
        println("[LanzouApi] jump url length=${jumpUrl.length}, resolving verification")

        val (primaryUrl, probedSize) = resolveVerifiedUrl(jumpUrl, iframeUrl) ?: return null
        val (finalUrl, mirrorSize) = resolveReachableUrl(primaryUrl, iframeUrl, filename)
        val size = shareSize.takeIf { it > 0 } ?: probedSize.takeIf { it > 0 } ?: mirrorSize
        val name = filename.ifBlank { filenameFromUrl(finalUrl) }
        println("[+] [LanzouApi] iframe flow success! filename=$name, size=$size, host=${hostOf(finalUrl)}, urlLength=${finalUrl.length}")

        return DownloadLink(
            fid = fileId,
            filename = name,
            downloadUrl = finalUrl,
            size = size,
            guestCookie = LanzouConstants.DOWN_IP_COOKIE
        )
    }

    /**
     * 下载节点可能依次返回两类 HTML：
     * 1) acw_sc__v2 人机校验（arg1）→ 本地算 Cookie 后重放同一 URL；
     * 2) “系统发现您的网络异常，需要验证后下载文件”→ 等 2 秒后 POST ajax.php，
     *    用返回的最终 URL 替换直链。
     */
    private suspend fun resolveVerifiedUrl(jumpUrl: String, referer: String): Pair<String, Long>? {
        var currentUrl = jumpUrl
        repeat(8) {
            val response = try {
                client.get(currentUrl) {
                    header("User-Agent", LanzouConstants.WEB_UA)
                    header("Referer", referer)
                    val cookie = cookieHeader(currentUrl)
                    if (cookie.isNotEmpty()) header("Cookie", cookie)
                }
            } catch (e: Throwable) {
                println("[-] [LanzouApi] download node GET failed: ${e.message}")
                return null
            }
            absorbCookies(currentUrl, response)

            val contentType = response.headers["Content-Type"].orEmpty()
            if (!contentType.contains("text/html", ignoreCase = true)) {
                val size = contentRangeTotal(response)
                    ?: response.headers["Content-Length"]?.toLongOrNull()?.takeIf { response.status.value == 200 }
                    ?: 0L
                response.bodyAsBytes()
                return currentUrl to size
            }

            val body = response.bodyAsText()
            val arg1 = Regex("""arg1\s*=\s*'([0-9A-Fa-f]{40})'""").find(body)?.groupValues?.get(1)
            if (arg1 != null) {
                println("[LanzouApi] acw_sc__v2 challenge detected, solving cookie")
                setCookie(currentUrl, "acw_sc__v2", acwScV2(arg1))
                return@repeat
            }
            if (body.contains("验证并下载") || body.contains("验证后下载")) {
                val verified = submitVerify(currentUrl, body) ?: return null
                return verified to 0L
            }
            println("[-] [LanzouApi] unexpected download node page, length=${body.length}")
            return null
        }
        println("[-] [LanzouApi] download node retry limit reached")
        return null
    }

    private suspend fun submitVerify(nodeUrl: String, html: String): String? {
        val dataBlock = Regex("""data\s*:\s*\{([^{}]*)\}""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { it.contains("file") }
            ?: html
        val file = Regex("""['"]?file['"]?\s*:\s*['"]([^'"]+)['"]""").find(dataBlock)?.groupValues?.get(1)
        val sign = Regex("""['"]?sign['"]?\s*:\s*['"]([^'"]+)['"]""").find(dataBlock)?.groupValues?.get(1)
        if (file.isNullOrBlank() || sign.isNullOrBlank()) {
            println("[-] [LanzouApi] verification page missing file/sign")
            return null
        }

        delay(verifyDelayMillis)
        val ajaxUrl = ajaxUrlFor(nodeUrl)
        val response = try {
            client.post(ajaxUrl) {
                header("User-Agent", LanzouConstants.WEB_UA)
                header("Referer", nodeUrl)
                header("Origin", originOf(nodeUrl))
                header("X-Requested-With", "XMLHttpRequest")
                val cookie = cookieHeader(ajaxUrl)
                if (cookie.isNotEmpty()) header("Cookie", cookie)
                setBody(FormDataContent(Parameters.build {
                    append("file", file)
                    append("el", "2")
                    append("sign", sign)
                }))
            }
        } catch (e: Throwable) {
            println("[-] [LanzouApi] verification POST failed: ${e.message}")
            return null
        }
        absorbCookies(ajaxUrl, response)

        val text = response.bodyAsText()
        println("[LanzouApi] verification response: ${text.take(240)}")
        val json = decodeJson(text) ?: return null
        val url = json["url"].strOrNull().orEmpty()
        if (json["zt"].intOr(0) != 1 || url.isBlank() || url == "?SignError") {
            println("[-] [LanzouApi] verification rejected: zt=${json["zt"].intOr(0)}, url=$url")
            return null
        }
        return absolutize(nodeUrl, url)
    }

    private suspend fun resolveReachableUrl(
        primaryUrl: String,
        referer: String,
        filename: String
    ): Pair<String, Long> {
        val primaryTotal = probeUrl(primaryUrl, referer, PRIMARY_PROBE_TIMEOUT_MS)
        if (primaryTotal != null) return primaryUrl to primaryTotal

        println("[-] [LanzouApi] primary CDN unreachable, trying mirrors")
        for (mirror in mirrorCandidates(filename)) {
            val total = probeUrl(mirror, referer, MIRROR_PROBE_TIMEOUT_MS)
            if (total != null) {
                println("[+] [LanzouApi] mirror reachable: host=${hostOf(mirror)}, total=$total")
                return mirror to total
            }
        }
        println("[-] [LanzouApi] no mirror reachable, keeping primary url")
        return primaryUrl to 0L
    }

    private suspend fun probeUrl(url: String, referer: String, timeoutMillis: Long): Long? {
        return try {
            val response = client.get(url) {
                header("User-Agent", LanzouConstants.WEB_UA)
                header("Referer", referer)
                header("Range", "bytes=0-0")
                timeout {
                    requestTimeoutMillis = timeoutMillis
                    socketTimeoutMillis = timeoutMillis
                }
                val cookie = cookieHeader(url)
                if (cookie.isNotEmpty()) header("Cookie", cookie)
            }
            absorbCookies(url, response)
            val status = response.status.value
            val total = contentRangeTotal(response)
                ?: response.headers["Content-Length"]?.toLongOrNull()
            response.bodyAsChannel().cancel(null)
            if (status == 200 || status == 206) total else null
        } catch (e: Throwable) {
            println("[-] [LanzouApi] probe failed: host=${hostOf(url)}, ${e.message}")
            null
        }
    }

    private fun mirrorCandidates(filename: String): List<String> {
        if (filename.isBlank()) return emptyList()
        val encoded = filename.encodeURLPathPart()
        return MIRROR_BASE_URLS.map { "$it$encoded" }
    }

    // ---------- 公共小工具 ----------

    private suspend fun fetchText(url: String, referer: String): String? {
        return try {
            val response = client.get(url) {
                header("User-Agent", LanzouConstants.WEB_UA)
                header("Referer", referer)
                val cookie = cookieHeader(url)
                if (cookie.isNotEmpty()) header("Cookie", cookie)
            }
            absorbCookies(url, response)
            response.bodyAsText()
        } catch (e: Throwable) {
            println("[-] [LanzouApi] GET failed url=$url: ${e.message}")
            null
        }
    }

    private fun decodeJson(text: String): JsonObject? = try {
        YunXJson.decodeFromString<JsonObject>(text)
    } catch (e: Throwable) {
        println("[-] [LanzouApi] Failed to decode JSON: ${e.message}")
        null
    }

    private fun extractSign(html: String): String {
        val match = Regex("""['"]sign['"]\s*:\s*['"]([^'"]+)['"]""").find(html)
            ?: Regex("""var\s+sign\s*=\s*['"]([^'"]+)['"]""").find(html)
            ?: Regex("""var\s+skdkd\s*=\s*['"]([^'"]+)['"]""").find(html)
        return match?.groupValues?.get(1).orEmpty()
    }

    private fun jsVar(html: String, name: String): String? =
        Regex("""(?:var|let|const)\s+$name\s*=\s*['"]([^'"]*)['"]""").find(html)?.groupValues?.get(1)

    private fun extractFilename(html: String): String {
        val title = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.trim().orEmpty()
        return title.substringBefore(" - ").trim()
    }

    private fun parseShareSize(html: String): Long {
        val meta = Regex(
            "<meta\\s+name=\"description\"\\s+content=\"[^\"]*?([0-9.]+)\\s*([KMGT])\\s*\"",
            RegexOption.IGNORE_CASE
        ).find(html)
        if (meta != null) return displaySizeToBytes(meta.groupValues[1], meta.groupValues[2])

        val span = Regex("""</span>\s*([0-9.]+)\s*([KMGT])\s*<""", RegexOption.IGNORE_CASE).find(html)
        if (span != null) return displaySizeToBytes(span.groupValues[1], span.groupValues[2])
        return 0L
    }

    private fun displaySizeToBytes(value: String, unit: String): Long {
        val number = value.toDoubleOrNull() ?: return 0L
        val factor = when (unit.uppercase()) {
            "K" -> 1024L
            "M" -> 1024L * 1024L
            "G" -> 1024L * 1024L * 1024L
            "T" -> 1024L * 1024L * 1024L * 1024L
            else -> 1L
        }
        return (number * factor).toLong()
    }

    private fun filenameFromUrl(url: String): String {
        val fn = Regex("""[?&]fn=([^&]+)""").find(url)?.groupValues?.get(1)
        return fn?.takeIf { it.isNotBlank() }
            ?: url.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: "lanzou_file"
    }

    private fun contentRangeTotal(response: HttpResponse): Long? =
        response.headers["Content-Range"]?.substringAfterLast('/')?.trim()?.toLongOrNull()

    private fun originOf(url: String): String =
        Regex("""^(https?://[^/]+)""").find(url)?.groupValues?.get(1).orEmpty()

    private fun hostOf(url: String): String =
        Regex("""^https?://([^/]+)""").find(url)?.groupValues?.get(1).orEmpty()

    private fun absolutize(base: String, target: String): String {
        if (target.startsWith("http://") || target.startsWith("https://")) return target
        val origin = originOf(base)
        val basePath = base.substringBefore('?').substringBefore('#')
        return when {
            target.startsWith("//") -> "https:$target"
            target.startsWith("/") -> origin + target
            target.startsWith("?") -> basePath + target
            else -> basePath.substringBeforeLast('/') + "/" + target
        }
    }

    private fun ajaxUrlFor(nodeUrl: String): String {
        val basePath = nodeUrl.substringBefore('?').substringBefore('#')
        return if (basePath.endsWith("/")) {
            basePath + "ajax.php"
        } else {
            basePath.substringBeforeLast('/') + "/ajax.php"
        }
    }

    private fun setCookie(url: String, name: String, value: String) {
        val host = hostOf(url)
        if (host.isBlank()) return
        cookies.getOrPut(host) { mutableMapOf() }[name] = value
    }

    private fun cookieHeader(url: String): String =
        cookies[hostOf(url)]?.entries?.joinToString("; ") { "${it.key}=${it.value}" }.orEmpty()

    private fun absorbCookies(url: String, response: HttpResponse) {
        response.headers.getAll("Set-Cookie")?.forEach { raw ->
            val pair = raw.substringBefore(';')
            val name = pair.substringBefore('=', "").trim()
            if (name.isBlank()) return@forEach
            setCookie(url, name, pair.substringAfter('=', "").trim())
        }
    }

    private fun acwScV2(arg1: String): String {
        val positions = intArrayOf(
            15, 35, 29, 24, 33, 16, 1, 38, 10, 9, 19, 31, 40, 27, 22, 23, 25, 13, 6, 11,
            39, 18, 20, 8, 14, 21, 32, 26, 2, 30, 7, 4, 17, 5, 3, 28, 34, 37, 12, 36
        )
        val mask = "30001760008560060615015330036900278003" + "75"
        val real = CharArray(positions.size)
        for (index in arg1.indices) {
            for (position in positions.indices) {
                if (positions[position] == index + 1) real[position] = arg1[index]
            }
        }
        val shuffled = real.concatToString()
        return buildString {
            var index = 0
            while (index + 2 <= shuffled.length && index + 2 <= mask.length) {
                val left = shuffled.substring(index, index + 2).toInt(16)
                val right = mask.substring(index, index + 2).toInt(16)
                append((left xor right).toString(16).padStart(2, '0'))
                index += 2
            }
        }
    }
}
