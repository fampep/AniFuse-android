// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlayableWarmCacheTest {
    @Before
    fun wipe() {
        PlayableWarmCache.clear()
    }

    @Test
    fun takeConsumesAFreshHit() {
        val source = PlayableSource(url = "https://cdn.example/ep2.m3u8", label = "Warm")
        PlayableWarmCache.put(21, 2, "sub", listOf(source), nowMs = 1_000L)
        val hit = PlayableWarmCache.take(21, 2, "sub", nowMs = 2_000L)
        assertEquals(1, hit?.size)
        assertEquals("https://cdn.example/ep2.m3u8", hit?.first()?.url)
        assertNull(PlayableWarmCache.take(21, 2, "sub", nowMs = 3_000L))
    }

    @Test
    fun expiredEntriesAreDropped() {
        val source = PlayableSource(url = "https://cdn.example/stale.m3u8")
        PlayableWarmCache.put(9, 1, "dub", listOf(source), nowMs = 0L)
        assertNull(
            PlayableWarmCache.peek(9, 1, "dub", nowMs = PlayableWarmCache.TTL_MS + 1L),
        )
    }

    @Test
    fun audioKeysAreIsolated() {
        PlayableWarmCache.put(
            3,
            4,
            "sub",
            listOf(PlayableSource(url = "https://cdn.example/sub.m3u8")),
            nowMs = 10L,
        )
        assertNull(PlayableWarmCache.peek(3, 4, "dub", nowMs = 20L))
        assertEquals(1, PlayableWarmCache.peek(3, 4, "SUB", nowMs = 20L)?.size)
    }
}
