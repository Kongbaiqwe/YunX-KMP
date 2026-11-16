package com.yunx.feature.parser.guangya

import com.yunx.core.model.DownloadLink
import com.yunx.core.model.ShareFile
import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.core.network.YunXJson
import com.yunx.feature.parser.*
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject

data class GuangYaSharePage(
    val files: List<ShareFile>,
    val total: Int,
    val shareAccessToken: String
)

class GuangYaApi(
    private val client: HttpClient = YunXHttpClientFactory.createHttpClient()
) {
    private val deviceId = GuangYaConstants.newDeviceId()

    private fun commonHeaders(builder: io.ktor.client.request.HttpRequestBuilder) {
        builder.header("User-Agent", GuangYaConstants.WEB_UA)
        builder.header("Referer", GuangYaConstants.DOWNLOAD_REFERER)
        builder.header("did", deviceId)
        builder.header("dt", "3")
        builder.contentType(ContentType.Application.Json)
    }

    suspend fun getShareAccessToken(shareCode: String, extractCode: String = ""): String {
        val payload = """{"share_id":"$shareCode","access_code":"$extractCode"}"""
        val response = client.post(GuangYaConstants.SHARE_ACCESS_TOKEN) {
            commonHeaders(this)
            setBody(payload)
        }
        val text = response.bodyAsText()
        val json = YunXJson.decodeFromString<JsonObject>(text)
        return json["data"]?.obj?.get("share_access_token").strOrNull().orEmpty()
    }

    suspend fun getShareFiles(
        shareCode: String,
        shareAccessToken: String,
        parentId: String = ""
    ): GuangYaSharePage {
        val payload = """{"share_id":"$shareCode","share_access_token":"$shareAccessToken","parent_id":"$parentId","page":1,"page_size":${GuangYaConstants.PAGE_SIZE}}"""
        val response = client.post(GuangYaConstants.SHARE_FILES) {
            commonHeaders(this)
            setBody(payload)
        }
        val text = response.bodyAsText()
        val json = YunXJson.decodeFromString<JsonObject>(text)
        val data = json["data"]?.obj ?: return GuangYaSharePage(emptyList(), 0, shareAccessToken)

        val total = data["total"].intOr(0)
        val list = data["list"]?.arr ?: emptyList()
        val files = list.mapNotNull { item ->
            val obj = item.obj ?: return@mapNotNull null
            val isDir = obj["type"].intOr(0) == 1
            val fid = obj["file_id"].strOrNull().orEmpty()
            val name = obj["file_name"].strOrNull().orEmpty()
            val size = obj["file_size"].longOr(0L)
            ShareFile(
                fid = fid,
                fname = name,
                fsize = size,
                isdir = isDir,
                fidToken = shareAccessToken
            )
        }
        return GuangYaSharePage(files, total, shareAccessToken)
    }

    suspend fun getDownloadUrl(
        shareCode: String,
        shareAccessToken: String,
        fileId: String
    ): DownloadLink? {
        val payload = """{"share_id":"$shareCode","share_access_token":"$shareAccessToken","file_id":"$fileId"}"""
        val response = client.post(GuangYaConstants.SHARE_DOWNLOAD) {
            commonHeaders(this)
            setBody(payload)
        }
        val text = response.bodyAsText()
        val json = YunXJson.decodeFromString<JsonObject>(text)
        val data = json["data"]?.obj ?: return null
        val downloadUrl = data["download_url"].strOrNull() ?: return null

        return DownloadLink(
            fid = fileId,
            filename = data["file_name"].strOrNull().orEmpty(),
            downloadUrl = downloadUrl,
            size = data["file_size"].longOr(0L),
            guestCookie = ""
        )
    }
}
