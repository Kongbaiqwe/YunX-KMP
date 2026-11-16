package com.yunx.shared

import com.yunx.core.model.ParsedShare
import com.yunx.core.model.SharePlatform
import com.yunx.feature.parser.ShareLinkParser

/**
 * SharedKit 的总入口对象，供 iOS Swift 端调用。
 * SKIE 会自动将其转换成符合 Swift 惯用语法的接口。
 */
object YunXShared {

    const val VERSION = "1.0.0-kmp"

    fun getVersion(): String = VERSION

    fun parseShareLink(input: String): ParsedShare? {
        return ShareLinkParser.parse(input)
    }

    fun getSupportedPlatforms(): List<SharePlatform> {
        return SharePlatform.entries
    }

    fun normalizeFileName(raw: String): String {
        return com.yunx.feature.download.util.DownloadFileNames.normalize(raw)
    }

    fun isMagnetLink(url: String): Boolean {
        return com.yunx.feature.download.util.MagnetLink.isMagnet(url)
    }

    fun coordinator(): YunXDownloadCoordinator {
        return YunXDownloadCoordinator.shared()
    }
}
