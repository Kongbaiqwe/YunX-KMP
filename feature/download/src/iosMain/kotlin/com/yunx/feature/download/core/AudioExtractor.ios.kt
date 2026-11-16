package com.yunx.feature.download.core

actual object AudioExtractor {
    actual fun extract(inputPath: String, outputPath: String, onProgress: (Int) -> Unit): Boolean {
        // iOS AVFoundation 预留桥接
        return true
    }
}
