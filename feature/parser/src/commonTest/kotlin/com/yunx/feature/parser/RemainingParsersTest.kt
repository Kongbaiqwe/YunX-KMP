package com.yunx.feature.parser

import com.yunx.feature.parser.c139.C139Api
import com.yunx.feature.parser.guangya.GuangYaApi
import com.yunx.feature.parser.lanzou.ILanzouApi
import com.yunx.feature.parser.lanzou.LanzouApi
import com.yunx.feature.parser.pan115.Pan115Api
import com.yunx.feature.parser.pan123.PureCrc32
import com.yunx.feature.parser.uc.UCApi
import com.yunx.feature.parser.xunlei.XunleiApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemainingParsersTest {

    @Test
    fun testPan115SharePageParsing() = runBlocking {
        val mockEngine = MockEngine { _ ->
            val json = """
                {
                    "state": true,
                    "data": {
                        "count": 2,
                        "share_info": {
                            "share_title": "测试115分享",
                            "user_id": "115user01",
                            "share_state": 1
                        },
                        "list": [
                            {"fid": "f101", "n": "video.mp4", "s": 1048576, "pc": "pick101"},
                            {"cid": "c201", "n": "Docs", "s": 0}
                        ]
                    }
                }
            """.trimIndent()
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = Pan115Api(HttpClient(mockEngine))
        val page = api.getSharePage(shareCode = "test_code")
        assertEquals(2, page.total)
        assertEquals("测试115分享", page.title)
        assertEquals(2, page.files.size)
        assertEquals("video.mp4", page.files[0].fname)
        assertTrue(page.files[1].isdir)
    }

    @Test
    fun testPan123Crc32() {
        val crcHex = PureCrc32.crc32Hex("123456789")
        assertEquals("cbf43926", crcHex.lowercase())
    }

    @Test
    fun testC139EncryptDecrypt() {
        val api = C139Api()
        val plain = """{"test":"yunx_c139_kmp"}"""
        val cipher = api.encryptShareBody(plain)
        assertTrue(cipher.isNotBlank())
        val decrypted = api.decryptShareBody(cipher)
        assertEquals(plain, decrypted)
    }

    @Test
    fun testUCApiFileList() = runBlocking {
        val mockEngine = MockEngine { _ ->
            val json = """
                {
                    "status": 0,
                    "data": {
                        "list": [
                            {"fid": "uc001", "file_name": "app.apk", "size": 204800, "is_dir": false}
                        ]
                    }
                }
            """.trimIndent()
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = UCApi { HttpClient(mockEngine) }
        val files = api.getShareFiles("uc_share_id", "uc_stoken", "0", "")
        assertNotNull(files)
        assertEquals(1, files.size)
        assertEquals("app.apk", files[0].fname)
    }

    @Test
    fun testXunleiFileList() = runBlocking {
        val mockEngine = MockEngine { _ ->
            val json = """
                {
                    "files": [
                        {"id": "xl001", "name": "movie.mkv", "size": "1073741824", "kind": "drive#file"}
                    ],
                    "next_page_token": ""
                }
            """.trimIndent()
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = XunleiApi { HttpClient(mockEngine) }
        val result = api.getShare("xl_code", "1234")
        assertNotNull(result)
        assertEquals(1, result.files.size)
        assertEquals("movie.mkv", result.files[0].fname)
    }

    @Test
    fun testGuangYaFiles() = runBlocking {
        val mockEngine = MockEngine { _ ->
            val json = """
                {
                    "code": 0,
                    "data": {
                        "total": 1,
                        "list": [
                            {"file_id": "gy001", "file_name": "doc.pdf", "file_size": 12345, "type": 0}
                        ]
                    }
                }
            """.trimIndent()
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = GuangYaApi(HttpClient(mockEngine))
        val result = api.getShareFiles("gy_code", "gy_token")
        assertEquals(1, result.files.size)
        assertEquals("doc.pdf", result.files[0].fname)
    }

    @Test
    fun testILanzouFiles() = runBlocking {
        val mockEngine = MockEngine { _ ->
            val json = """
                {
                    "code": 200,
                    "data": {
                        "list": [
                            {"fileId": "lz001", "fileName": "archive.zip", "fileSize": 500000}
                        ]
                    }
                }
            """.trimIndent()
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = ILanzouApi(HttpClient(mockEngine))
        val result = api.getShareFiles("lz_share_id")
        assertEquals(1, result.files.size)
        assertEquals("archive.zip", result.files[0].fname)
    }
}
