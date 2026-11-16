package com.yunx.feature.download

import com.yunx.core.database.entity.DownloadTaskEntity
import com.yunx.core.database.getAppDatabaseBuilder
import com.yunx.feature.download.core.ChunkDownloader
import com.yunx.feature.download.core.DownloadManager
import com.yunx.feature.download.core.HlsDownloader
import com.yunx.feature.download.engine.EngineTaskParams
import com.yunx.feature.download.engine.GopeedBridge
import com.yunx.feature.download.engine.GopeedDownloadEngine
import com.yunx.feature.download.engine.UrlSessionDownloadEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadEngineIntegrationTest {

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "yunx_engine_test_${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun testNormalChunkDownloadRegression() {
        runBlocking {
            val dir = tempDir()
            val targetFile = File(dir, "sample.bin")
            val dummyData = ByteArray(1024) { (it % 128).toByte() }

            val mockClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        val range = request.headers["Range"]
                        if (range == "bytes=0-0") {
                            respond(
                                content = dummyData.copyOfRange(0, 1),
                                status = HttpStatusCode.PartialContent,
                                headers = headersOf(
                                    HttpHeaders.ContentRange to listOf("bytes 0-0/1024"),
                                    HttpHeaders.ContentType to listOf("application/octet-stream")
                                )
                            )
                        } else if (range == "bytes=0-1023") {
                            respond(
                                content = dummyData,
                                status = HttpStatusCode.PartialContent,
                                headers = headersOf(
                                    HttpHeaders.ContentRange to listOf("bytes 0-1023/1024"),
                                    HttpHeaders.ContentType to listOf("application/octet-stream")
                                )
                            )
                        } else {
                            respond(
                                content = dummyData,
                                status = HttpStatusCode.OK,
                                headers = headersOf(
                                    HttpHeaders.ContentLength to listOf("1024"),
                                    HttpHeaders.ContentType to listOf("application/octet-stream")
                                )
                            )
                        }
                    }
                }
            }

            try {
                val db = getAppDatabaseBuilder(null).build()
                val dao = db.downloadTaskDao()
                val chunkDownloader = ChunkDownloader { mockClient }
                val manager = DownloadManager(
                    dao = dao,
                    downloader = chunkDownloader,
                    saveDirProvider = { dir.absolutePath }
                )

                val taskId = manager.enqueue(
                    url = "https://example.com/files/sample.bin",
                    platform = "generic",
                    fileName = "sample.bin",
                    totalSize = 1024L,
                    autoStart = true
                )

                // 等待协程执行完成
                var attempts = 0
                while (attempts < 20) {
                    val task: DownloadTaskEntity? = dao.get(taskId)
                    if (task?.status == DownloadTaskEntity.STATUS_COMPLETED) break
                    delay(100)
                    attempts++
                }

                val finalTask: DownloadTaskEntity? = dao.get(taskId)
                assertNotNull(finalTask)
                assertEquals(DownloadTaskEntity.STATUS_COMPLETED, finalTask?.status)
                assertEquals(1024L, finalTask?.downloadedSize)
                assertTrue("目标文件必须落盘存在", targetFile.exists())
                assertEquals(1024L, targetFile.length())
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun testHlsDownloadRegression() {
        runBlocking {
            val dir = tempDir()
            val destFile = File(dir, "video.ts")

            val masterM3u8 = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-STREAM-INF:BANDWIDTH=1280000
                https://media.example.com/media.m3u8
            """.trimIndent()

            val mediaM3u8 = """
                #EXTM3U
                #EXT-X-TARGETDURATION:10
                #EXTINF:10.0,
                segment1.ts
                #EXTINF:10.0,
                segment2.ts
                #EXT-X-ENDLIST
            """.trimIndent()

            val seg1 = ByteArray(512) { 1 }
            val seg2 = ByteArray(512) { 2 }

            val mockClient = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        when (request.url.toString()) {
                            "https://media.example.com/master.m3u8" -> {
                                respond(masterM3u8, HttpStatusCode.OK)
                            }
                            "https://media.example.com/media.m3u8" -> {
                                respond(mediaM3u8, HttpStatusCode.OK)
                            }
                            "https://media.example.com/segment1.ts" -> {
                                respond(seg1, HttpStatusCode.OK)
                            }
                            "https://media.example.com/segment2.ts" -> {
                                respond(seg2, HttpStatusCode.OK)
                            }
                            else -> error("Unhandled ${request.url}")
                        }
                    }
                }
            }

            try {
                val hlsDownloader = HlsDownloader { mockClient }
                var reportedBytes = 0L
                val success = hlsDownloader.download(
                    url = "https://media.example.com/master.m3u8",
                    headers = mapOf("User-Agent" to "YunX"),
                    destPath = destFile.absolutePath
                ) { bytes ->
                    reportedBytes += bytes
                }

                assertTrue("HLS 流式下载必须成功", success)
                assertTrue("HLS 输出文件必须存在", destFile.exists())
                assertEquals(1024L, destFile.length())
                assertEquals(1024L, reportedBytes)
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun testDownloadEngineInterfaces() {
        runBlocking {
            // 验证 GopeedDownloadEngine 接口
            GopeedBridge.enableMockMode(true)
            val gopeedEngine = GopeedDownloadEngine()
            assertTrue(gopeedEngine.isAvailable())
            assertTrue(gopeedEngine.startEngine())

            val engineTaskId = gopeedEngine.createTask(
                EngineTaskParams(
                    taskId = 1001L,
                    url = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567",
                    saveDir = "/downloads",
                    fileName = "test.iso"
                )
            )
            assertNotNull(engineTaskId)
            assertTrue(engineTaskId.isNotEmpty())

            val status = gopeedEngine.getTaskStatus(engineTaskId)
            assertNotNull(status)
            assertEquals("running", status?.status)
            assertTrue(gopeedEngine.pauseTask(engineTaskId))
            assertTrue(gopeedEngine.resumeTask(engineTaskId))
            assertTrue(gopeedEngine.deleteTask(engineTaskId))

            // 验证 UrlSessionDownloadEngine 接口
            val urlSessionEngine = UrlSessionDownloadEngine()
            assertTrue(urlSessionEngine.isAvailable())
            val usId = urlSessionEngine.createTask(
                EngineTaskParams(
                    taskId = 1002L,
                    url = "https://example.com/file.zip",
                    saveDir = "/downloads",
                    fileName = "file.zip",
                    totalSize = 4096L
                )
            )
            val usStatus = urlSessionEngine.getTaskStatus(usId)
            assertEquals("wait", usStatus?.status)
            assertTrue(urlSessionEngine.resumeTask(usId))
            assertEquals("running", urlSessionEngine.getTaskStatus(usId)?.status)
            assertTrue(urlSessionEngine.deleteTask(usId))
        }
    }
}
