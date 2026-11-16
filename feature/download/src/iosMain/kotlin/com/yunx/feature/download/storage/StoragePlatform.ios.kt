package com.yunx.feature.download.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.stat

@OptIn(ExperimentalForeignApi::class)
actual object StoragePlatform {
    private const val BUFFER_SIZE = 64 * 1024

    actual fun getDefaultDownloadDir(): String {
        return "/tmp/downloads"
    }

    actual fun getTempDownloadDir(): String {
        return "/tmp/yunx_download_temp"
    }

    actual fun fileExists(path: String): Boolean = memScoped {
        val s = alloc<stat>()
        stat(path, s.ptr) == 0
    }

    actual fun fileLength(path: String): Long = memScoped {
        val s = alloc<stat>()
        if (stat(path, s.ptr) == 0) s.st_size else 0L
    }

    actual fun deleteFile(path: String): Boolean {
        return remove(path) == 0
    }

    actual fun mkdirs(dirPath: String): Boolean {
        return mkdir(dirPath, 0x1EDu) == 0 // 0755
    }

    actual fun openWriter(path: String): PlatformFileWriter {
        val fp = fopen(path, "r+b") ?: fopen(path, "w+b")
            ?: error("Cannot open file: $path")
        return object : PlatformFileWriter {
            override fun seek(position: Long) {
                fseek(fp, position, SEEK_SET)
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (length <= 0) return
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(offset), 1u, length.toULong(), fp)
                }
            }

            override fun close() {
                fclose(fp)
            }
        }
    }

    actual fun mergeChunkFiles(
        parts: List<String>,
        targetPath: String,
        onProgress: ((Long) -> Unit)?,
        onPartDigest: ((String, String) -> Unit)?
    ): Long {
        val outFp = fopen(targetPath, "wb") ?: error("Cannot create target: $targetPath")
        var total = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        try {
            for (partPath in parts) {
                val inFp = fopen(partPath, "rb") ?: continue
                try {
                    while (true) {
                        val readBytes = buffer.usePinned { pinned ->
                            fread(pinned.addressOf(0), 1u, BUFFER_SIZE.toULong(), inFp).toLong()
                        }
                        if (readBytes <= 0) break
                        buffer.usePinned { pinned ->
                            fwrite(pinned.addressOf(0), 1u, readBytes.toULong(), outFp)
                        }
                        total += readBytes
                    }
                } finally {
                    fclose(inFp)
                }
                remove(partPath)
                onProgress?.invoke(total)
            }
        } finally {
            fclose(outFp)
        }
        return total
    }
}
