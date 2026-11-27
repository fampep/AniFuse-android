// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceFailoverTest {
    @Test
    fun bufferedPlaybackKeepsTheLivePlayheadOnTheNextSource() {
        val decision = sourceFailover(
            index = 0,
            count = 3,
            livePositionMs = 12_400L,
            heldResumeMs = 2_000L,
            historyMs = 90_000L,
            allowHistory = true,
        )
        assertEquals(1, decision.nextIndex)
        assertEquals(12_400L, decision.resumeMs)
    }

    @Test
    fun errorBeforePlaybackUsesHistoryOnlyWhenAllowed() {
        val withHistory = sourceFailover(
            index = 0,
            count = 2,
            livePositionMs = 0L,
            heldResumeMs = 0L,
            historyMs = 48_000L,
            allowHistory = true,
        )
        assertEquals(48_000L, withHistory.resumeMs)
        val midEpisode = sourceFailover(
            index = 1,
            count = 2,
            livePositionMs = 400L,
            heldResumeMs = 0L,
            historyMs = 48_000L,
            allowHistory = false,
        )
        assertNull(midEpisode.nextIndex)
        assertEquals(400L, midEpisode.resumeMs)
    }

    @Test
    fun deadHostIsSkippedForADifferentCdn() {
        val urls = listOf(
            "https://cdn-a.example/a.m3u8",
            "https://user:token@cdn-a.example:443/b.m3u8?sig=1",
            "https://cdn-b.example/c.m3u8",
        )
        assertEquals("cdn-a.example", playbackHost(urls[0]))
        assertEquals("cdn-a.example", playbackHost(urls[1]))
        assertEquals(2, nextHealthySource(urls, 0, setOf("cdn-a.example")))
        assertEquals(null, nextHealthySource(urls, 2, setOf("cdn-b.example")))
    }

    @Test
    fun freshResolveMustChangeTheUrls() {
        assertEquals(false, hasNewPlayableUrls(listOf("https://cdn/a"), listOf("https://cdn/a")))
        assertEquals(false, hasNewPlayableUrls(listOf("https://cdn/a"), emptyList()))
        assertEquals(true, hasNewPlayableUrls(listOf("https://cdn/a?sig=old"), listOf("https://cdn/a?sig=new")))
    }

    @Test
    fun decoderGlitchDoesNotBurnAPlayingSource() {
        assertEquals(SourceRecovery.NextSource, sourceRecovery(2004, 403, 12_000L))
        assertEquals(SourceRecovery.SeekLive, sourceRecovery(1002, null, 4_000L))
        assertEquals(SourceRecovery.RetrySame, sourceRecovery(4003, null, 20_000L))
        assertEquals(SourceRecovery.NextSource, sourceRecovery(4001, null, 0L))
        assertEquals(SourceRecovery.GiveUp, sourceRecovery(4001, null, 30_000L))
        assertEquals(403, httpStatusOf(IllegalStateException("Response code: 403")))
    }
}
