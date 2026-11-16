package com.yunx.feature.download.core

import java.io.File
import java.io.FileOutputStream

actual object AudioExtractor {
    actual fun extract(inputPath: String, outputPath: String, onProgress: (Int) -> Unit): Boolean {
        val input = File(inputPath)
        val out = File(outputPath)
        if (!input.exists()) return false
        out.parentFile?.mkdirs()

        // JVM 兜底模拟（真实 Android 端保留 MediaExtractor 原声抽取）：直接复制
        input.inputStream().use { fis ->
            FileOutputStream(out).use { fos ->
                fis.copyTo(fos)
            }
        }
        onProgress(100)
        return out.length() > 0
    }
}
