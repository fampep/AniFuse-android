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

    @Test
    fun liveLaneBeatsNextWhenProbing() = kotlinx.coroutines.runBlocking {
        PlayableWarmCache.put(
            11,
            3,
            "sub",
            listOf(PlayableSource(url = "https://cdn.example/next.m3u8")),
            lane = PlayableWarmCache.LANE_NEXT,
            nowMs = 10L,
        )
        PlayableWarmCache.put(
            11,
            3,
            "sub",
            listOf(PlayableSource(url = "https://cdn.example/live.m3u8")),
            lane = PlayableWarmCache.LANE_LIVE,
            nowMs = 20L,
        )
        val hit = takeProbedWarm(
            anilistId = 11,
            episode = 3,
            audio = "sub",
            nowMs = 30L,
            probe = { it },
        )
        assertEquals("https://cdn.example/live.m3u8", hit.single().url)
        assertNull(PlayableWarmCache.peek(11, 3, "sub", PlayableWarmCache.LANE_LIVE, nowMs = 40L))
        assertEquals(1, PlayableWarmCache.peek(11, 3, "sub", PlayableWarmCache.LANE_NEXT, nowMs = 40L)?.size)
    }

    @Test
    fun diskRecallSurvivesEmptyMemory() = kotlinx.coroutines.runBlocking {
        var forgotten = false
        val hit = takeProbedWarm(
            anilistId = 7,
            episode = 2,
            audio = "dub",
            nowMs = 50L,
            probe = { it },
            diskRecall = {
                listOf(PlayableSource(url = "https://cdn.example/disk.m3u8", label = "Disk"))
            },
            onDiskMiss = { forgotten = true },
        )
        assertEquals("https://cdn.example/disk.m3u8", hit.single().url)
        assertEquals(false, forgotten)
        assertEquals(
            1,
            PlayableWarmCache.peek(7, 2, "dub", PlayableWarmCache.LANE_LIVE, nowMs = 60L)?.size,
        )
    }

    @Test
    fun liveSourceKeysNormalizeAudio() {
        assertEquals("9:1:sub", LiveSourceStore.key(9, 1, "SUB"))
        assertEquals("9:1:dub", LiveSourceStore.key(9, 1, " dub "))
    }
}
