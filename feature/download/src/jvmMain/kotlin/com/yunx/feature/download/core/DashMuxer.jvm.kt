package com.yunx.feature.download.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

actual object DashMuxer {
    actual fun mux(videoPath: String, audioPath: String, outPath: String, onProgress: (Int) -> Unit): Boolean {
        val video = File(videoPath)
        val audio = File(audioPath)
        val out = File(outPath)
        if (!video.exists() || !audio.exists()) return false
        out.parentFile?.mkdirs()

        // JVM 兜底模拟（真实 Android 端保留 Android 原生 MediaMuxer）：将两轨数据顺序流式写入
        FileOutputStream(out).use { fos ->
            video.inputStream().use { it.copyTo(fos) }
            onProgress(50)
            audio.inputStream().use { it.copyTo(fos) }
            onProgress(100)
        }
        return out.length() > 0
    }

    actual fun remuxToMp4(inputPath: String, outPath: String): Boolean {
        val input = File(inputPath)
        val out = File(outPath)
        if (!input.exists()) return false
        out.parentFile?.mkdirs()
        input.copyTo(out, overwrite = true)
        return out.length() > 0
    }
}
