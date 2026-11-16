package com.yunx.feature.parser.uc

import com.yunx.core.model.DownloadLink
import com.yunx.core.model.FileThumbnail
import com.yunx.core.model.QuotaInfo
import com.yunx.core.model.ShareFile
import com.yunx.core.model.ShareToken
import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.core.network.YunXJson
import com.yunx.feature.parser.arr
import com.yunx.feature.parser.boolOr
import com.yunx.feature.parser.intOr
import com.yunx.feature.parser.longOr
import com.yunx.feature.parser.obj
import com.yunx.feature.parser.quark.QuarkApiException
import com.yunx.feature.parser.quark.QuarkCookieUtil
import com.yunx.feature.parser.str
import com.yunx.feature.parser.strOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * UC 网盘 API 封装（KMP / Ktor 3.x + kotlinx.serialization）。
 */
class UCApi(
    private val clientProvider: () -> HttpClient = { YunXHttpClientFactory.createHttpClient() }
) {
    private val client: HttpClient get() = clientProvider()

    var cookieSink: ((merged: String, requestCookie: String) -> Unit)? = null
    var guestPugs: String = ""
        private set

    // ---------- 账号 ----------

    suspend fun fetchNickname(cookie: String): String? = runCatching {
        val response = client.get(UCConstants.ACCOUNT_INFO_URL) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, UCConstants.USER_AGENT)
        }
        if (!response.status.isSuccess()) return null
        val bodyStr = response.bodyAsText()
        val json = YunXJson.parseToJsonElement(bodyStr).obj ?: return null
        if (json["success"].boolOr(false)) {
            json["data"]?.obj?.get("nickname").strOrNull()?.takeIf { it.isNotBlank() }
        } else null
    }.getOrNull()

    // ---------- 分享解析 ----------

    /** 获取分享 Token */
    suspend fun getShareToken(shareId: String, pwd: String?, cookie: String): ShareToken? {
        val bodyObj = buildJsonObject {
            put("pwd_id", shareId)
            put("passcode", pwd ?: "")
            put("support_visit_limit_private_share", true)
        }
        val response = postJson(UCConstants.SHARE_TOKEN_URL, cookie, bodyObj.toString())
        return parseData(response) { data ->
            val dataObj = data.obj ?: return@parseData null
            ShareToken(
                stoken = dataObj["stoken"].str(),
                title = dataObj["title"].str(),
                firstFid = dataObj["first_fid"].str()
            )
        }
    }

    /** 获取分享文件列表 */
    suspend fun getShareFiles(
        shareId: String,
        stoken: String,
        pdirFid: String,
        cookie: String,
        page: Int = 1,
        size: Int = 100
    ): List<ShareFile>? {
        val url = buildString {
            append(UCConstants.SHARE_DETAIL_URL)
            append("&pwd_id=").append(shareId)
            append("&stoken=").append(stoken)
            append("&pdir_fid=").append(pdirFid)
            append("&ver=2")
            append("&force=0")
            append("&_page=").append(page)
            append("&_size=").append(size)
            append("&_fetch_banner=0")
            append("&_fetch_share=0")
            append("&fetch_relate_conversation=0")
            append("&_fetch_total=1")
            append("&_sort=file_type:asc,file_name:asc")
        }

        val response = client.get(url) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, UCConstants.USER_AGENT)
            header("Origin", UCConstants.WEB_ORIGIN)
            header("Referer", UCConstants.DOWNLOAD_REFERER)
        }

        return parseData(response) { data ->
            val array = data.obj?.get("list").arr ?: JsonArray(emptyList())
            array.mapNotNull { itemElem ->
                val item = itemElem.obj ?: return@mapNotNull null
                val fid = item["fid"].strOrNull() ?: return@mapNotNull null
                val fname = item["file_name"].str()
                val fsize = item["size"].longOr()
                val isdir = item["dir"].boolOr()
                val pdir = item["pdir_fid"].str()
                val fidToken = item["share_fid_token"].str()
                val modifyTime = item["updated_at"].str()
                ShareFile(
                    fid = fid,
                    fname = fname,
                    fsize = fsize,
                    isdir = isdir,
                    pdirFid = pdir,
                    fidToken = fidToken,
                    modifyTime = modifyTime
                )
            }
        }
    }

    /** 获取下载直链 */
    suspend fun getDownloadLink(fid: String, cookie: String): DownloadLink? {
        val bodyObj = buildJsonObject {
            put("fids", buildJsonArray { add(JsonPrimitive(fid)) })
        }
        val response = postJson(UCConstants.DOWNLOAD_URL, cookie, bodyObj.toString())
        mergeCookieFromResponse(response, cookie)
        val bodyStr = response.bodyAsText()
        if (bodyStr.isBlank()) throw QuarkApiException("UC下载直链响应为空")
        val json = YunXJson.parseToJsonElement(bodyStr).obj ?: throw QuarkApiException("解析UC下载响应失败")
        val status = json["status"].intOr()
        val code = json["code"].intOr()
        if (status != 200 && code != 0) {
            val msg = json["message"].strOrNull() ?: "获取直链失败 (status=$status)"
            throw QuarkApiException(msg, code)
        }
        val dataArray = json["data"].arr ?: return null
        val first = dataArray.firstOrNull()?.obj ?: return null

        return DownloadLink(
            fid = first["fid"].str(),
            filename = first["file_name"].str(),
            downloadUrl = first["download_url"].str(),
            size = first["size"].longOr()
        )
    }

    private suspend fun postJson(url: String, cookie: String, jsonBody: String): HttpResponse {
        val response = client.post(url) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, UCConstants.USER_AGENT)
            header("Referer", UCConstants.DOWNLOAD_REFERER)
            header("Origin", UCConstants.WEB_ORIGIN)
            contentType(ContentType.Application.Json)
            setBody(jsonBody)
        }
        mergeCookieFromResponse(response, cookie)
        return response
    }

    private fun mergeCookieFromResponse(response: HttpResponse, requestCookie: String) {
        val setCookies = response.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()
        if (setCookies.isEmpty()) return
        val merged = QuarkCookieUtil.mergeFromSetCookies(requestCookie, setCookies)
        if (merged != requestCookie) {
            cookieSink?.invoke(merged, requestCookie)
        }
    }

    private suspend fun <T> parseData(response: HttpResponse, parser: (JsonElement) -> T?): T? {
        val bodyStr = response.bodyAsText()
        if (bodyStr.isBlank()) return null
        val json = YunXJson.parseToJsonElement(bodyStr).obj ?: return null
        val status = json["status"].intOr()
        val code = json["code"].intOr()
        if (status != 200 && code != 0) {
            val msg = json["message"].strOrNull() ?: "操作失败 (status=$status)"
            throw QuarkApiException(msg, code)
        }
        val data = json["data"] ?: return null
        return parser(data)
    }
}
