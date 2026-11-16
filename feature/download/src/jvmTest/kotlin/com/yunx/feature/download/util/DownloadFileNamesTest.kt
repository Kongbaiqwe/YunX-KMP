package com.yunx.feature.download.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFileNamesTest {

    @Test
    fun testApkConflictSuffix() {
        assertEquals("x (1).apk", DownloadFileNames.normalize("x.apk (1)"))
        assertEquals("x (2).apk", DownloadFileNames.normalize("x.apk(2)"))
        assertEquals("x (1).apk", DownloadFileNames.normalize("x.apk（1）"))
        assertEquals("报告 (1).apk", DownloadFileNames.normalize("  报告.apk (1)  "))
    }

    @Test
    fun testStripRedundantTxt() {
        assertEquals("README.md", DownloadFileNames.normalize("README.md.txt"))
        assertEquals("x.MD", DownloadFileNames.normalize("x.MD.txt"))
        assertEquals("app.log", DownloadFileNames.normalize("app.log.txt"))
        assertEquals("data.json", DownloadFileNames.normalize("data.json.txt"))
    }

    @Test
    fun testKeepTxtForNonTextExtensions() {
        assertEquals("x.zip.txt", DownloadFileNames.normalize("x.zip.txt"))
        assertEquals("x.exe.txt", DownloadFileNames.normalize("x.exe.txt"))
        assertEquals("x.tar.txt", DownloadFileNames.normalize("x.tar.txt"))
    }

    @Test
    fun testNormalFileNamesUntouched() {
        assertEquals("x.apk", DownloadFileNames.normalize("x.apk"))
        assertEquals("x.tar.gz", DownloadFileNames.normalize("x.tar.gz"))
        assertEquals("没有扩展名", DownloadFileNames.normalize("没有扩展名"))
        assertEquals("photo (3).jpg", DownloadFileNames.normalize("photo (3).jpg"))
        assertEquals("下载 (1)", DownloadFileNames.normalize("下载 (1)"))
        assertEquals("x.txt", DownloadFileNames.normalize("x.txt"))
    }

    @Test
    fun testMalformedFileNamesUntouched() {
        assertEquals("x.apk (1) (2)", DownloadFileNames.normalize("x.apk (1) (2)"))
        assertEquals("会议记录 (2026).md", DownloadFileNames.normalize("会议记录 (2026).md"))
    }

    @Test
    fun testCombinedForms() {
        assertEquals("x (1).md", DownloadFileNames.normalize("x.md (1)"))
        assertEquals("x.md (1).txt", DownloadFileNames.normalize("x.md (1).txt"))
    }
}
