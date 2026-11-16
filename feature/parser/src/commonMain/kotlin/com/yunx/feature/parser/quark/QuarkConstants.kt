package com.yunx.feature.parser.quark

object QuarkConstants {

    /** 夸克 PC 客户端 User-Agent */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Safari/537.36 QuarkPC/6.0.8.649"

    /** WebView 登录页（PC 环境） */
    const val LOGIN_URL = "https://pan.quark.cn/?fr=pc&platform=pc"

    /** 提取 Cookie 的域名 */
    const val COOKIE_DOMAIN = "https://pan.quark.cn"

    /** 验证登录状态的接口 */
    const val ACCOUNT_INFO_URL = "https://pan.quark.cn/account/info"

    /** 解析/下载 API 强制 User-Agent */
    const val API_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.12-a038f7b798 Safari/537.36 Channel/pckk_other_ch"

    /** 业务 API 基础域名 */
    const val API_BASE = "https://drive-pc.quark.cn"

    /** 列表缩略图接口所在域名 */
    const val THUMBNAIL_HOST = "drive-pc.quark.cn"

    /** 获取分享 Token */
    const val SHARE_TOKEN_URL = "$API_BASE/1/clouddrive/share/sharepage/token?pr=ucpro&fr=pc"

    /** 验证分享提取码 */
    const val SHARE_PASSWORD_URL = "$API_BASE/1/clouddrive/share/password?pr=ucpro&fr=pc"

    /** 获取分享文件列表 */
    const val SHARE_DETAIL_URL = "$API_BASE/1/clouddrive/share/sharepage/detail?pr=ucpro&fr=pc"

    /** 获取下载直链 */
    const val DOWNLOAD_URL = "$API_BASE/1/clouddrive/file/download?pr=ucpro&fr=pc&sys=win32&ve=3.23.2"

    /** 根目录 fid */
    const val DEFAULT_PDIR_FID = "0"

    /** 个人网盘文件列表 / 创建目录 */
    const val FILE_URL = "$API_BASE/1/clouddrive/file?pr=ucpro&fr=pc"

    /** 个人网盘文件列表（排序） */
    const val CLOUD_FILE_SORT_URL = "$API_BASE/1/clouddrive/file/sort?pr=ucpro&fr=pc"

    /** 转存分享文件 */
    const val SAVE_URL = "$API_BASE/1/clouddrive/share/sharepage/save?pr=ucpro&fr=pc"

    /** 异步任务查询 */
    const val TASK_URL = "$API_BASE/1/clouddrive/task?pr=ucpro&fr=pc"

    /** 刷新会话 Cookie 配置接口 */
    const val CONFIG_URL = "$API_BASE/1/clouddrive/config?pr=ucpro&fr=pc"

    /** 下载 Referer */
    const val DOWNLOAD_REFERER = "https://pan.quark.cn/"

    /** 临时转存目录名 */
    const val TEMP_DIR_NAME = "YunX临时转存"
}
