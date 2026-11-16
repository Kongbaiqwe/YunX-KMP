package com.yunx.feature.parser.c139

import com.yunx.core.common.PlatformCrypto
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
import kotlin.random.Random

data class C139SharePage(
    val files: List<ShareFile>,
    val total: Int,
    val title: String
)

class C139Api(
    private val client: HttpClient = YunXHttpClientFactory.createHttpClient()
) {
    private val shareKeyBytes = C139Constants.SHARE_AES_KEY.encodeToByteArray()

    fun encryptShareBody(plainText: String): String {
        val iv = Random.nextBytes(16)
        val encrypted = PlatformCrypto.aesCbcEncrypt(shareKeyBytes, iv, plainText.encodeToByteArray())
        val combined = ByteArray(16 + encrypted.size)
        iv.copyInto(combined, 0, 0, 16)
        encrypted.copyInto(combined, 16, 0, encrypted.size)
        return PlatformCrypto.base64Encode(combined)
    }

    fun decryptShareBody(base64Cipher: String): String {
        val raw = PlatformCrypto.base64Decode(base64Cipher)
        if (raw.size <= 16) return ""
        val iv = raw.copyOfRange(0, 16)
        val cipher = raw.copyOfRange(16, raw.size)
        val decrypted = PlatformCrypto.aesCbcDecrypt(shareKeyBytes, iv, cipher)
        return decrypted.decodeToString()
    }

    suspend fun getShareFiles(
        linkId: String,
        password: String = "",
        pCaId: String = "root"
    ): C139SharePage {
        val requestBody = """{"linkId":"$linkId","passwd":"$password","pCaID":"$pCaId"}"""
        val encryptedReq = encryptShareBody(requestBody)

        val response = client.post(C139Constants.SHARE_LIST_URL) {
            header("User-Agent", C139Constants.USER_AGENT)
            header("Referer", C139Constants.REFERER)
            contentType(ContentType.Application.Json)
            setBody(encryptedReq)
        }

        val text = response.bodyAsText()
        val plain = decryptShareBody(text)
        val json = YunXJson.decodeFromString<JsonObject>(plain)
        val data = json["data"]?.obj ?: return C139SharePage(emptyList(), 0, "")

        val list = data["getOutLinkInfoV6"]?.obj?.get("caLst")?.arr ?: emptyList()
        val files = list.mapNotNull { item ->
            val obj = item.obj ?: return@mapNotNull null
            val isDir = obj["isDir"].strOrNull() == "1" || obj["caID"] != null
            val fid = obj["caID"].strOrNull() ?: obj["coID"].strOrNull().orEmpty()
            val name = obj["caName"].strOrNull() ?: obj["coName"].strOrNull().orEmpty()
            val size = obj["size"].longOr(0L)
            ShareFile(
                fid = fid,
                fname = name,
                fsize = size,
                isdir = isDir,
                fidToken = linkId
            )
        }
        return C139SharePage(files, files.size, "")
    }

    suspend fun getDownloadLink(
        linkId: String,
        coId: String,
        password: String = ""
    ): DownloadLink? {
        val requestBody = """{"linkId":"$linkId","passwd":"$password","coIDLst":["$coId"]}"""
        val encryptedReq = encryptShareBody(requestBody)

        val response = client.post(C139Constants.SHARE_LINK_URL) {
            header("User-Agent", C139Constants.USER_AGENT)
            header("Referer", C139Constants.REFERER)
            contentType(ContentType.Application.Json)
            setBody(encryptedReq)
        }

        val text = response.bodyAsText()
        val plain = decryptShareBody(text)
        val json = YunXJson.decodeFromString<JsonObject>(plain)
        val dlObj = json["data"]?.obj?.get("dlFromOutLinkV3")?.obj
        val redrUrl = dlObj?.get("redrUrl").strOrNull() ?: return null

        return DownloadLink(
            fid = coId,
            filename = dlObj?.get("coName").strOrNull().orEmpty(),
            downloadUrl = redrUrl,
            size = dlObj?.get("size").longOr(0L),
            guestCookie = ""
        )
    }
}
