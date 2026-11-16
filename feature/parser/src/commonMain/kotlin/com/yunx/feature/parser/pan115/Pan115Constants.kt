package com.yunx.feature.parser.pan115

object Pan115Constants {
    const val API_BASE = "https://webapi.115.com"
    const val PRO_API_BASE = "https://proapi.115.com"
    const val REFERER = "https://115.com/"
    const val DOWNLOAD_REFERER = "https://proapi.115.com/"

    var CLIENT_UA = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.122 Safari/537.36 115desktop/27.0.5.7"
    const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    const val USER_INFO_URL = "$API_BASE/user/info?ct=json"
    const val INDEX_INFO_URL = "$API_BASE/files/index_info?count_space_nums=1"
    const val FILES_URL = "$API_BASE/files"
    const val FILE_DOWNLOAD_URL = "$API_BASE/files/download"
    const val APP_DOWNLOAD_URL = "$PRO_API_BASE/app/chrome/downurl"
    const val FILE_ADD_URL = "$API_BASE/files/add"
    const val FILE_RENAME_URL = "$API_BASE/files/batch_rename"
    const val FILE_MOVE_URL = "$API_BASE/files/move"
    const val MOVE_PROGRESS_URL = "$API_BASE/files/move_progress"
    const val FILE_DELETE_URL = "$API_BASE/rb/delete"

    const val SHARE_SNAP_URL = "$PRO_API_BASE/app/share/snap"
    const val SHARE_DOWN_URL = "$PRO_API_BASE/app/share/downurl"
    const val APP_VERSION_URL = "https://appversion.115.com/1/web/1.0/api/chrome"

    const val ROOT_CID = "0"
    const val PAGE_LIMIT = 50
    const val SHARE_PAGE_LIMIT = 40
    const val AID_PERSONAL = "1"

    fun shareReferer(shareCode: String, receiveCode: String = ""): String =
        "https://115.com/s/$shareCode" + if (receiveCode.isNotBlank()) "?password=$receiveCode" else ""
}
