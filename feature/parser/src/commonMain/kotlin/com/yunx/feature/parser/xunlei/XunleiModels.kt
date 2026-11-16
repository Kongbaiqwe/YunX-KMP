package com.yunx.feature.parser.xunlei

import com.yunx.core.model.ShareFile
import kotlinx.serialization.Serializable

@Serializable
data class XunleiShareResult(
    val title: String,
    val files: List<ShareFile>,
    val passCodeToken: String,
    val shareId: String,
    val nextPageToken: String = ""
)

@Serializable
data class XunleiLoginStep(
    val needSms: Boolean = false,
    val smsCreditKey: String = "",
    val smsToken: String = "",
    val message: String = ""
)
