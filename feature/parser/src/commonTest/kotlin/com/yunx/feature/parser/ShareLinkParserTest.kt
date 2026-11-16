package com.yunx.feature.parser

import com.yunx.core.model.SharePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShareLinkParserTest {

    @Test
    fun testParsesAllSupportedPlatforms() {
        val cases = listOf(
            "https://pan.quark.cn/s/Abc123?pwd=a1B2" to (SharePlatform.QUARK to "Abc123"),
            "https://drive.uc.cn/s/Abc123" to (SharePlatform.UC to "Abc123"),
            "https://pan.xunlei.com/s/Abc_123-xy" to (SharePlatform.XUNLEI to "Abc_123-xy"),
            "https://pan.baidu.com/s/1Abc_123-xy?pwd=9xYz" to (SharePlatform.BAIDU to "Abc_123-xy"),
            "https://yun.139.com/shareweb/#/w/i/Abc_123" to (SharePlatform.C139 to "Abc_123"),
            "https://www.123pan.com/s/2785Vv-T4Ded" to (SharePlatform.PAN123 to "2785Vv-T4Ded"),
            "https://115.com/s/swz54f736y2?password=y118" to (SharePlatform.PAN115 to "swz54f736y2")
        )

        cases.forEach { (text, expected) ->
            val parsed = ShareLinkParser.parse(text)
            assertEquals(expected.first, parsed?.platform)
            assertEquals(expected.second, parsed?.shareId)
        }
    }

    @Test
    fun testExplicitTextPasswordIsExtracted() {
        val parsed = ShareLinkParser.parse("链接 https://drive.uc.cn/s/Abc123 提取码：a1B2")
        assertEquals("a1B2", parsed?.pwd)
    }

    @Test
    fun testParsesPan115Forms() {
        val withQuery = ShareLinkParser.parse("https://115cdn.com/s/swz54f736y2?password=y118")
        assertEquals(SharePlatform.PAN115, withQuery?.platform)
        assertEquals("swz54f736y2", withQuery?.shareId)
        assertEquals("y118", withQuery?.pwd)

        val command = ShareLinkParser.parse("https://115.com/swz54f736y2-y118/")
        assertEquals(SharePlatform.PAN115, command?.platform)
        assertEquals("swz54f736y2", command?.shareId)
        assertEquals("y118", command?.pwd)
    }

    @Test
    fun testRejectsUnrelatedUrl() {
        assertNull(ShareLinkParser.parse("https://example.com/s/Abc123"))
    }

    @Test
    fun testParsesGuangyaAndLanzouWithoutMisjudgingILanzou() {
        val guangya = ShareLinkParser.parse("https://www.guangyapan.com/s/Share123")
        assertEquals(SharePlatform.GUANGYA, guangya?.platform)
        assertEquals("Share123", guangya?.shareId)

        val lanzouSub = ShareLinkParser.parse("https://wwx.lanzoui.com/iAbc123")
        assertEquals(SharePlatform.LANZOU, lanzouSub?.platform)
        assertEquals("iAbc123", lanzouSub?.shareId)

        val lanzouFolder = ShareLinkParser.parse("https://pan.lanzou.com/bFolder1?pwd=a1B2")
        assertEquals(SharePlatform.LANZOU, lanzouFolder?.platform)
        assertEquals("bFolder1", lanzouFolder?.shareId)
        assertEquals("a1B2", lanzouFolder?.pwd)

        val ilanzou = ShareLinkParser.parse("https://www.ilanzou.com/s/Abc123")
        assertEquals(SharePlatform.ILANZOU, ilanzou?.platform)
        assertEquals("Abc123", ilanzou?.shareId)
    }
}
