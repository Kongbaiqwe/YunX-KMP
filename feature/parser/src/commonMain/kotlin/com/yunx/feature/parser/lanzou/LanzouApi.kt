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
        val htmlResponse = client.get(targetUrl) {
            header("User-Agent", LanzouConstants.WEB_UA)
            header("Referer", targetUrl)
        }
        val html = htmlResponse.bodyAsText()

        // 提取 signs / signs 接口地址
        val signMatch = Regex("""['"]sign['"]\s*:\s*['"]([^'"]+)['"]""").find(html)
            ?: Regex("""var\s+skdkd\s*=\s*['"]([^'"]+)['"]""").find(html)
        val sign = signMatch?.groupValues?.get(1).orEmpty()

        val ajaxPathMatch = Regex("""url\s*:\s*['"](/ajaxm\.php\?[^'"]+)['"]""").find(html)
            ?: Regex("""['"](/ajaxm\.php)['"]""").find(html)
        val ajaxPath = ajaxPathMatch?.groupValues?.get(1) ?: "/ajaxm.php"

        val domain = targetUrl.substringBefore("/", "https://www.lanzoui.com")
        val ajaxUrl = if (ajaxPath.startsWith("http")) ajaxPath else "$domain$ajaxPath"

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
        val json = YunXJson.decodeFromString<JsonObject>(text)
        val zt = json["zt"].intOr(0)
        if (zt != 1) return null

        val dom = json["dom"].strOrNull().orEmpty()
        val url = json["url"].strOrNull().orEmpty()
        val downloadUrl = if (url.startsWith("http")) url else "$dom/file/$url"

        return DownloadLink(
            fid = sign,
            filename = json["inf"].strOrNull().orEmpty(),
            downloadUrl = downloadUrl,
            size = 0L,
            guestCookie = LanzouConstants.DOWN_IP_COOKIE
        )
    }
}
