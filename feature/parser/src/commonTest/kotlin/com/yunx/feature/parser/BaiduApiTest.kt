package com.yunx.feature.parser

import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.feature.parser.baidu.BaiduApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BaiduApiTest {

    @Test
    fun testBaiduVerifyAndListAndLocateDownload() = runBlocking {
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("share/verify") -> {
                    respond(
                        content = """
                            {
                                "errno": 0,
                                "errmsg": "ok",
                                "randsk": "test_randsk_secret"
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("xpan/share?method=list") -> {
                    respond(
                        content = """
                            {
                                "errno": 0,
                                "title": "测试百度分享",
                                "share_id": "999888",
                                "uk": "12345678",
                                "list": [
                                    {
                                        "fs_id": "111222333",
                                        "server_filename": "Document.docx",
                                        "size": 20480,
                                        "isdir": "0",
                                        "path": "/share/Document.docx",
                                        "server_mtime": "1700000000"
                                    },
                                    {
                                        "fs_id": "444555666",
                                        "server_filename": "Music",
                                        "size": 0,
                                        "isdir": "1",
                                        "path": "/share/Music",
                                        "server_mtime": "1700000000"
                                    }
                                ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("method=locatedownload") -> {
                    respond(
                        content = """
                            {
                                "errno": 0,
                                "urls": [
                                    {
                                        "url": "https://d2-ant.baidu.com/encrypt_channel",
                                        "encrypt": 1,
                                        "rank": 1
                                    },
                                    {
                                        "url": "https://appall01.baidupcs.com/file/direct_plain_url",
                                        "encrypt": 0,
                                        "rank": 2
                                    }
                                ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("{}", HttpStatusCode.NotFound)
            }
        }

        val client = YunXHttpClientFactory.createHttpClient(engine = mockEngine)
        val api = BaiduApi(clientProvider = { client })

        // 1. 验证提取码
        val randsk = api.verifyShare("surl123", "9xYz", "test_cookie")
        assertEquals("test_randsk_secret", randsk)

        // 2. 验证分享列表
        val shareList = api.listShare("surl123", randsk, "/", "test_cookie")
        assertEquals("测试百度分享", shareList.title)
        assertEquals("999888", shareList.shareId)
        assertEquals("12345678", shareList.uk)
        assertEquals(2, shareList.files.size)

        val doc = shareList.files[0]
        assertEquals("111222333", doc.fid)
        assertEquals("Document.docx", doc.fname)
        assertEquals(20480L, doc.fsize)
        assertFalse(doc.isdir)

        val music = shareList.files[1]
        assertEquals("/share/Music", music.fid)
        assertEquals("Music", music.fname)
        assertTrue(music.isdir)

        // 3. 验证直链获取（自动过滤掉 encrypt=1 加密通道，选中 encrypt=0 明文通道）
        val directUrl = api.locateDownload("/share/Document.docx", "test_cookie")
        assertEquals("https://appall01.baidupcs.com/file/direct_plain_url", directUrl)
    }
}
