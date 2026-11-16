package com.yunx.feature.download.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream

class ChunkDownloaderMergeTest {

    private val downloader = ChunkDownloader()

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "yunx_merge_${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun writesAllBytesAndDeletesEachPart() {
        runBlocking {
            val dir = tempDir()
            try {
                val parts = listOf(
                    File(dir, "part_0").apply { writeBytes(ByteArray(300_000) { 1 }) },
                    File(dir, "part_1").apply { writeBytes(ByteArray(100_000) { 2 }) }
                )
                val out = java.io.ByteArrayOutputStream()
                val written = downloader.mergeChunksToStream(parts, out)
                assertEquals(400_000L, written)
                assertEquals(400_000, out.size())
                assertFalse("写完整片后必须立即删除分片（否则峰值占用翻倍）", parts.any { it.exists() })
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun keepsPartThatFailedToWrite() {
        val dir = tempDir()
        try {
            val first = File(dir, "part_0").apply { writeBytes(ByteArray(10)) }
            val second = File(dir, "part_1").apply { writeBytes(ByteArray(10)) }
            val full = object : OutputStream() {
                private var written = 0
                override fun write(b: Int) {
                    if (written + 1 > 10) throw IOException("write failed: ENOSPC (No space left on device)")
                    written++
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (written + len > 10) throw IOException("write failed: ENOSPC (No space left on device)")
                    written += len
                }
            }
            assertThrows(IOException::class.java) {
                runBlocking { downloader.mergeChunksToStream(listOf(first, second), full) }
            }
            assertFalse("已完整写入目标的分片应已释放", first.exists())
            assertTrue("未写完的分片必须保留，避免失败后凭空少数据", second.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
