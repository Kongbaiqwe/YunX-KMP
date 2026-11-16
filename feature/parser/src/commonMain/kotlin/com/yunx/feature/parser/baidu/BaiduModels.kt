package com.yunx.feature.parser.baidu

import com.yunx.core.model.ShareFile
import kotlinx.serialization.Serializable

/**
 * 百度网盘分享解析数据。
 */
@Serializable
data class BaiduShareList(
    val title: String,
    val shareId: String,
    val uk: String,
    val files: List<ShareFile>
)

/**
 * 百度转存结果。
 */
@Serializable
data class BaiduTransferResult(
    val fsId: String,
    val path: String
)
