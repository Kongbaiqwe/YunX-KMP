package com.yunx.feature.parser

import com.yunx.feature.parser.lanzou.LanzouApi
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

class LanzouIframeFlowTest {

    private val iframeTopHtml = """
        <!DOCTYPE html>
        <html>
        <head>
        <title>inphic.game.mouse.pw1.rar - 蓝奏云</title>
        <meta name="description" content="文件大小：17.3 M" />
        </head>
        <body>
        <iframe class="ifr2" src="/fn?VTNaMApjAWxU" frameborder="0"></iframe>
        <script type="text/javascript">var fid = 49951122;</script>
        </body>
        </html>
    """.trimIndent()

    private val iframeHtml = """
        <html><body>
        <script type="text/javascript">
        var down_1 = '';
        var down_2 = '';
        var down_3 = '&toolsdown';
        var wp_sign = 'WPSIGN0001';
        var ajaxdata = 'yE3Z';
        var kdns =1;
        var domain1 = 'https://apifile.test/ajaxfile.php?file=49951122';
        var domain2 = 'https://apifile2.test/ajaxfile.php?file=49951122';
        </script>
        </body></html>
    """.trimIndent()

    private val verifyHtml = """
        <html><body>
        <div>系统发现您的网络异常，需要验证后下载文件</div>
        <script type="text/javascript">
        function down_r(el){
            post_ajax({
                data : { 'file':'FILETOKEN0001','el':el,'sign':'SIGNTOKEN0001==' }
            });
        }
        </script>
        </body></html>
    """.trimIndent()

    private val ajaxmTopHtml = """
        <html><head>
        <title>legacy.bin - 蓝奏云</title>
        <meta name="description" content="文件大小：1 K" />
        </head><body>
        <script type="text/javascript">
        var sign = 'LEGACYSIGN';
        var url = '/ajaxm.php?file=123';
        </script>
        </body></html>
    """.trimIndent()

    private fun engine(): MockEngine = MockEngine { request ->
        val url = request.url.toString()
        val range = request.headers["Range"]
        when {
            url == "https://share.test/abc" ->
                respond(iframeTopHtml, HttpStatusCode.OK, htmlHeaders())
            url.startsWith("https://share.test/fn?") ->
                respond(iframeHtml, HttpStatusCode.OK, htmlHeaders())
            url.startsWith("https://apifile.test/") ->
                respond(
                    """{"zt":1,"dom":"https://dl.test","url":"?TOKEN","inf":0}""",
                    HttpStatusCode.OK,
                    jsonHeaders()
                )
            url == "https://dl.test/file/?TOKEN" ->
                respond(verifyHtml, HttpStatusCode.OK, htmlHeaders())
            url == "https://dl.test/file/ajax.php" ->
                respond(
                    """{"zt":1,"url":"https://zip.test/file.rar?fn=x.rar"}""",
                    HttpStatusCode.OK,
                    jsonHeaders()
                )
            url.startsWith("https://zip.test/") && range == "bytes=0-0" ->
                respond(
                    ByteArray(1),
                    HttpStatusCode.PartialContent,
                    headersOf(
                        HttpHeaders.ContentType to listOf("application/octet-stream"),
                        "Content-Range" to listOf("bytes 0-0/18142841")
                    )
                )
            url == "https://share2.test/xyz" ->
                respond(ajaxmTopHtml, HttpStatusCode.OK, htmlHeaders())
            url.startsWith("https://share2.test/ajaxm.php") ->
                respond(
                    """{"zt":1,"dom":"https://dl2.test","url":"TOKEN2","inf":"legacy.bin"}""",
                    HttpStatusCode.OK,
                    jsonHeaders()
                )
            else ->
                respond("not found: $url", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "text/plain"))
        }
    }

    private fun htmlHeaders() = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun testIframeAjaxfileFlow() = runBlocking {
        val api = LanzouApi(HttpClient(engine()), verifyDelayMillis = 0)
        val link = api.resolveDownloadLink("https://share.test/abc")

        assertNotNull(link, "iframe 流程应解析出下载模型")
        assertEquals("https://zip.test/file.rar?fn=x.rar", link.downloadUrl)
        assertEquals("inphic.game.mouse.pw1.rar", link.filename)
        assertEquals("49951122", link.fid)
        assertEquals(18140364L, link.size, "17.3 M 应换算成字节")
    }

    @Test
    fun testAjaxmFlowStillWorks() = runBlocking {
        val api = LanzouApi(HttpClient(engine()), verifyDelayMillis = 0)
        val link = api.resolveDownloadLink("https://share2.test/xyz")

        assertNotNull(link, "旧版 ajaxm 流程不应被 iframe 改动破坏")
        assertEquals("https://dl2.test/file/TOKEN2", link.downloadUrl)
        assertEquals("legacy.bin", link.filename)
        assertEquals(1024L, link.size)
    }
}
