package com.yunx.feature.download.core

import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.feature.download.policy.HttpRangePolicy
import com.yunx.feature.download.storage.PlatformFileWriter
import com.yunx.feature.download.storage.StoragePlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * 分片下载单次失败后的瞬时 IO 重试次数
 */
private const val CHUNK_RETRIES = 3

/**
 * 当服务器忽略 Range（返回 200 整文件）时，对单分片重试 Range 的次数
 */
private const val RANGE_RETRIES = 4

/**
 * 网络读缓冲：64KB
 */
const val BUFFER_SIZE = 64 * 1024

enum class ChunkResult { OK, RANGE_IGNORED, FAILED }

class PreemptedException : Exception("慢连接抢占")

/**
 * 跨平台分片下载器
 */
class ChunkDownloader(
    private val clientProvider: () -> HttpClient = { YunXHttpClientFactory.createHttpClient() }
) {
    val client: HttpClient get() = clientProvider()

    suspend fun getTotalSize(url: String, headers: Map<String, String>): Long? = withContext(Dispatchers.IO) {
        val withRange = probeSize(url, headers, withRange = true)
        if (withRange != null) return@withContext withRange
        probeSize(url, headers, withRange = false)
    }

    private suspend fun probeSize(url: String, headers: Map<String, String>, withRange: Boolean): Long? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = client.request(url) {
                    method = HttpMethod.Get
                    if (withRange) header("Range", "bytes=0-0")
                    headers.forEach { (k, v) -> header(k, v) }
                }
                val contentType = response.headers["Content-Type"].orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) return@runCatching null

                if (withRange) {
                    if (response.status != HttpStatusCode.PartialContent) return@runCatching null
                    val range = HttpRangePolicy.parse(response.headers["Content-Range"]) ?: return@runCatching null
                    if (range.start != 0L || range.end != 0L) return@runCatching null
                    range.total
                } else {
                    if (response.status != HttpStatusCode.OK) return@runCatching null
                    response.headers["Content-Length"]?.toLongOrNull()?.takeIf { it > 0 }
                }
            }.getOrNull()
        }

    suspend fun downloadChunk(
        taskId: Long,
        url: String,
        start: Long,
        end: Long,
        partFilePath: String,
        headers: Map<String, String>,
        isPreempted: () -> Boolean = { false },
        onBytes: suspend (Long) -> Unit
    ): ChunkResult = withContext(Dispatchers.IO) {
        val attempts = CHUNK_RETRIES + RANGE_RETRIES
        repeat(attempts) { attempt ->
            if (!isActive) throw CancellationException("下载被取消")
            val existing = StoragePlatform.fileLength(partFilePath)
            val from = start + existing
            val unknownTotal = end == Long.MAX_VALUE
            val expected = if (unknownTotal) -1L else end - start + 1
            if (!unknownTotal && existing >= expected) return@withContext ChunkResult.OK

            var wasPreempted = false
            val res = try {
                doChunkAttempt(url, from, end, unknownTotal, partFilePath, headers, existing, isPreempted, onBytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PreemptedException) {
                wasPreempted = true
                null
            } catch (e: Throwable) {
                if (isPreempted()) {
                    wasPreempted = true
                    null
                } else {
                    if (!isActive) throw CancellationException("下载被取消", e)
                    null
                }
            }

            when (res) {
                ChunkResult.OK -> return@withContext ChunkResult.OK
                ChunkResult.FAILED -> return@withContext ChunkResult.FAILED
                ChunkResult.RANGE_IGNORED -> return@withContext ChunkResult.RANGE_IGNORED
                null -> {
                    if (!wasPreempted && attempt < attempts - 1) {
                        delay((500L * (attempt + 1)).coerceAtMost(3000))
                    }
                }
            }
        }
        ChunkResult.FAILED
    }

    private suspend fun doChunkAttempt(
        url: String,
        from: Long,
        end: Long,
        unknownTotal: Boolean,
        partFilePath: String,
        headers: Map<String, String>,
        existing: Long,
        isPreempted: () -> Boolean,
        onBytes: suspend (Long) -> Unit
    ): ChunkResult {
        val response = client.request(url) {
            method = HttpMethod.Get
            header("Range", if (unknownTotal) "bytes=$from-" else "bytes=$from-$end")
            headers.forEach { (k, v) -> header(k, v) }
        }

        val contentType = response.headers["Content-Type"].orEmpty()
        if (contentType.contains("text/html", ignoreCase = true)) {
            return ChunkResult.FAILED
        }

        return when (response.status) {
            HttpStatusCode.PartialContent -> {
                val requestedEnd = if (unknownTotal) null else end
                if (!HttpRangePolicy.matches(response.headers["Content-Range"], from, requestedEnd)) {
                    return ChunkResult.FAILED
                }
                val expected = if (unknownTotal) -1L else end - from + 1
                val channel = response.bodyAsChannel()
                val written = writeSlice(channel, partFilePath, existing, expected, isPreempted, onBytes)
                if (isPreempted()) throw PreemptedException()
                if (!unknownTotal && written != expected) return ChunkResult.FAILED
                ChunkResult.OK
            }
            HttpStatusCode.OK -> {
                ChunkResult.RANGE_IGNORED
            }
            else -> {
                ChunkResult.FAILED
            }
        }
    }

    private suspend fun writeSlice(
        channel: ByteReadChannel,
        partFilePath: String,
        existing: Long,
        expected: Long,
        isPreempted: () -> Boolean,
        onBytes: suspend (Long) -> Unit
    ): Long {
        var written = 0L
        val writer: PlatformFileWriter = StoragePlatform.openWriter(partFilePath)
        try {
            writer.seek(existing)
            val buffer = ByteArray(BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                val allow = if (expected < 0) read.toLong() else min(read.toLong(), expected - written)
                if (allow <= 0) break
                writer.write(buffer, 0, allow.toInt())
                written += allow
                onBytes(allow)
                if (isPreempted()) break
                if (expected >= 0 && written >= expected) break
            }
        } finally {
            writer.close()
        }
        return written
    }

    suspend fun downloadFull(
        url: String,
        targetPath: String,
        headers: Map<String, String>,
        total: Long = -1L,
        onBytes: suspend (Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val existing = 0L
        StoragePlatform.deleteFile(targetPath)
        val response = client.request(url) {
            method = HttpMethod.Get
            headers.forEach { (k, v) -> header(k, v) }
        }
        if (response.headers["Content-Type"].orEmpty().contains("text/html", ignoreCase = true)) {
            error("HTML error page")
        }
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.PartialContent) {
            error("HTTP error ${response.status}")
        }
        val expected = if (total > 0) total else -1L
        val channel = response.bodyAsChannel()
        var written = 0L
        val writer = StoragePlatform.openWriter(targetPath)
        try {
            val buffer = ByteArray(BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                val allow = if (expected < 0) read.toLong() else min(read.toLong(), expected - written)
                if (allow <= 0) break
                writer.write(buffer, 0, allow.toInt())
                written += allow
                onBytes(allow)
                if (expected >= 0 && written >= expected) break
            }
        } finally {
            writer.close()
        }
        if (total > 0 && written < total) return@withContext false
        true
    }

    suspend fun mergeParts(
        parts: List<String>,
        targetPath: String,
        onProgress: ((Long) -> Unit)? = null,
        onPartDigest: ((String, String) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        StoragePlatform.mergeChunkFiles(parts, targetPath, onProgress, onPartDigest)
    }
}
