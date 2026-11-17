package com.yunx.feature.parser.lanzou

import com.yunx.core.model.DownloadLink
import com.yunx.core.model.ShareFile
import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.core.network.YunXJson
import com.yunx.feature.parser.*
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.JsonObject

class LanzouApi(
    private val client: HttpClient = YunXHttpClientFactory.createHttpClient()
) {
    suspend fun resolveDownloadLink(
        shareUrl: String,
        password: String = ""
    ): DownloadLink? {
        val targetUrl = LanzouConstants.normalizeShareUrl(shareUrl)
        println("[LanzouApi] Requesting share page: $targetUrl")
        val htmlResponse = client.get(targetUrl) {
            header("User-Agent", LanzouConstants.WEB_UA)
            header("Referer", targetUrl)
        }
        val html = htmlResponse.bodyAsText()
        println("[LanzouApi] Share page response status=${htmlResponse.status}, html length=${html.length}")

        // 提取 signs / signs 接口地址
        val signMatch = Regex("""['"]sign['"]\s*:\s*['"]([^'"]+)['"]""").find(html)
            ?: Regex("""var\s+skdkd\s*=\s*['"]([^'"]+)['"]""").find(html)
        val sign = signMatch?.groupValues?.get(1).orEmpty()
        println("[LanzouApi] Extracted sign: '$sign'")

        val ajaxPathMatch = Regex("""url\s*:\s*['"](/ajaxm\.php\?[^'"]+)['"]""").find(html)
            ?: Regex("""['"](/ajaxm\.php)['"]""").find(html)
        val ajaxPath = ajaxPathMatch?.groupValues?.get(1) ?: "/ajaxm.php"

        val domain = Regex("""^(https?://[^/]+)""").find(targetUrl)?.groupValues?.get(1) ?: "https://www.lanzoui.com"
        val ajaxUrl = if (ajaxPath.startsWith("http")) ajaxPath else "$domain$ajaxPath"
        println("[LanzouApi] POST ajaxUrl: $ajaxUrl (password length=${password.length})")

        val response = client.post(ajaxUrl) {
            header("User-Agent", LanzouConstants.WEB_UA)
            header("Referer", targetUrl)
            header("X-Requested-With", "XMLHttpRequest")
            setBody(FormDataContent(Parameters.build {
                append("action", "downprocess")
                append("sign", sign)
                append("p", password)
            }))
        }

        val text = response.bodyAsText()
        println("[LanzouApi] Ajax response status=${response.status}, body preview: ${text.take(200)}")
        val json = try {
            YunXJson.decodeFromString<JsonObject>(text)
        } catch (e: Throwable) {
            println("[-] [LanzouApi] Failed to decode JSON response: ${e.message}")
            return null
        }
        val zt = json["zt"].intOr(0)
        if (zt != 1) {
            println("[-] [LanzouApi] Resolved unsuccessful: zt=$zt, inf=${json["inf"].strOrNull()}")
            return null
        }

        val dom = json["dom"].strOrNull().orEmpty()
        val url = json["url"].strOrNull().orEmpty()
        val downloadUrl = if (url.startsWith("http")) url else "$dom/file/$url"
        val filename = json["inf"].strOrNull().orEmpty()
        println("[+] [LanzouApi] Success! filename=$filename, downloadUrl=$downloadUrl")

        return DownloadLink(
            fid = sign,
            filename = filename,
            downloadUrl = downloadUrl,
            size = 0L,
            guestCookie = LanzouConstants.DOWN_IP_COOKIE
        )
    }
}
