package com.yunx.feature.download.storage

/**
 * 跨平台随机写文件句柄
 */
interface PlatformFileWriter {
    fun seek(position: Long)
    fun write(bytes: ByteArray, offset: Int, length: Int)
    fun close()
}

/**
 * 跨平台存储与沙盒路径门面
 */
expect object StoragePlatform {
    fun getDefaultDownloadDir(): String
    fun getTempDownloadDir(): String
    fun fileExists(path: String): Boolean
    fun fileLength(path: String): Long
    fun deleteFile(path: String): Boolean
    fun mkdirs(dirPath: String): Boolean
    fun openWriter(path: String): PlatformFileWriter
    
    /**
     * 流式边写边删合并分片文件
     */
    fun mergeChunkFiles(
        parts: List<String>,
        targetPath: String,
        onProgress: ((Long) -> Unit)? = null,
        onPartDigest: ((String, String) -> Unit)? = null
    ): Long
}
