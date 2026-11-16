package com.yunx.feature.download.core

import com.yunx.core.network.YunXHttpClientFactory
import com.yunx.feature.download.policy.HlsRequestPolicy
import com.yunx.feature.download.storage.StoragePlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * 跨平台 HLS 媒体流解析与分片拼接下载器
 */
class HlsDownloader(
    private val clientProvider: () -> HttpClient = { YunXHttpClientFactory.createHttpClient() }
) {
    val client: HttpClient get() = clientProvider()

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val MAX_PLAYLIST_BYTES = 1024 * 1024L
        private const val MAX_SEGMENTS = 20_000
        private const val MAX_SEGMENT_BYTES = 512L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 100L * 1024 * 1024 * 1024
        private const val COPY_BUFFER_SIZE = 64 * 1024
    }

    suspend fun download(
        url: String,
        headers: Map<String, String>,
        destPath: String,
        onBytes: suspend (Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val credentialOrigin = HlsRequestPolicy.initialUrl(url) ?: return@withContext false

        runCatching {
            val master = fetchText(credentialOrigin, credentialOrigin, headers) ?: return@runCatching false
            val mediaUrl = resolveMediaPlaylist(master.finalUrl, master.text) ?: return@runCatching false
            val media = if (mediaUrl == master.finalUrl) master
            else fetchText(mediaUrl, credentialOrigin, headers) ?: return@runCatching false

            if (media.text.contains("#EXT-X-KEY") || media.text.contains("#EXT-X-BYTERANGE")) {
                return@runCatching false
            }

            val initUri = parseMapUri(media.text)?.let { HlsRequestPolicy.resolve(media.finalUrl, it) }
            val rawSegments = parseSegments(media.text)
            if (rawSegments.isEmpty()) return@runCatching false
            val segments = rawSegments.map { raw ->
                HlsRequestPolicy.resolve(media.finalUrl, raw) ?: error("Invalid segment url")
            }

            StoragePlatform.deleteFile(destPath)
            val writer = StoragePlatform.openWriter(destPath)
            var total = 0L

            try {
                if (initUri != null) {
                    val wrote = fetchTo(initUri, credentialOrigin, headers, writer, MAX_SEGMENT_BYTES) { bytes ->
                        total = checkedTotal(total, bytes)
                        onBytes(bytes)
                    } ?: return@runCatching false
                    if (wrote <= 0) return@runCatching false
                }

                for (segment in segments) {
                    val wrote = fetchTo(segment, credentialOrigin, headers, writer, MAX_SEGMENT_BYTES) { bytes ->
                        total = checkedTotal(total, bytes)
                        onBytes(bytes)
                    } ?: return@runCatching false
                    if (wrote <= 0) return@runCatching false
                }
                true
            } finally {
                writer.close()
            }
        }.onFailure {
            StoragePlatform.deleteFile(destPath)
        }.getOrDefault(false)
    }

    private data class FetchedText(val finalUrl: Url, val text: String)

    private suspend fun fetchText(
        startUrl: Url,
        credentialOrigin: Url,
        headers: Map<String, String>
    ): FetchedText? {
        var current = startUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val reqHeaders = HlsRequestPolicy.headersFor(current, credentialOrigin, headers)
            val response = client.request(current.toString()) {
                method = HttpMethod.Get
                reqHeaders.forEach { (k, v) -> header(k, v) }
            }
            if (response.status.value in 300..399) {
                val loc = response.headers["Location"] ?: return null
                val next = HlsRequestPolicy.resolve(current, loc) ?: return null
                if (redirectCount >= MAX_REDIRECTS) return null
                current = next
                return@repeat
            }
            if (response.status != HttpStatusCode.OK) return null
            val text = response.bodyAsText()
            if (text.length > MAX_PLAYLIST_BYTES) error("Playlist too large")
            return FetchedText(current, text)
        }
        return null
    }

    private suspend fun fetchTo(
        startUrl: Url,
        credentialOrigin: Url,
        headers: Map<String, String>,
        writer: com.yunx.feature.download.storage.PlatformFileWriter,
        maxBytes: Long,
        onBytes: suspend (Long) -> Unit
    ): Long? {
        var current = startUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val reqHeaders = HlsRequestPolicy.headersFor(current, credentialOrigin, headers)
            val response = client.request(current.toString()) {
                method = HttpMethod.Get
                reqHeaders.forEach { (k, v) -> header(k, v) }
            }
            if (response.status.value in 300..399) {
                val loc = response.headers["Location"] ?: return null
                val next = HlsRequestPolicy.resolve(current, loc) ?: return null
                if (redirectCount >= MAX_REDIRECTS) return null
                current = next
                return@repeat
            }
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.PartialContent) return null
            val channel: ByteReadChannel = response.bodyAsChannel()
            var written = 0L
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                written += read
                if (written > maxBytes) error("Segment too large")
                writer.write(buffer, 0, read)
                onBytes(read.toLong())
            }
            return written
        }
        return null
    }

    private fun checkedTotal(current: Long, added: Long): Long {
        val total = current + added
        if (total > MAX_TOTAL_BYTES) error("Total HLS size too large")
        return total
    }

    private fun resolveMediaPlaylist(playlistUrl: Url, text: String): Url? {
        val lines = text.lineSequence().toList()
        for (index in lines.indices) {
            if (lines[index].startsWith("#EXT-X-STREAM-INF")) {
                val next = lines.getOrNull(index + 1)?.trim() ?: continue
                if (next.isNotBlank() && !next.startsWith("#")) {
                    return HlsRequestPolicy.resolve(playlistUrl, next)
                }
            }
        }
        return playlistUrl
    }

    private fun parseSegments(text: String): List<String> = buildList {
        for (line in text.lineSequence()) {
            val value = line.trim()
            if (value.isNotBlank() && !value.startsWith("#")) {
                if (size >= MAX_SEGMENTS) error("Too many HLS segments")
                add(value)
            }
        }
    }

    private fun parseMapUri(text: String): String? {
        val line = text.lineSequence().firstOrNull { it.startsWith("#EXT-X-MAP") } ?: return null
        return Regex("""URI="([^"]+)"""").find(line)?.groupValues?.get(1)
    }
}
