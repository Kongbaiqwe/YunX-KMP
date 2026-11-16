package com.yunx.shared

import com.yunx.core.model.SharePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class YunXSharedTest {

    @Test
    fun testVersion() {
        assertEquals("1.0.0-kmp", YunXShared.getVersion())
    }

    @Test
    fun testParseQuark() {
        val link = "夸克分享：https://pan.quark.cn/s/abcdef123456 提取码: 8888"
        val res = YunXShared.parseShareLink(link)
        assertNotNull(res)
        assertEquals("abcdef123456", res.shareId)
        assertEquals("8888", res.pwd)
        assertEquals(SharePlatform.QUARK, res.platform)
    }

    @Test
    fun testParseBaidu() {
        val link = "链接: https://pan.baidu.com/s/1a2b3c4d_xyz 密码: 1234"
        val res = YunXShared.parseShareLink(link)
        assertNotNull(res)
        assertEquals("a2b3c4d_xyz", res.shareId)
        assertEquals("1234", res.pwd)
        assertEquals(SharePlatform.BAIDU, res.platform)
    }

    @Test
    fun testSupportedPlatforms() {
        val platforms = YunXShared.getSupportedPlatforms()
        assertTrue(platforms.size >= 10)
    }

    @Test
    fun testDownloadUtilities() {
        assertEquals("x (1).apk", YunXShared.normalizeFileName("x.apk (1)"))
        assertTrue(YunXShared.isMagnetLink("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
    }
}
