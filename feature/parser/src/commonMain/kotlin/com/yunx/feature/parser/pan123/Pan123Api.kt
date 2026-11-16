package com.yunx.feature.parser.pan123

import com.yunx.core.model.DownloadLink
import com.yunx.core.model.ShareFile
import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.core.network.YunXJson
import com.yunx.feature.parser.arr
import com.yunx.feature.parser.boolOr
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

/**
 * 纯 Kotlin 跨平台 CRC32 实现（IEEE 802.3 标准）
 */
object PureCrc32 {
    private val table = IntArray(256) { i ->
        var crc = i
        for (j in 0 until 8) {
            crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xEDB88320.toInt() else (crc ushr 1)
        }
        crc
    }

    fun crc32Hex(s: String): String {
        val bytes = s.encodeToByteArray()
        var crc = -1
        for (b in bytes) {
            val idx = (crc xor b.toInt()) and 0xFF
            crc = (crc ushr 8) xor table[idx]
        }
        val value = (crc.toLong().inv()) and 0xFFFFFFFFL
        return value.toString(16)
    }
}

/**
 * 123云盘 API 封装（KMP / Ktor 3.x + kotlinx.serialization）。
 */
class Pan123Api(
    private val clientProvider: () -> HttpClient = { YunXHttpClientFactory.createHttpClient() }
) {
    private val client: HttpClient get() = clientProvider()

    /** 获取分享文件列表 */
    suspend fun getShare(
        shareKey: String,
        sharePwd: String = "",
        parentFileId: String = "0",
        page: Int = 1,
        limit: Int = 100
    ): List<ShareFile>? {
        val url = buildString {
            append(Pan123Constants.SHARE_INFO_URL)
            append("?shareKey=").append(shareKey)
            append("&SharePwd=").append(sharePwd)
            append("&parentFileId=").append(parentFileId)
            append("&page=").append(page)
            append("&limit=").append(limit)
        }

        val response = client.get(url) {
            header(HttpHeaders.UserAgent, Pan123Constants.USER_AGENT)
            header("Platform", "web")
            header("App-Version", "3")
        }

        val bodyStr = response.bodyAsText()
        if (bodyStr.isBlank()) return null
        val json = YunXJson.parseToJsonElement(bodyStr).obj ?: return null
        val code = json["code"].intOr()
        if (code != 0) {
            val msg = json["message"].strOrNull() ?: "123云盘错误 (code=$code)"
            throw QuarkApiException(msg, code)
        }

        val data = json["data"].obj ?: return null
        val infoList = data["InfoList"].arr ?: return emptyList()

        return infoList.mapNotNull { itemElem ->
            val item = itemElem.obj ?: return@mapNotNull null
            val fileId = item["FileId"].strOrNull() ?: item["fileId"].strOrNull() ?: return@mapNotNull null
            val fileName = item["FileName"].str().ifEmpty { item["filename"].str() }
            val size = item["Size"].longOr().takeIf { it > 0 } ?: item["size"].longOr()
            val type = item["Type"].intOr()
            val isDir = type == 1 || item["dir"].boolOr()
            val pdir = item["ParentFileId"].str()
            val updateAt = item["UpdateAt"].str()

            ShareFile(
                fid = fileId,
                fname = fileName,
                fsize = size,
                isdir = isDir,
                pdirFid = pdir,
                fidToken = "",
                modifyTime = updateAt
            )
        }
    }

    /** 获取下载直链 */
    suspend fun getDownloadLink(
        shareKey: String,
        fileId: String,
        s3keyFlag: String = "",
        sharePwd: String = ""
    ): DownloadLink? {
        val url = buildString {
            append(Pan123Constants.SHARE_DOWNLOAD_URL)
            append("?shareKey=").append(shareKey)
            append("&fileId=").append(fileId)
            append("&s3keyFlag=").append(s3keyFlag)
            append("&SharePwd=").append(sharePwd)
        }

        val response = client.get(url) {
            header(HttpHeaders.UserAgent, Pan123Constants.USER_AGENT)
            header("Platform", "web")
            header("App-Version", "3")
        }

        val bodyStr = response.bodyAsText()
        if (bodyStr.isBlank()) return null
        val json = YunXJson.parseToJsonElement(bodyStr).obj ?: return null
        val code = json["code"].intOr()
        if (code != 0) {
            val msg = json["message"].strOrNull() ?: "获取123下载直链失败 (code=$code)"
            throw QuarkApiException(msg, code)
        }

        val data = json["data"].obj ?: return null
        val downloadUrl = data["DownloadUrl"].str().ifEmpty { data["downloadUrl"].str() }

        return DownloadLink(
            fid = fileId,
            filename = data["FileName"].str(),
            downloadUrl = downloadUrl,
            size = data["Size"].longOr()
        )
    }
}
