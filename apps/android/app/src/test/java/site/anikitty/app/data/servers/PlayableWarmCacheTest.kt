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
    fun dropTitleClearsEveryLaneForThatShow() {
        PlayableWarmCache.put(
            21,
            4,
            "sub",
            listOf(PlayableSource(url = "https://cdn.example/live.m3u8")),
            lane = PlayableWarmCache.LANE_LIVE,
            nowMs = 10L,
        )
        PlayableWarmCache.put(
            21,
            5,
            "sub",
            listOf(PlayableSource(url = "https://cdn.example/next.m3u8")),
            lane = PlayableWarmCache.LANE_NEXT,
            nowMs = 11L,
        )
        PlayableWarmCache.put(
            22,
            1,
            "sub",
            listOf(PlayableSource(url = "https://cdn.example/other.m3u8")),
            nowMs = 12L,
        )
        PlayableWarmCache.dropTitle(21)
        assertNull(PlayableWarmCache.peek(21, 4, "sub", PlayableWarmCache.LANE_LIVE, nowMs = 20L))
        assertNull(PlayableWarmCache.peek(21, 5, "sub", PlayableWarmCache.LANE_NEXT, nowMs = 20L))
        assertEquals(1, PlayableWarmCache.peek(22, 1, "sub", nowMs = 20L)?.size)
    }

    @Test
    fun liveSourceKeysNormalizeAudio() {
        assertEquals("9:1:sub", LiveSourceStore.key(9, 1, "SUB"))
        assertEquals("9:1:dub", LiveSourceStore.key(9, 1, " dub "))
        // Live keeps the legacy 3-part key; shadow/standby append the lane.
        assertEquals("9:1:sub", LiveSourceStore.key(9, 1, "sub", PlayableWarmCache.LANE_LIVE))
        assertEquals(
            "9:1:sub:shadow",
            LiveSourceStore.key(9, 1, "sub", PlayableWarmCache.LANE_SHADOW),
        )
        assertEquals(
            "9:1:dub:standby",
            LiveSourceStore.key(9, 1, "DUB", PlayableWarmCache.LANE_STANDBY),
        )
    }

    @Test
    fun shadowDiskRecallPromotesIntoShadowLane() = kotlinx.coroutines.runBlocking {
        PlayableWarmCache.clear()
        val hit = takeProbedShadow(
            anilistId = 42,
            episode = 7,
            audio = "sub",
            nowMs = 100L,
            probe = { it },
            diskRecall = {
                listOf(PlayableSource(url = "https://cdn.standby/ep.m3u8", label = "Shadow"))
            },
        )
        assertEquals("https://cdn.standby/ep.m3u8", hit.single().url)
        assertEquals(
            1,
            PlayableWarmCache.peek(42, 7, "sub", PlayableWarmCache.LANE_SHADOW, nowMs = 110L)?.size,
        )
        assertNull(PlayableWarmCache.peek(42, 7, "sub", PlayableWarmCache.LANE_LIVE, nowMs = 110L))
    }

    @Test
    fun shadowMemoryBeatsDiskAndConsumesTheLane() = kotlinx.coroutines.runBlocking {
        PlayableWarmCache.put(
            5,
            1,
            "sub",
            listOf(PlayableSource(url = "https://cdn.mem/shadow.m3u8")),
            lane = PlayableWarmCache.LANE_SHADOW,
            nowMs = 10L,
        )
        var diskHit = false
        val hit = takeProbedShadow(
            anilistId = 5,
            episode = 1,
            audio = "sub",
            nowMs = 20L,
            probe = { it },
            diskRecall = {
                diskHit = true
                listOf(PlayableSource(url = "https://cdn.disk/shadow.m3u8"))
            },
        )
        assertEquals("https://cdn.mem/shadow.m3u8", hit.single().url)
        assertEquals(false, diskHit)
        assertNull(PlayableWarmCache.peek(5, 1, "sub", PlayableWarmCache.LANE_SHADOW, nowMs = 30L))
    }

    @Test
    fun liveEncodeKeepsUnwrapAndHlsFields() {
        val source = PlayableSource(
            url = "https://cdn.example/master.txt",
            headers = mapOf("Referer" to "https://gotaku.to/"),
            label = "1080p",
            stripBytes = 4096,
            gotakuObf = GotakuObf("seed", "token", segmentBytes = 4096),
            flix = FlixUnwrap("pk", "mask", englishAudio = true),
            hls = true,
            skip = site.anikitty.app.data.skip.SkipTimes(
                intro = site.anikitty.app.data.skip.SkipInterval(10.0, 90.0),
            ),
            subtitles = listOf(
                SubtitleTrack(url = "https://cdn.example/en.vtt", label = "English", language = "en"),
            ),
            serverName = "gotaku",
        )
        val round = LiveSourceStore.decode(LiveSourceStore.encode(listOf(source))).single()
        assertEquals(source.url, round.url)
        assertEquals(true, round.hls)
        assertEquals(4096, round.stripBytes)
        assertEquals("seed", round.gotakuObf?.keySeed)
        assertEquals("token", round.gotakuObf?.playlistToken)
        assertEquals("pk", round.flix?.playlistKey)
        assertEquals(true, round.flix?.englishAudio)
        assertEquals(10.0, round.skip.intro?.startSec ?: -1.0, 0.01)
        assertEquals("https://cdn.example/en.vtt", round.subtitles.single().url)
        assertEquals("gotaku", round.serverName)
    }
}
