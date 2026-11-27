// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeWarmTest {
    private fun source(url: String) = PlayableSource(url = url, label = url)

    @Test
    fun aDeadWarmUrlIsDroppedAndTheFreshRaceIsKept() = runBlocking {
        val cached = listOf(source("https://dead.cdn/a.m3u8"))
        val fresh = listOf(source("https://live.cdn/b.m3u8"))
        val chosen = warmPlayable(
            existing = cached,
            resolve = { fresh },
            probe = { sources -> sources.filter { "live" in it.url } },
        )
        assertEquals("https://live.cdn/b.m3u8", chosen.single().url)
    }

    @Test
    fun aLiveWarmUrlSkipsTheRace() = runBlocking {
        var resolved = 0
        val cached = listOf(source("https://live.cdn/a.m3u8"))
        val chosen = warmPlayable(
            existing = cached,
            resolve = {
                resolved += 1
                listOf(source("https://other.cdn/b.m3u8"))
            },
            probe = { it },
        )
        assertEquals(0, resolved)
        assertEquals(cached, chosen)
    }

    @Test
    fun aSecondMissDoesNotKeepTheDeadUrl() = runBlocking {
        val chosen = warmPlayable(
            existing = listOf(source("https://dead.cdn/a.m3u8")),
            resolve = { listOf(source("https://also-dead.cdn/b.m3u8")) },
            probe = { emptyList() },
        )
        assertTrue(chosen.isEmpty())
    }

    @Test
    fun standbyDropsTheHostThatIsAlreadyPlaying() {
        val sources = listOf(
            source("https://cdn-a.example/ep.m3u8"),
            source("https://cdn-b.example:443/ep.m3u8"),
            source("https://user@cdn-a.example/other.m3u8"),
        )
        val standby = creditsStandby(sources, "cdn-a.example")
        assertEquals(listOf("https://cdn-b.example:443/ep.m3u8"), standby.map { it.url })
    }

    @Test
    fun encryptedSourcesAreNotPrefetched() {
        assertTrue(canPrefetch(source("https://cdn.example/master.m3u8")))
        assertFalse(canPrefetch(source("file:///storage/ep.mp4")))
        assertFalse(
            canPrefetch(
                PlayableSource(
                    url = "https://cdn.example/wrapped",
                    flix = FlixUnwrap(playlistKey = "k", segmentMask = "m", englishAudio = false),
                ),
            ),
        )
    }
}
