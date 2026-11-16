package com.yunx.feature.parser.xunlei

object XunleiConstants {
    const val AUTH_BASE = "https://xluser-ssl.xunlei.com"
    const val PAN_BASE = "https://api-pan.xunlei.com"
    const val CLIENT_ID = "Xp6pAdwyJv9sQuoN"
    const val CLIENT_SECRET = "standard_a@api#"
    const val APP_CLIENT_ID = "Xp6vsxz_7IYVw2BB"
    const val APP_CLIENT_SECRET = "Xp6vsy4tN9toTVdMSpomVdXpRmES"
    const val APP_CLIENT_VERSION = "8.31.0.9726"
    const val APP_PACKAGE_NAME = "com.xunlei.downloadprovider"
    const val APP_UA = "ANDROID-com.xunlei.downloadprovider/8.31.0.9726 netWorkType/5G appid/40"

    const val LOGIN_URL = "$AUTH_BASE/v3/login"
    const val SEND_SMS_URL = "$AUTH_BASE/v3/sendSms"
    const val SMS_LOGIN_URL = "$AUTH_BASE/v3/smslogin"
    const val CAPTCHA_INIT_URL = "$AUTH_BASE/v1/shield/captcha/init"
    const val TOKEN_URL = "$AUTH_BASE/v1/auth/token"

    const val FILES_URL = "$PAN_BASE/drive/v1/files"
    const val SHARE_URL = "$PAN_BASE/drive/v1/share"
    const val SHARE_SAVE_URL = "$PAN_BASE/drive/v1/share/save"
    const val ABOUT_URL = "$PAN_BASE/drive/v1/about"
}
