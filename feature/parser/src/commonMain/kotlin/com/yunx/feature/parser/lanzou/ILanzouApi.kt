package com.yunx.feature.parser.lanzou

import com.yunx.core.model.DownloadLink
import com.yunx.core.model.ShareFile
import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.core.network.YunXJson
import com.yunx.feature.parser.*
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.serialization.json.JsonObject

data class ILanzouSharePage(
    val files: List<ShareFile>,
    val total: Int,
    val title: String
)

class ILanzouApi(
    private val client: HttpClient = YunXHttpClientFactory.createHttpClient()
) {
    suspend fun getShareFiles(
        shareId: String,
        password: String = "",
        folderId: String = "0"
    ): ILanzouSharePage {
        val uuid = ILanzouConstants.newUuid()
        val referer = "${ILanzouConstants.SHARE_REFERER_PREFIX}$shareId"

        val response = client.post(ILanzouConstants.SHARE_LIST) {
            header("User-Agent", ILanzouConstants.WEB_UA)
            header("Referer", referer)
            setBody(FormDataContent(Parameters.build {
                append("shareId", shareId)
                append("folderId", folderId)
                append("sharePwd", password)
                append("uuid", uuid)
                append("devType", ILanzouConstants.DEV_TYPE)
                append("devModel", ILanzouConstants.SHARE_DEV_MODEL)
                append("devVersion", ILanzouConstants.DEV_VERSION)
            }))
        }

        val text = response.bodyAsText()
        val json = YunXJson.decodeFromString<JsonObject>(text)
        val data = json["data"]?.obj ?: return ILanzouSharePage(emptyList(), 0, "")

        val list = data["list"]?.arr ?: emptyList()
        val files = list.mapNotNull { item ->
            val obj = item.obj ?: return@mapNotNull null
            val isDir = obj["type"].intOr(0) == 1 || obj["folderId"] != null
            val fid = obj["fileId"].strOrNull() ?: obj["folderId"].strOrNull().orEmpty()
            val name = obj["fileName"].strOrNull() ?: obj["folderName"].strOrNull().orEmpty()
            val size = obj["fileSize"].longOr(0L)
            ShareFile(
                fid = fid,
                fname = name,
                fsize = size,
                isdir = isDir,
                fidToken = shareId
            )
        }
        return ILanzouSharePage(files, files.size, "")
    }

    suspend fun getDownloadUrl(
        shareId: String,
        fileId: String,
        password: String = ""
    ): DownloadLink? {
        val uuid = ILanzouConstants.newUuid()
        val referer = "${ILanzouConstants.SHARE_REFERER_PREFIX}$shareId"

        val response = client.post(ILanzouConstants.SHARE_REDIRECT) {
            header("User-Agent", ILanzouConstants.WEB_UA)
            header("Referer", referer)
            setBody(FormDataContent(Parameters.build {
                append("shareId", shareId)
                append("fileId", fileId)
                append("sharePwd", password)
                append("uuid", uuid)
                append("devType", ILanzouConstants.DEV_TYPE)
                append("devModel", ILanzouConstants.SHARE_DEV_MODEL)
                append("devVersion", ILanzouConstants.DEV_VERSION)
            }))
        }

        val text = response.bodyAsText()
        val json = YunXJson.decodeFromString<JsonObject>(text)
        val data = json["data"]?.obj ?: return null
        val downloadUrl = data["downloadUrl"].strOrNull() ?: data["downUrl"].strOrNull() ?: return null

        return DownloadLink(
            fid = fileId,
            filename = data["fileName"].strOrNull().orEmpty(),
            downloadUrl = downloadUrl,
            size = data["fileSize"].longOr(0L),
            guestCookie = ""
        )
    }
}
