package com.yunx.feature.parser.pan115

import com.yunx.core.model.DownloadLink
import com.yunx.core.model.ShareFile
import com.yunx.core.model.ShareInfo
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
import kotlinx.serialization.json.jsonObject

data class Pan115SharePage(
    val files: List<ShareFile>,
    val total: Int,
    val userId: String,
    val title: String,
    val shareState: Int
)

class Pan115Api(
    private val client: HttpClient = YunXHttpClientFactory.createHttpClient()
) {
    suspend fun getSharePage(
        shareCode: String,
        receiveCode: String = "",
        cid: String = "0",
        cookie: String = "",
        offset: Int = 0,
        limit: Int = Pan115Constants.SHARE_PAGE_LIMIT
    ): Pan115SharePage {
        val url = buildString {
            append(Pan115Constants.SHARE_SNAP_URL)
            append("?share_code=").append(shareCode)
            if (receiveCode.isNotBlank()) append("&receive_code=").append(receiveCode)
            append("&offset=").append(offset)
            append("&limit=").append(limit)
            append("&cid=").append(cid.ifBlank { Pan115Constants.ROOT_CID })
            append("&format=json")
        }

        val response = client.get(url) {
            header("User-Agent", Pan115Constants.WEB_UA)
            header("Referer", Pan115Constants.shareReferer(shareCode, receiveCode))
            if (cookie.isNotBlank()) header("Cookie", cookie)
        }
        val text = response.bodyAsText()
        val json = YunXJson.decodeFromString<JsonObject>(text)
        val data = json["data"]?.obj ?: return Pan115SharePage(emptyList(), 0, "", "", 0)

        val count = data["count"].intOr(0)
        val userId = data["share_info"]?.obj?.get("user_id").strOrNull() ?: data["user_id"].strOrNull().orEmpty()
        val title = data["share_info"]?.obj?.get("share_title").strOrNull() ?: data["share_title"].strOrNull().orEmpty()
        val state = data["share_info"]?.obj?.get("share_state").intOr(1)

        val rawList = data["list"]?.arr ?: emptyList()
        val files = rawList.mapNotNull { element ->
            val obj = element.obj ?: return@mapNotNull null
            val isDir = obj["fid"] == null && obj["cid"] != null
            val fid = if (isDir) obj["cid"].str() else obj["fid"].str()
            val name = if (isDir) obj["n"].str() else obj["n"].str()
            val size = if (isDir) 0L else obj["s"].longOr(0L)
            ShareFile(
                fid = fid,
                fname = name,
                fsize = size,
                isdir = isDir,
                fidToken = obj["pc"].strOrNull().orEmpty()
            )
        }

        return Pan115SharePage(files, count, userId, title, state)
    }

    suspend fun getAppDownloadLink(pickCode: String, cookie: String): DownloadLink? {
        if (pickCode.isBlank()) return null
        val key = Pan115Crypto.newKey()
        val payload = """{"pickcode":"$pickCode"}"""
        val encryptedData = Pan115Crypto.encode(payload, key)

        val response = client.post(Pan115Constants.APP_DOWNLOAD_URL) {
            header("User-Agent", Pan115Constants.CLIENT_UA)
            header("Referer", Pan115Constants.DOWNLOAD_REFERER)
            if (cookie.isNotBlank()) header("Cookie", cookie)
            setBody(FormDataContent(Parameters.build {
                append("data", encryptedData)
            }))
        }

        val text = response.bodyAsText()
        val json = YunXJson.decodeFromString<JsonObject>(text)
        val encrypted = json["data"].strOrNull() ?: return null
        val decodedText = Pan115Crypto.decode(encrypted, key)
        val decodedJson = YunXJson.decodeFromString<JsonObject>(decodedText)

        for ((_, value) in decodedJson) {
            val itemObj = value.obj ?: continue
            val urlObj = itemObj["url"]?.obj ?: continue
            val fileUrl = urlObj["url"].strOrNull() ?: continue
            if (fileUrl.isNotBlank()) {
                val fileName = itemObj["file_name"].strOrNull().orEmpty()
                val fileSize = itemObj["file_size"].longOr(0L)
                return DownloadLink(
                    fid = itemObj["pick_code"].strOrNull() ?: pickCode,
                    filename = fileName,
                    downloadUrl = fileUrl,
                    size = fileSize,
                    guestCookie = ""
                )
            }
        }
        return null
    }
}
