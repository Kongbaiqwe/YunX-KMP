package com.yunx.feature.download.core

/**
 * 跨平台音轨抽取门面
 */
expect object AudioExtractor {
    fun extract(inputPath: String, outputPath: String, onProgress: (Int) -> Unit = {}): Boolean
}
