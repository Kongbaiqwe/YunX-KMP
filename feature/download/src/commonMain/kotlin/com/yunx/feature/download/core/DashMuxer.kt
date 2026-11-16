package com.yunx.feature.download.core

/**
 * 跨平台音视频轨道重封装与合轨门面
 */
expect object DashMuxer {
    fun mux(videoPath: String, audioPath: String, outPath: String, onProgress: (Int) -> Unit = {}): Boolean
    fun remuxToMp4(inputPath: String, outPath: String): Boolean
}
