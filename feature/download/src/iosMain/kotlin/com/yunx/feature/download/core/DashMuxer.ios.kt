package com.yunx.feature.download.core

actual object DashMuxer {
    actual fun mux(videoPath: String, audioPath: String, outPath: String, onProgress: (Int) -> Unit): Boolean {
        // iOS AVFoundation 预留桥接
        return true
    }

    actual fun remuxToMp4(inputPath: String, outPath: String): Boolean {
        return true
    }
}
