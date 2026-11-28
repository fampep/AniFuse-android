// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SubtitleLibraryCacheTest {
    @Test
    fun memoryCacheKeepsAFreshHitAndExpiresAStaleOne() {
        var now = 1_000L
        val cache = CaptionMemoryCache(ttlMs = 100L, missTtlMs = 50L, clock = { now })
        val tracks = listOf(
            SubtitleTrack(
                url = "http://143.246.203.212/api/file?id=${"a".repeat(64)}",
                label = "English",
            ),
        )
        cache.put("1/1", tracks)
        assertEquals(tracks, cache.get("1/1"))
        now += 80L
        assertEquals(tracks, cache.get("1/1"))
        now += 30L // age 110 > ttl 100
        assertNull(cache.get("1/1"))
    }

    @Test
    fun anEmptyAnswerIsRememberedOnlyBriefly() {
        var now = 1_000L
        val cache = CaptionMemoryCache(ttlMs = 100L, missTtlMs = 50L, clock = { now })
        cache.put("2/3", emptyList())
        assertTrue(cache.get("2/3")!!.isEmpty())
        now += 60L // age 60 > miss ttl 50
        assertNull(cache.get("2/3"))
    }

    @Test
    fun fileStoreRoundTripsAndReportsMissing() {
        val dir = Files.createTempDirectory("captions").toFile()
        try {
            val store = CaptionFileStore(dir)
            val body = """{"tracks":[]}"""
            store.write("12/34", body)
            val stored = store.read("12/34")
            assertNotNull(stored)
            assertEquals(body, stored!!.body)
            assertTrue(stored.savedAtMs > 0L)
            assertNull(store.read("9/9"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun peekPromotesAFreshDiskListingIntoMemory() {
        val dir = Files.createTempDirectory("captions-peek").toFile()
        try {
            val store = CaptionFileStore(dir)
            val key = "a".repeat(64)
            val body = """{"tracks":[{"language":"English","flag":"🇬🇧","site":"AniGato","download":"/api/file?id=$key"}]}"""
            store.write("21/7", body)
            val memory = CaptionMemoryCache(ttlMs = CAPTION_TTL_MS, missTtlMs = CAPTION_MISS_TTL_MS)
            val promoted = promoteCaptionPeek(
                key = "21/7",
                memory = memory,
                store = store,
                nowMs = System.currentTimeMillis(),
                ttlMs = CAPTION_TTL_MS,
            )
            assertNotNull(promoted)
            assertEquals(1, promoted!!.size)
            assertEquals(promoted, memory.get("21/7"))
            // Empty disk listings must not pretend captions are gone.
            store.write("21/8", """{"tracks":[]}""")
            assertNull(
                promoteCaptionPeek(
                    key = "21/8",
                    memory = CaptionMemoryCache(),
                    store = store,
                ),
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
