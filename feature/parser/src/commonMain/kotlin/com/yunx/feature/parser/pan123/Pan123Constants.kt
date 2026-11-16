package com.yunx.feature.parser.pan123

object Pan123Constants {
    const val API_BASE = "https://www.123pan.com"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val SHARE_INFO_URL = "$API_BASE/b/api/share/get"
    const val SHARE_DOWNLOAD_URL = "$API_BASE/b/api/share/download/info"
    const val USER_INFO_URL = "$API_BASE/b/api/user/info"

    const val SIGN_OFFSET_SECONDS = 57600L // +16h
    val SIGN_TABLE = charArrayOf('a', 'd', 'e', 'f', 'g', 'h', 'l', 'm', 'y', 'i')
}
