package com.yunx.feature.parser

import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.feature.parser.quark.QuarkApi
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

class QuarkApiTest {

    @Test
    fun testGetShareTokenAndFiles() = runBlocking {
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("sharepage/token") -> {
                    respond(
                        content = """
                            {
                                "status": 200,
                                "code": 0,
                                "message": "ok",
                                "data": {
                                    "stoken": "mock_stoken_123",
                                    "title": "测试夸克分享",
                                    "first_fid": "fid_root_1"
                                }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("sharepage/detail") -> {
                    respond(
                        content = """
                            {
                                "status": 200,
                                "code": 0,
                                "data": {
                                    "list": [
                                        {
                                            "fid": "fid_file_001",
                                            "file_name": "Antigravity-Manual.pdf",
                                            "size": 1048576,
                                            "dir": false,
                                            "pdir_fid": "0",
                                            "share_fid_token": "token_abc",
                                            "updated_at": "2026-11-16 12:00:00"
                                        },
                                        {
                                            "fid": "fid_folder_002",
                                            "file_name": "Videos",
                                            "size": 0,
                                            "dir": true,
                                            "pdir_fid": "0",
                                            "share_fid_token": "token_folder",
                                            "updated_at": "2026-11-16 12:05:00"
                                        }
                                    ]
                                }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("file/download") -> {
                    respond(
                        content = """
                            {
                                "status": 200,
                                "code": 0,
                                "data": [
                                    {
                                        "fid": "fid_file_001",
                                        "file_name": "Antigravity-Manual.pdf",
                                        "download_url": "https://dl.quark.cn/download/test_direct_url",
                                        "size": 1048576
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
        val api = QuarkApi(clientProvider = { client })

        // 1. 验证获取分享 Token
        val token = api.getShareToken("Abc1234", "1234", "test_cookie")
        assertNotNull(token)
        assertEquals("mock_stoken_123", token.stoken)
        assertEquals("测试夸克分享", token.title)

        // 2. 验证获取分享文件目录列表
        val files = api.getShareFiles("Abc1234", token.stoken, "0", "test_cookie")
        assertNotNull(files)
        assertEquals(2, files.size)

        val file1 = files[0]
        assertEquals("fid_file_001", file1.fid)
        assertEquals("Antigravity-Manual.pdf", file1.fname)
        assertEquals(1048576L, file1.fsize)
        assertFalse(file1.isdir)

        val file2 = files[1]
        assertEquals("fid_folder_002", file2.fid)
        assertEquals("Videos", file2.fname)
        assertTrue(file2.isdir)

        // 3. 验证获取下载直链
        val downloadLink = api.getDownloadLink("fid_file_001", "test_cookie")
        assertNotNull(downloadLink)
        assertEquals("https://dl.quark.cn/download/test_direct_url", downloadLink.downloadUrl)
        assertEquals(1048576L, downloadLink.size)
    }
}
