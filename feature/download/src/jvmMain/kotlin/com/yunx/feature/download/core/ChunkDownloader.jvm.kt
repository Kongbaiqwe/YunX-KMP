package com.yunx.feature.download.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.security.MessageDigest

suspend fun ChunkDownloader.mergeChunksToStream(
    chunkFiles: List<File>,
    out: OutputStream,
    onProgress: ((Long) -> Unit)? = null,
    onPartDigest: ((File, String) -> Unit)? = null
): Long = withContext(Dispatchers.IO) {
    var total = 0L
    val buffer = ByteArray(BUFFER_SIZE)
    chunkFiles.forEach { part ->
        val digest = onPartDigest?.let { MessageDigest.getInstance("SHA-256") }
        FileInputStream(part).use { fis ->
            while (true) {
                if (!isActive) throw CancellationException("下载被取消")
                val read = fis.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
                digest?.update(buffer, 0, read)
                total += read
            }
        }
        if (digest != null) {
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            onPartDigest?.invoke(part, hex)
        }
        part.delete()
        onProgress?.invoke(total)
    }
    out.flush()
    total
}
