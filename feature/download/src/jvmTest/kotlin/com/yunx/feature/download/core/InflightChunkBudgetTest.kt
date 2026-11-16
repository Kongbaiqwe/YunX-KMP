package com.yunx.feature.download.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InflightChunkBudgetTest {

    @Test
    fun clampsIntoSafeRange() {
        assertTrue(DownloadManager.inflightChunksFor(16L * MB) >= 8)
        assertTrue(DownloadManager.inflightChunksFor(32L * MB) >= 8)
        assertTrue(DownloadManager.inflightChunksFor(64L * MB) >= 8)
        assertTrue(DownloadManager.inflightChunksFor(256L * MB) <= 512)
        assertTrue(DownloadManager.inflightChunksFor(512L * MB) <= 512)
        assertTrue(DownloadManager.inflightChunksFor(1024L * MB) <= 512)
        assertEquals(512, DownloadManager.inflightChunksFor(256L * MB))
    }

    @Test
    fun totalBufferStaysWithinHeapBudget() {
        for (heapMb in longArrayOf(32, 64, 128, 256, 512)) {
            val heap = heapMb * MB
            val chunks = DownloadManager.inflightChunksFor(heap)
            val buffers = chunks.toLong() * BUFFER_SIZE
            assertTrue(
                "heap=${heapMb}MB chunks=$chunks buffers=$buffers",
                buffers <= heap / 8
            )
        }
    }

    @Test
    fun perStreamBufferIs64Kb() {
        assertEquals(64L * 1024, BUFFER_SIZE.toLong())
    }

    private companion object {
        const val MB = 1024L * 1024
    }
}
