// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShadowResolveTest {
    @Before
    fun resetHealth() {
        PlaybackHostHealth.detach()
    }

    @After
    fun clearHealth() {
        PlaybackHostHealth.detach()
    }

    private fun source(url: String) = PlayableSource(url = url, label = url)

    @Test
    fun theRunnerUpIsADifferentHostAndARecentSuccessWins() {
        PlaybackHostHealth.note("cdn-b.example", ok = true, latencyMs = 800L, nowMs = 1_000L)
        PlaybackHostHealth.note("cdn-c.example", ok = true, latencyMs = 80L, nowMs = 1_000L)
        val ranked = rankShadowCandidates(
            sources = listOf(
                source("https://cdn-a.example/a.m3u8"),
                source("https://cdn-b.example/b.m3u8"),
                source("https://user@cdn-a.example/mirror.m3u8"),
                source("https://cdn-c.example/c.m3u8"),
            ),
            playingHost = "cdn-a.example",
            deadHosts = emptySet(),
            score = { PlaybackHostHealth.score(it, nowMs = 1_000L) },
        )
        assertEquals(
            listOf("https://cdn-c.example/c.m3u8", "https://cdn-b.example/b.m3u8"),
            ranked.map { it.url },
        )
    }

    @Test
    fun aDeadHostIsNotTheStandby() {
        val ranked = rankShadowCandidates(
            sources = listOf(
                source("https://cdn-b.example/b.m3u8"),
                source("https://cdn-c.example/c.m3u8"),
            ),
            playingHost = "cdn-a.example",
            deadHosts = setOf("cdn-b.example"),
        )
        assertEquals(listOf("https://cdn-c.example/c.m3u8"), ranked.map { it.url })
    }

    @Test
    fun aSiblingUrlIsProbedWithoutASecondResolve() = runBlocking {
        var resolved = 0
        val known = listOf(
            source("https://cdn-a.example/playing.m3u8"),
            source("https://cdn-b.example/standby.m3u8"),
        )
        val armed = armLiveShadow(
            known = known,
            playingHost = "cdn-a.example",
            resolve = {
                resolved += 1
                emptyList()
            },
            probe = { "cdn-b" in it.url },
            prefetch = { true },
            score = { 0 },
        )
        assertEquals(0, resolved)
        assertEquals("https://cdn-b.example/standby.m3u8", armed.first().url)
        assertTrue(PlaybackHostHealth.score("cdn-b.example") > 0)
    }

    @Test
    fun theSameHostForcesABackgroundResolve() = runBlocking {
        var resolved = 0
        val armed = armLiveShadow(
            known = listOf(source("https://cdn-a.example/playing.m3u8")),
            playingHost = "cdn-a.example",
            resolve = {
                resolved += 1
                listOf(source("https://cdn-c.example/fresh.m3u8"))
            },
            probe = { "cdn-c" in it.url },
            prefetch = { true },
            score = { 0 },
        )
        assertEquals(1, resolved)
        assertEquals("https://cdn-c.example/fresh.m3u8", armed.single().url)
    }

    @Test
    fun aDeadProbeTriesTheNextHostAndSkipsTheRaceWhenOneLives() = runBlocking {
        val probed = mutableListOf<String>()
        val armed = armLiveShadow(
            known = listOf(
                source("https://cdn-b.example/dead.m3u8"),
                source("https://cdn-c.example/live.m3u8"),
            ),
            playingHost = "cdn-a.example",
            resolve = { error("resolve should not run") },
            probe = {
                probed += it.url
                "cdn-c" in it.url
            },
            prefetch = { true },
            score = { 0 },
        )
        assertEquals(
            listOf("https://cdn-b.example/dead.m3u8", "https://cdn-c.example/live.m3u8"),
            probed,
        )
        assertEquals("https://cdn-c.example/live.m3u8", armed.first().url)
    }
}
