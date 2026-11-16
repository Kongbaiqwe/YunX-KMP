package com.yunx.feature.parser.guangya

import kotlin.random.Random

object GuangYaConstants {
    const val CLIENT_ID = "aMe-8VSlkrbQXpUR"
    const val API_BASE = "https://api.guangyapan.com"
    const val WEB_SITE = "https://www.guangyapan.com"
    const val DOWNLOAD_REFERER = "$WEB_SITE/"

    const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val SHARE_SUMMARY = "$API_BASE/userres/v1/get_share_summary"
    const val SHARE_ACCESS_TOKEN = "$API_BASE/userres/v1/get_share_access_token"
    const val SHARE_FILES = "$API_BASE/userres/v1/get_share_page_files_list"
    const val SHARE_DOWNLOAD = "$API_BASE/userres/v1/get_share_download_url"

    const val PAGE_SIZE = 100

    fun newDeviceId(): String {
        val chars = "0123456789abcdef"
        return buildString(32) {
            repeat(32) { append(chars[Random.nextInt(16)]) }
        }
    }
}
