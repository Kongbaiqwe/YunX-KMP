package com.yunx.feature.download.storage

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

actual object StoragePlatform {
    private const val BUFFER_SIZE = 64 * 1024

    actual fun getDefaultDownloadDir(): String {
        val userHome = System.getProperty("user.home") ?: "."
        val downloads = File(userHome, "Downloads")
        if (!downloads.exists()) downloads.mkdirs()
        return downloads.absolutePath
    }

    actual fun getTempDownloadDir(): String {
        val tmp = System.getProperty("java.io.tmpdir") ?: "."
        val dir = File(tmp, "yunx_download_temp")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    actual fun fileExists(path: String): Boolean = File(path).exists()

    actual fun fileLength(path: String): Long = File(path).length()

    actual fun deleteFile(path: String): Boolean = File(path).delete()

    actual fun mkdirs(dirPath: String): Boolean = File(dirPath).mkdirs()

    actual fun openWriter(path: String): PlatformFileWriter {
        val file = File(path)
        file.parentFile?.mkdirs()
        val raf = RandomAccessFile(file, "rw")
        return object : PlatformFileWriter {
            override fun seek(position: Long) = raf.seek(position)
            override fun write(bytes: ByteArray, offset: Int, length: Int) = raf.write(bytes, offset, length)
            override fun close() = raf.close()
        }
    }

    actual fun mergeChunkFiles(
        parts: List<String>,
        targetPath: String,
        onProgress: ((Long) -> Unit)?,
        onPartDigest: ((String, String) -> Unit)?
    ): Long {
        val target = File(targetPath)
        target.parentFile?.mkdirs()
        var total = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        FileOutputStream(target).use { out ->
            for (partPath in parts) {
                val partFile = File(partPath)
                val digest = onPartDigest?.let { MessageDigest.getInstance("SHA-256") }
                FileInputStream(partFile).use { fis ->
                    while (true) {
                        val read = fis.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)
                        total += read
                    }
                }
                if (digest != null) {
                    val hex = digest.digest().joinToString("") { "%02x".format(it) }
                    onPartDigest.invoke(partPath, hex)
                }
                // 边写边删：写完立即删除分片释放磁盘空间
                partFile.delete()
                onProgress?.invoke(total)
            }
            out.flush()
        }
        return total
    }
}
