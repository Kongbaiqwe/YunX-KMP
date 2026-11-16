package com.yunx.feature.parser

import com.yunx.core.model.ParsedShare
import com.yunx.core.model.SharePlatform

/**
 * 从分享链接或整段分享文案中提取 share_id 与提取码。
 * 支持：夸克、UC、迅雷、百度、移动139、123、115、光涯、i蓝奏、蓝奏等。
 */
object ShareLinkParser {

    private val urlRegex = Regex("""https?://[^\s]+""")
    private val quarkShareIdRegex = Regex("""pan\.quark\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val ucShareIdRegex = Regex("""drive\.uc\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val xunleiShareIdRegex = Regex("""pan\.xunlei\.com/s/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val baiduShareIdRegex = Regex("""pan\.baidu\.com/s/(1[A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val c139ShareIdRegex = Regex("""yun\.139\.com/shareweb/.*?/w/i/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val pan123ShareIdRegex = Regex("""123(?:865|pan)\.(?:com|cn)/s/([A-Za-z0-9]+-[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val pan123ShareSubRegex = Regex("""share\.123pan\.cn/123pan/([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val pan123SrrRegex = Regex("""api/srr\?sk=([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val pan115ShareIdRegex = Regex("""115(?:cdn|rc)?\.com/s/(sw[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val pan115CommandRegex = Regex("""115(?:cdn|rc)?\.com/(sw[A-Za-z0-9]{8,})-([A-Za-z0-9]{4,8})""", RegexOption.IGNORE_CASE)
    private val pwdIn115UrlRegex = Regex("""[?&]password=([A-Za-z0-9]+)""")
    private val guangyaShareIdRegex = Regex("""guangyapan\.com/s/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val ilanzouShareIdRegex =
        Regex("""(?:^|[/.])ilanzou\.(?:com|net|org|cn)/s/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val lanzouShareIdRegex =
        Regex(
            """(?:^|[/.])(?:lanzou[a-z0-9-]*|lan[zs]o[ux]|lanzn)\.(?:com|net|org|cn)/([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE
        )
    private val pwdInLanzouUrlRegex = Regex("""[?&](?:pwd|pass|passcode|password)=([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val pwdInUrlRegex = Regex("""[?&]pwd=([A-Za-z0-9]+)""")
    private val pwdInTextRegex = Regex("""(?:提取码|访问码|密码)[：:]\s*([A-Za-z0-9]{4,8})""")

    fun parse(text: String): ParsedShare? {
        val url = urlRegex.find(text.trim())?.value
            ?.trimEnd('。', '，', ',', '；', ';', ')', ']', '}', '"', '\'')
            ?: return null

        // 夸克链接
        quarkShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.QUARK, originalUrl = url)
        }
        // UC 链接
        ucShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.UC, originalUrl = url)
        }
        // 迅雷链接
        xunleiShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.XUNLEI, originalUrl = url)
        }
        // 百度链接：https://pan.baidu.com/s/1xxxxx?pwd=xxxx
        baiduShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val surl = sid.removePrefix("1")
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = surl, pwd = pwd, platform = SharePlatform.BAIDU, originalUrl = url)
        }
        // 139（和彩云）链接
        c139ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.C139, originalUrl = url)
        }
        // 123 云盘链接
        pan123ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123, originalUrl = url)
        }
        pan123ShareSubRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123, originalUrl = url)
        }
        pan123SrrRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123, originalUrl = url)
        }
        // 115 网盘链接
        pan115ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdIn115UrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN115, originalUrl = url)
        }
        // 115 口令形式
        pan115CommandRegex.find(url)?.let { match ->
            return ParsedShare(
                shareId = match.groupValues[1],
                pwd = match.groupValues[2],
                platform = SharePlatform.PAN115,
                originalUrl = url
            )
        }
        // 光鸭云盘链接
        guangyaShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.GUANGYA, originalUrl = url)
        }
        // 蓝奏云优享版链接
        ilanzouShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInLanzouUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.ILANZOU, originalUrl = url)
        }
        // 蓝奏云链接
        lanzouShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInLanzouUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.LANZOU, originalUrl = url)
        }
        return null
    }
}
