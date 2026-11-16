package com.yunx.feature.parser.quark

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
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 夸克网盘 API 封装（KMP / Ktor 3.x + kotlinx.serialization）。
 */
class QuarkApi(
    private val clientProvider: () -> HttpClient = { YunXHttpClientFactory.createHttpClient() }
) {
    private val client: HttpClient get() = clientProvider()

    var cookieSink: ((merged: String, requestCookie: String) -> Unit)? = null

    var guestPugs: String = ""
        private set

    // ---------- 账号 ----------

    suspend fun fetchNickname(cookie: String): String? = runCatching {
        val response = client.get(QuarkConstants.ACCOUNT_INFO_URL) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, QuarkConstants.API_USER_AGENT)
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
        val response = postJson(QuarkConstants.SHARE_TOKEN_URL, cookie, bodyObj.toString())
        return parseData(response) { data ->
            val dataObj = data.obj ?: return@parseData null
            ShareToken(
                stoken = dataObj["stoken"].str(),
                title = dataObj["title"].str(),
                firstFid = dataObj["first_fid"].str()
            )
        }
    }

    /** 验证分享提取码 */
    suspend fun verifySharePassword(shareId: String, passcode: String, cookie: String): Boolean = runCatching {
        val bodyObj = buildJsonObject {
            put("share_id", shareId)
            put("passcode", passcode)
        }
        val response = postJson(QuarkConstants.SHARE_PASSWORD_URL, cookie, bodyObj.toString())
        val json = YunXJson.parseToJsonElement(response.bodyAsText()).obj
        json?.get("status").intOr() == 200
    }.getOrDefault(false)

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
            append(QuarkConstants.SHARE_DETAIL_URL)
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
            header(HttpHeaders.UserAgent, QuarkConstants.API_USER_AGENT)
            header("Origin", "https://pan.quark.cn")
            header("Referer", "https://pan.quark.cn/")
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

    /** 个人网盘文件列表（带排序） */
    suspend fun listCloudFiles(
        pdirFid: String,
        cookie: String,
        page: Int = 1,
        size: Int = 100
    ): List<ShareFile>? {
        val all = mutableListOf<ShareFile>()
        var p = page

        val thumbnailAuthHeaders = mapOf(
            "Cookie" to cookie,
            "User-Agent" to QuarkConstants.API_USER_AGENT,
            "Referer" to "https://pan.quark.cn/"
        )
        val thumbnailHeaders = mapOf(
            "User-Agent" to QuarkConstants.API_USER_AGENT,
            "Referer" to "https://pan.quark.cn/"
        )

        while (true) {
            val url = buildString {
                append(QuarkConstants.CLOUD_FILE_SORT_URL)
                append("&uc_param_str=")
                append("&pdir_fid=").append(pdirFid)
                append("&_page=").append(p)
                append("&_size=").append(size)
                append("&_fetch_total=1")
                append("&_fetch_sub_dirs=0")
                append("&_sort=file_type:asc,updated_at:desc")
                append("&fetch_all_file=1")
                append("&fetch_risk_file_name=1")
            }

            val response = client.get(url) {
                header(HttpHeaders.Cookie, cookie)
                header(HttpHeaders.UserAgent, QuarkConstants.API_USER_AGENT)
                header("Origin", "https://pan.quark.cn")
                header("Referer", "https://pan.quark.cn/")
            }

            val files = parseData(response) { data ->
                val array = data.obj?.get("list").arr ?: JsonArray(emptyList())
                array.mapNotNull { itemElem ->
                    val item = itemElem.obj ?: return@mapNotNull null
                    val fid = item["fid"].strOrNull() ?: return@mapNotNull null
                    val fname = item["file_name"].str().ifEmpty { item["fname"].str() }
                    val fsize = item["size"].longOr()
                    val isdir = item["dir"].boolOr()
                    val pdir = item["pdir_fid"].str()
                    val modifyTime = item["updated_at"].str()
                    val thumbUrl = item["thumbnail"].str()

                    ShareFile(
                        fid = fid,
                        fname = fname,
                        fsize = fsize,
                        isdir = isdir,
                        pdirFid = pdir,
                        fidToken = "",
                        modifyTime = modifyTime,
                        thumbnail = FileThumbnail.of(
                            url = thumbUrl,
                            headers = thumbnailHeaders,
                            authHost = QuarkConstants.THUMBNAIL_HOST,
                            authHeaders = thumbnailAuthHeaders
                        )
                    )
                }
            } ?: emptyList()

            all += files
            if (files.size < size || p >= 100) break
            p++
        }
        return all.ifEmpty { null }
    }

    /** 个人网盘创建目录 */
    suspend fun createFolder(name: String, parentFid: String, cookie: String): String? {
        val bodyObj = buildJsonObject {
            put("pdir_fid", parentFid)
            put("file_name", name)
            put("dir_path", "")
            put("dir_init_lock", false)
        }
        val response = postJson(QuarkConstants.FILE_URL, cookie, bodyObj.toString())
        return parseData(response) { data ->
            data.obj?.get("fid").strOrNull()
        }
    }

    /** 转存分享文件到个人网盘目录 */
    suspend fun saveShareFile(
        shareId: String,
        stoken: String,
        pdirFid: String,
        fid: String,
        fidToken: String,
        toPdirFid: String,
        cookie: String
    ): String? {
        val bodyObj = buildJsonObject {
            put("pwd_id", shareId)
            put("stoken", stoken)
            put("pdir_fid", pdirFid)
            put("to_pdir_fid", toPdirFid)
            put("fid_list", buildJsonArray { add(JsonPrimitive(fid)) })
            put("fid_token_list", buildJsonArray { add(JsonPrimitive(fidToken)) })
            put("scene", "link")
        }
        val response = postJson(QuarkConstants.SAVE_URL, cookie, bodyObj.toString())
        return parseData(response) { data ->
            data.obj?.get("task_id").strOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    /** 轮询转存任务 */
    suspend fun pollTask(taskId: String, cookie: String): String? {
        val url = "${QuarkConstants.TASK_URL}&task_id=$taskId&retry_index=0"
        for (i in 0 until 10) {
            val savedFid = runCatching {
                val response = client.get(url) {
                    header(HttpHeaders.Cookie, cookie)
                    header(HttpHeaders.UserAgent, QuarkConstants.API_USER_AGENT)
                }
                val bodyStr = response.bodyAsText()
                val json = YunXJson.parseToJsonElement(bodyStr).obj ?: return@runCatching null
                if (json["status"].intOr() != 200) return@runCatching null
                val data = json["data"]?.obj ?: return@runCatching null
                val finished = data["finished_at"].longOr() > 0L ||
                    data["status"].intOr() == 2 ||
                    data["task_status"].intOr() == 2
                if (!finished) return@runCatching null
                data["save_as"]?.obj?.get("save_as_top_fids").arr?.firstOrNull().strOrNull()
            }.getOrNull()

            if (!savedFid.isNullOrBlank()) return savedFid
            delay(1000)
        }
        return null
    }

    /** 网盘空间详情 */
    suspend fun getQuota(cookie: String): QuotaInfo? = runCatching {
        val url = "https://drive-pc.quark.cn/1/clouddrive/member?pr=ucpro&fr=pc&fetch_subscribe=true&_ch=home"
        val response = client.get(url) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, QuarkConstants.API_USER_AGENT)
        }
        val bodyStr = response.bodyAsText()
        val data = YunXJson.parseToJsonElement(bodyStr).obj?.get("data").obj ?: return@runCatching null
        QuotaInfo(
            used = data["use_capacity"].longOr(),
            total = data["total_capacity"].longOr()
        )
    }.getOrNull()

    /** 获取下载直链 */
    suspend fun getDownloadLink(fid: String, cookie: String): DownloadLink? {
        val bodyObj = buildJsonObject {
            put("fids", buildJsonArray { add(JsonPrimitive(fid)) })
        }
        val response = postJson(QuarkConstants.DOWNLOAD_URL, cookie, bodyObj.toString())
        mergeCookieFromResponse(response, cookie)
        val bodyStr = response.bodyAsText()
        if (bodyStr.isBlank()) throw QuarkApiException("下载直链响应为空")
        val json = YunXJson.parseToJsonElement(bodyStr).obj ?: throw QuarkApiException("解析下载响应失败")
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

    // ---------- 内部 HTTP 工具 ----------

    private suspend fun postJson(url: String, cookie: String, jsonBody: String): HttpResponse {
        val response = client.post(url) {
            header(HttpHeaders.Cookie, cookie)
            header(HttpHeaders.UserAgent, QuarkConstants.API_USER_AGENT)
            header("Referer", QuarkConstants.DOWNLOAD_REFERER)
            header("Origin", "https://pan.quark.cn")
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
