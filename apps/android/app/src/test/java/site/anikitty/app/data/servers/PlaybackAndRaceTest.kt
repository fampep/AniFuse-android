// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.download.progressiveResume
import site.anikitty.app.ui.player.pipAspectRatio

class PlaybackAndRaceTest {
    @Test
    fun transportHeadersAreDroppedAndRefererStays() {
        val kept = playbackHeaders(
            mapOf(
                "Referer" to "https://site/watch",
                "User-Agent" to "browser",
                "Accept-Encoding" to "gzip",
                "Range" to "bytes=0-",
                "Cookie" to "sid=1",
                "X-Token" to "abc",
            ),
        )
        assertEquals("https://site/watch", kept["Referer"])
        assertEquals("sid=1", kept["Cookie"])
        assertEquals("abc", kept["X-Token"])
        assertFalse(kept.keys.any { it.equals("Accept-Encoding", true) })
        assertFalse(kept.keys.any { it.equals("Range", true) })
        assertFalse(kept.keys.any { it.equals("User-Agent", true) })
    }

    @Test
    fun aSlowPreferredResolverLosesToTheNextOne() = runBlocking {
        val winner = raceHeadStart(
            count = 3,
            parallel = 3,
            headStartMs = 30L,
            timeoutMs = 200L,
        ) { index ->
            when (index) {
                0 -> {
                    delay(180)
                    "slow"
                }
                1 -> "fast"
                else -> {
                    delay(150)
                    "later"
                }
            }
        }
        assertEquals(1, winner?.first)
        assertEquals("fast", winner?.second)
    }

    @Test
    fun everyMissReturnsNull() = runBlocking {
        val winner = raceHeadStart<String>(count = 2, headStartMs = 10L, timeoutMs = 40L) { null }
        assertNull(winner)
    }

    @Test
    fun slidingWindowStillReachesPastTheFirstPack() = runBlocking {
        val winner = raceHeadStart(
            count = 6,
            parallel = 2,
            headStartMs = 5L,
            timeoutMs = 80L,
        ) { index ->
            when (index) {
                0, 1, 2, 3, 4 -> {
                    delay(40)
                    null
                }
                else -> "tail"
            }
        }
        assertEquals(5, winner?.first)
        assertEquals("tail", winner?.second)
    }

    @Test
    fun healthyHostsLeadAfterThePreferred() {
        val ranked = preferHealthyHosts(listOf("pick", "dead", "fast", "unknown")) { name ->
            when (name) {
                "fast" -> 900
                "unknown" -> 0
                "dead" -> -1000
                else -> 0
            }
        }
        assertEquals(listOf("pick", "fast", "unknown", "dead"), ranked)
    }

    @Test
    fun aPartialFileAsksForTheRemainder() {
        val fresh = progressiveResume(0L)
        assertNull(fresh.rangeHeader)
        assertFalse(fresh.appendOnPartial)
        val mid = progressiveResume(4_000_000L)
        assertEquals("bytes=4000000-", mid.rangeHeader)
        assertTrue(mid.appendOnPartial)
    }

    @Test
    fun pipAspectClampsAPortraitFrameBackToWidescreen() {
        assertEquals(16 to 9, pipAspectRatio(0, 0))
        assertEquals(1920 to 1080, pipAspectRatio(1920, 1080))
        assertEquals(16 to 9, pipAspectRatio(100, 1000))
    }
}
