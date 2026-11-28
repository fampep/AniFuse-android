// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnipmXorUnwrapTest {
    @Test
    fun xorKeyFromSyncCandidates() {
        // First byte 0xB8 → key 0xFF when packet 188 also matches.
        val payload = ByteArray(400) { 0 }
        payload[0] = 0xB8.toByte()
        payload[188] = 0xB8.toByte()
        assertEquals(0xFF, anipmXorKey(payload))

        val clear = anipmXorUnwrap(payload)
        assertEquals(0x47, clear[0].toInt() and 0xFF)
        assertEquals(0x47, clear[188].toInt() and 0xFF)
    }

    @Test
    fun pngTrailerPayloadCutsAfterIend() {
        // Minimal PNG: signature + empty IEND + XOR'd TS sync trailer.
        val png = byteArrayOf(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
            0x0d, 0x0a, 0x1a, 0x0a,
            // IEND chunk: length 0, type IEND, CRC 0
            0, 0, 0, 0,
            'I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte(),
            0, 0, 0, 0,
        )
        val trailer = ByteArray(564) { 0 }
        for (i in 0 until 564 step 188) trailer[i] = 0xB8.toByte()
        val raw = png + trailer
        val cut = stripPngTrailerPayload(raw)
        assertEquals(564, cut?.size)
        assertEquals(0xB8, cut!![0].toInt() and 0xFF)

        val clear = anipmXorUnwrap(raw)
        assertEquals(0x47, clear[0].toInt() and 0xFF)
        assertEquals(0x47, clear[188].toInt() and 0xFF)
        assertEquals(0x47, clear[376].toInt() and 0xFF)
        assertNull(stripPngTrailerPayload(trailer))
    }

    @Test
    fun hinataAndAniPmSourcesEnablePngXor() {
        assertTrue(
            HinataTvServer(cache = null).server.id == "hinatatv",
        )
        // PlayableSource defaults stay off for MegaPlay / plain HLS.
        assertFalse(PlayableSource(url = "https://cdn.example/a.m3u8").pngXor)
    }

    @Test
    fun byterangePastPngUsesStreamXor() {
        assertFalse(anipmXorUsesStreamByterange(0L))
        assertTrue(anipmXorUsesStreamByterange(2_963L))
        // First ranged chunk already past IEND — key from sync candidates.
        val chunk = ByteArray(400) { 0 }
        chunk[0] = 0xB8.toByte()
        chunk[188] = 0xB8.toByte()
        assertEquals(0xFF, anipmXorStreamKey(chunk, 0, chunk.size))
        // Short first read still picks the first-byte candidate.
        assertEquals(0xFF, anipmXorStreamKey(byteArrayOf(0xB8.toByte()), 0, 1))
    }
}
