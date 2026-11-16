package com.yunx.feature.download.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetLinkTest {

    private val hash = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun recognisesMagnetScheme() {
        assertTrue(MagnetLink.isMagnet("magnet:?xt=urn:btih:$hash"))
        assertTrue(MagnetLink.isMagnet("  MAGNET:?xt=urn:btih:$hash  "))
        assertFalse(MagnetLink.isMagnet("https://example.com/a.bin"))
        assertFalse(MagnetLink.isMagnet("magnet:"))
        assertFalse(MagnetLink.isMagnet(""))
    }

    @Test
    fun extractsDisplayNameFromDn() {
        val url = "magnet:?xt=urn:btih:$hash&dn=Ubuntu%2022.04%20Live.iso&tr=udp%3A%2F%2Ftracker"
        assertEquals("Ubuntu 22.04 Live.iso", MagnetLink.displayName(url))
    }

    @Test
    fun keepsLiteralPlusInDisplayName() {
        val url = "magnet:?xt=urn:btih:$hash&dn=C%2B%2B%20Primer.pdf"
        assertEquals("C++ Primer.pdf", MagnetLink.displayName(url))
    }

    @Test
    fun fallsBackToInfoHashWhenNoDisplayName() {
        assertEquals("01234567", MagnetLink.placeholderHash("magnet:?xt=urn:btih:$hash"))
        assertEquals("", MagnetLink.placeholderHash("magnet:?dn="))
    }

    @Test
    fun truncatesOverlongDisplayName() {
        val long = "A".repeat(200)
        assertEquals(80, MagnetLink.displayName("magnet:?xt=urn:btih:$hash&dn=$long").length)
    }

    @Test
    fun extractsInfoHash() {
        assertEquals(hash.uppercase(), MagnetLink.infoHash("magnet:?xt=urn:btih:$hash"))
        assertEquals("MFRGGZDFMZTWQ2LK", MagnetLink.infoHash("magnet:?xt=urn:btih:mfrggzdfmztwq2lk"))
        assertEquals("", MagnetLink.infoHash("magnet:?dn=no-hash-here"))
    }

    @Test
    fun displayNameTakesFirstOccurrenceOnly() {
        val url = "magnet:?dn=First.iso&dn=Second.iso&xt=urn:btih:$hash"
        assertEquals("First.iso", MagnetLink.displayName(url))
    }
}
