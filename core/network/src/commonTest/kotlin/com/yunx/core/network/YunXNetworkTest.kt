package com.yunx.core.network

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
data class MockTestResponse(
    val code: Int,
    val message: String,
    val timestamp: Long = 0L
)

@Serializable
data class HttpBinGetResponse(
    val url: String = "",
    val origin: String = ""
)

class YunXNetworkTest {

    @Test
    fun testJsonIgnoreUnknownKeys() {
        val rawJson = """
            {
                "code": 200,
                "message": "success",
                "extra_server_field_that_is_unknown": "hello_kmp",
                "nested_unknown": {"id": 123}
            }
        """.trimIndent()

        val parsed = YunXJson.decodeFromString<MockTestResponse>(rawJson)
        assertEquals(200, parsed.code)
        assertEquals("success", parsed.message)
        assertEquals(0L, parsed.timestamp) // 默认值生效
    }

    @Test
    fun testMockHttpClientJsonParsing() = runBlocking {
        val mockEngine = MockEngine { request ->
            val userAgent = request.headers[HttpHeaders.UserAgent]
            assertTrue(userAgent != null && userAgent.contains("YunX"), "User-Agent header must be present")

            respond(
                content = """{"code":0,"message":"ok","timestamp":1700000000000,"random_field":"ignored"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = YunXHttpClientFactory.createHttpClient(engine = mockEngine)
        val response = client.get("https://api.yunx.mock/test").body<MockTestResponse>()

        assertEquals(0, response.code)
        assertEquals("ok", response.message)
        assertEquals(1700000000000L, response.timestamp)
        client.close()
    }

    @Test
    fun testRealNetworkRequestIfAvailable() = runBlocking {
        // 使用实际底层引擎（JVM 下为 OkHttp）
        val client = YunXHttpClientFactory.createHttpClient()
        try {
            val response = client.get("https://httpbin.org/get")
            if (response.status == HttpStatusCode.OK) {
                val data = response.body<HttpBinGetResponse>()
                assertNotNull(data.url)
                println("[+] 真实公开网络接口测试成功: url=${data.url}, status=${response.status}")
            }
        } catch (e: Exception) {
            println("[-] 真实网络请求未达或环境离线 (非致命): ${e.message}")
        } finally {
            client.close()
        }
    }
}
