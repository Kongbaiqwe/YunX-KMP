package com.yunx.feature.parser.xunlei

import com.yunx.core.model.DownloadLink
import com.yunx.core.model.QuotaInfo
import com.yunx.core.model.ShareFile
import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.core.network.YunXJson
import com.yunx.feature.parser.arr
import com.yunx.feature.parser.intOr
import com.yunx.feature.parser.longOr
import com.yunx.feature.parser.obj
import com.yunx.feature.parser.quark.QuarkApiException
import com.yunx.feature.parser.str
import com.yunx.feature.parser.strOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 迅雷云盘 API 封装（KMP / Ktor 3.x + kotlinx.serialization）。
 */
class XunleiApi(
    private val clientProvider: () -> HttpClient = { YunXHttpClientFactory.createHttpClient() }
) {
    private val client: HttpClient get() = clientProvider()

    /** 分享解析 */
    suspend fun getShare(
        shareId: String,
        passCode: String,
        accessToken: String = "",
        deviceId: String = "",
        pageToken: String = ""
    ): XunleiShareResult? {
        val url = buildString {
            append(XunleiConstants.SHARE_URL)
            append("?share_id=").append(shareId)
            append("&pass_code=").append(passCode.encodeURLParameter())
            append("&limit=100&page_token=").append(pageToken.encodeURLParameter())
            append("&thumbnail_size=SIZE_SMALL")
        }

        val response = client.get(url) {
            header(HttpHeaders.UserAgent, XunleiConstants.APP_UA)
            if (accessToken.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            if (deviceId.isNotBlank()) {
                header("X-Device-Id", deviceId)
            }
        }

        val bodyStr = response.bodyAsText()
        if (bodyStr.isBlank()) return null
        val data = YunXJson.parseToJsonElement(bodyStr).obj ?: return null

        val shareStatus = data["share_status"].str()
        when (shareStatus) {
            "PASS_CODE_EMPTY" -> throw QuarkApiException("请输入提取码")
            "PASS_CODE_ERROR" -> throw QuarkApiException("提取码错误")
            "PASS_CODE_NEED" -> throw QuarkApiException("此分享需要提取码")
        }

        val filesArray = data["files"].arr ?: JsonArray(emptyList())
        val files = filesArray.mapNotNull { itemElem ->
            val item = itemElem.obj ?: return@mapNotNull null
            val id = item["id"].strOrNull() ?: return@mapNotNull null
            val name = item["name"].str()
            val size = item["size"].longOr()
            val kind = item["kind"].str()
            val isDir = kind == "drive#folder"
            val parentId = item["parent_id"].str()
            val updated = item["updated_time"].str()
            ShareFile(
                fid = id,
                fname = name,
                fsize = size,
                isdir = isDir,
                pdirFid = parentId,
                fidToken = "",
                modifyTime = updated
            )
        }

        return XunleiShareResult(
            title = data["title"].str(),
            files = files,
            passCodeToken = data["pass_code_token"].str(),
            shareId = shareId,
            nextPageToken = data["next_page_token"].str()
        )
    }

    /** 文件详情与下载直链 */
    suspend fun getFileDetail(
        fileId: String,
        accessToken: String,
        deviceId: String = ""
    ): DownloadLink? {
        val url = "${XunleiConstants.FILES_URL}/$fileId?_magic=2021&usage=PLAY&thumbnail_size=SIZE_LARGE" +
            "&with=hdr10&with=subtitle_files&with=task&with=public_share_tag"

        val response = client.get(url) {
            header(HttpHeaders.UserAgent, XunleiConstants.APP_UA)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            if (deviceId.isNotBlank()) {
                header("X-Device-Id", deviceId)
            }
        }

        val bodyStr = response.bodyAsText()
        val data = YunXJson.parseToJsonElement(bodyStr).obj ?: return null
        val links = data["links"].obj
        val octet = links?.get("application/octet-stream").obj
        val downloadUrl = octet?.get("url").str().ifEmpty { data["web_content_link"].str() }

        return DownloadLink(
            fid = data["id"].str(),
            filename = data["name"].str(),
            downloadUrl = downloadUrl,
            size = data["size"].longOr()
        )
    }

    /** 网盘容量 */
    suspend fun getQuota(accessToken: String): QuotaInfo? = runCatching {
        val response = client.get(XunleiConstants.ABOUT_URL) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.UserAgent, XunleiConstants.APP_UA)
        }
        val bodyStr = response.bodyAsText()
        val data = YunXJson.parseToJsonElement(bodyStr).obj ?: return null
        val space = data["space"].obj ?: return null
        QuotaInfo(
            used = space["used"].longOr(),
            total = space["total"].longOr()
        )
    }.getOrNull()
}
