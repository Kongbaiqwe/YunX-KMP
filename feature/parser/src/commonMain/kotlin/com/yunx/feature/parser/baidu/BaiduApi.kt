package com.yunx.feature.parser.baidu

import com.yunx.core.model.ShareFile
import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.core.network.YunXJson
import com.yunx.feature.parser.arr
import com.yunx.feature.parser.intOr
import com.yunx.feature.parser.longOr
import com.yunx.feature.parser.obj
import com.yunx.feature.parser.str
import com.yunx.feature.parser.strOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.concurrent.Volatile

/**
 * 百度网盘 API 封装（KMP / Ktor 3.x + kotlinx.serialization）。
 */
class BaiduApi(
    private val clientProvider: () -> HttpClient = { YunXHttpClientFactory.createHttpClient() }
) {
    private val client: HttpClient get() = clientProvider()

    @Volatile
    private var cachedBdstoken: String? = null

    // ---------- 账号 ----------

    suspend fun fetchNickname(cookie: String): String? = runCatching {
        val result = templateVariable(cookie, """["username"]""") ?: return null
        result["username"].strOrNull()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    suspend fun getBdstoken(cookie: String): String? {
        cachedBdstoken?.takeIf { it.isNotBlank() }?.let { return it }
        val result = templateVariable(cookie, """["bdstoken"]""") ?: return null
        val token = result["bdstoken"].strOrNull()?.takeIf { it.isNotBlank() } ?: return null
        cachedBdstoken = token
        return token
    }

    private suspend fun templateVariable(cookie: String, fields: String): JsonObject? = runCatching {
        val url = "https://pan.baidu.com/api/gettemplatevariable" +
            "?clienttype=0&app_id=${BaiduConstants.APP_ID}&web=1&fields=" +
            fields.encodeURLParameter()
        val response = client.get(url) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, BaiduConstants.UA_WEB)
        }
        if (!response.status.isSuccess()) return null
        val json = YunXJson.parseToJsonElement(response.bodyAsText()).obj ?: return null
        if (json["errno"].intOr() != 0) return null
        json["result"]?.obj
    }.getOrNull()

    // ---------- 分享解析 ----------

    /**
     * 验证提取码：POST /share/verify，返回 randsk
     */
    suspend fun verifyShare(surl: String, pwd: String, cookie: String): String {
        val response = client.submitForm(
            url = "https://pan.baidu.com/share/verify?surl=$surl",
            formParameters = Parameters.build {
                append("pwd", pwd)
                append("vcode_str", "")
                append("vcode", "")
            }
        ) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, BaiduConstants.UA_WEB)
            header("Referer", "https://pan.baidu.com/s/$surl")
        }
        val json = YunXJson.parseToJsonElement(response.bodyAsText()).obj ?: throw BaiduApiException("解析验证响应失败")
        checkErrno(json, "验证百度提取码失败")
        return json["randsk"].strOrNull()?.takeIf { it.isNotBlank() }
            ?: throw BaiduApiException("未获取到百度分享提取码密钥 (randsk)")
    }

    /**
     * 列出分享文件
     */
    suspend fun listShare(
        surl: String,
        sekey: String,
        dir: String,
        cookie: String,
        page: Int = 1
    ): BaiduShareList {
        val isRoot = dir.isBlank() || dir == "/"
        val root = if (isRoot) "1" else "0"
        val sekeyPart = if (sekey.isNotBlank()) "&sekey=${sekey.encodeURLParameter()}" else ""
        val url = "https://pan.baidu.com/rest/2.0/xpan/share?method=list" +
            "&shorturl=$surl&page=$page&num=100&root=$root&dir=" +
            (if (dir.isBlank()) "/" else dir).encodeURLParameter() +
            sekeyPart

        val authCookie = if (sekey.isNotBlank() && !cookie.contains("BDCLND=")) {
            "$cookie; BDCLND=$sekey"
        } else {
            cookie
        }

        val response = client.get(url) {
            header(HttpHeaders.Cookie, authCookie)
            header(HttpHeaders.UserAgent, BaiduConstants.UA_WEB)
            header("Referer", "https://pan.baidu.com/s/$surl")
        }

        val json = YunXJson.parseToJsonElement(response.bodyAsText()).obj ?: throw BaiduApiException("解析列表响应失败")
        val errno = json["errno"].intOr()
        if (errno != 0) {
            if (sekey.isBlank()) {
                val tip = if (errno == -6) "该分享需要登录或身份验证 (errno=$errno)" else "该分享需要提取码 (errno=$errno)"
                throw BaiduApiException(tip, errno)
            }
            checkErrno(json, "获取百度分享文件列表失败")
        }

        val array = json["list"].arr ?: JsonArray(emptyList())
        val files = array.mapNotNull { itemElem ->
            val item = itemElem.obj ?: return@mapNotNull null
            val isdir = item["isdir"].str() == "1"
            val path = item["path"].str()
            val fsId = item["fs_id"].str()
            val fname = item["server_filename"].str()
            val fsize = item["size"].longOr()
            val modifyTime = item["server_mtime"].str()

            ShareFile(
                fid = if (isdir) path else fsId,
                fname = fname,
                fsize = fsize,
                isdir = isdir,
                pdirFid = path,
                fidToken = "",
                modifyTime = modifyTime
            )
        }

        return BaiduShareList(
            title = json["title"].str(),
            shareId = json["share_id"].str(),
            uk = json["uk"].str(),
            files = files
        )
    }

    /**
     * 创建目录
     */
    suspend fun createDir(path: String, cookie: String): Boolean = runCatching {
        val bdstoken = getBdstoken(cookie) ?: return false
        val response = client.submitForm(
            url = "https://pan.baidu.com/api/create?a=commit&channel=chunlei&web=1" +
                "&app_id=${BaiduConstants.APP_ID}&clienttype=0&bdstoken=$bdstoken",
            formParameters = Parameters.build {
                append("path", path)
                append("isdir", "1")
                append("size", "")
                append("block_list", "[]")
                append("method", "post")
                append("dataType", "json")
            }
        ) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, BaiduConstants.UA_NETDISK)
            header("Referer", "https://yun.baidu.com/disk/main")
        }
        val json = YunXJson.parseToJsonElement(response.bodyAsText()).obj ?: return false
        json["errno"].intOr() == 0
    }.getOrDefault(false)

    /**
     * 转存分享文件到个人网盘目录
     */
    suspend fun transfer(
        shareId: String,
        uk: String,
        sekey: String,
        fsId: String,
        toDir: String,
        cookie: String
    ): BaiduTransferResult {
        val bdstoken = getBdstoken(cookie)
            ?: throw BaiduApiException("获取 bdstoken 失败")

        val url = "https://pan.baidu.com/share/transfer?shareid=$shareId&from=$uk" +
            "&channel=chunlei&sekey=$sekey&ondup=newcopy&web=1&app_id=${BaiduConstants.APP_ID}" +
            "&bdstoken=$bdstoken&clienttype=0"

        val authCookie = if (cookie.contains("BDCLND=")) cookie else "$cookie; BDCLND=$sekey"
        val response = client.submitForm(
            url = url,
            formParameters = Parameters.build {
                append("fsidlist", "[\"$fsId\"]")
                append("path", toDir)
            }
        ) {
            header(HttpHeaders.Cookie, authCookie)
            header(HttpHeaders.UserAgent, BaiduConstants.UA_WEB)
            header("Origin", "https://pan.baidu.com")
            header("Referer", "https://pan.baidu.com/s/")
        }

        val json = YunXJson.parseToJsonElement(response.bodyAsText()).obj ?: throw BaiduApiException("解析转存响应失败")
        checkErrno(json, "转存百度分享文件失败")

        val extra = json["extra"]?.obj
        val first = extra?.get("list")?.arr?.firstOrNull()?.obj
        val fsIdNew = first?.get("to_fs_id").strOrNull()?.takeIf { it.isNotBlank() }
            ?: throw BaiduApiException("转存未返回目标文件 fs_id")
        val pathNew = first?.get("to").strOrNull()?.takeIf { it.isNotBlank() } ?: "$toDir/"

        return BaiduTransferResult(fsId = fsIdNew, path = pathNew)
    }

    /**
     * 获取高速下载直链（locatedownload 接口）
     */
    suspend fun locateDownload(path: String, cookie: String): String {
        val time = "1700000000"
        val url = "https://d.pcs.baidu.com/rest/2.0/pcs/file" +
            "?method=locatedownload" +
            "&app_id=${BaiduConstants.APP_ID}" +
            "&clienttype=17&ver=4.0" +
            "&ant=1&check_blue=1&es=1&esl=1&apn_id=1_-1" +
            "&freeisp=0&queryfree=0&use=1&dtype=1&eck=1&ehps=1" +
            "&err_ver=1.0&network_type=WIFI&channel=0" +
            "&path=${path.encodeURLParameter()}" +
            "&time=$time" +
            "&rand=5ed606e9da222cde0474cdf70eda884b" +
            "&devuid=0F1E9FC2E084472DA5A61C4CF4C759AF" +
            "&cuid=0F1E9FC2E084472DA5A61C4CF4C759AF" +
            "&deviceid=348642637967375013" +
            "&psign=860a071f77c860e8cea06e4e54c518f3" +
            "&version=2.2.111.34&version_app=12.24.6&vip=0"

        val response = client.submitForm(
            url = url,
            formParameters = Parameters.build { append("0", "") }
        ) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, BaiduConstants.UA_NETDISK)
        }

        val json = YunXJson.parseToJsonElement(response.bodyAsText()).obj ?: throw BaiduApiException("解析高速直链响应失败")
        checkErrno(json, "获取百度高速直链失败")

        val urls = json["urls"].arr ?: JsonArray(emptyList())
        val candidates = urls.mapNotNull { it.obj }
            .filter { it["url"].strOrNull()?.isNotBlank() == true }

        val directUrl = candidates
            .filter { it["encrypt"].intOr(1) == 0 }
            .sortedBy { !it["url"].str().startsWith("https") }
            .firstOrNull()?.get("url").strOrNull()
            ?: candidates.firstOrNull { it["url"].str().startsWith("https") }?.get("url").strOrNull()
            ?: candidates.firstOrNull()?.get("url").strOrNull()
            ?: throw BaiduApiException("未获取到有效的百度下载链接")

        return directUrl
    }

    private fun checkErrno(json: JsonObject, defaultMessage: String) {
        val errno = json["errno"].intOr()
        if (errno != 0) {
            val errMsg = json["errmsg"].strOrNull()
                ?: json["show_msg"].strOrNull()
                ?: "$defaultMessage (errno=$errno)"
            throw BaiduApiException(errMsg, errno)
        }
    }
}
