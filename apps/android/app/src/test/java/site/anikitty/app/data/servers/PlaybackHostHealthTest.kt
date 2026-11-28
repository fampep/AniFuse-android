// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackHostHealthTest {
    @After
    fun reset() {
        PlaybackHostHealth.detach()
    }

    @Test
    fun aLeavingPlayerDoesNotClearTheHostTheNextEpisodeRecorded() {
        val first = Any()
        val next = Any()
        PlaybackHostHealth.notePlaying("cdn-a.example", first)
        PlaybackHostHealth.notePlaying("cdn-b.example", next)
        PlaybackHostHealth.clearPlaying(first)
        assertEquals("cdn-b.example", PlaybackHostHealth.playingHost())
        PlaybackHostHealth.clearPlaying(next)
        assertEquals(null, PlaybackHostHealth.playingHost())
    }

    @Test
    fun aFailureSinksAndAFasterSuccessOutranksASlowOne() {
        PlaybackHostHealth.detach()
        PlaybackHostHealth.note("cdn-a.example", ok = false, nowMs = 5_000L)
        PlaybackHostHealth.note("cdn-b.example", ok = true, latencyMs = 2_000L, nowMs = 5_000L)
        PlaybackHostHealth.note("cdn-c.example", ok = true, latencyMs = 100L, nowMs = 5_000L)
        assertEquals(-1_000, PlaybackHostHealth.score("cdn-a.example", nowMs = 5_000L))
        assertTrue(
            PlaybackHostHealth.score("cdn-c.example", nowMs = 5_000L) >
                PlaybackHostHealth.score("cdn-b.example", nowMs = 5_000L),
        )
        assertEquals(0, PlaybackHostHealth.score("cdn-a.example", nowMs = 5_000L + PlaybackHostHealth.HOLD_MS + 1))
    }

    @Test
    fun marksSurviveANewProcessWithoutRewritingAnUnchangedLedger() {
        val dir = File.createTempFile("hosts", "").apply {
            delete()
            mkdirs()
        }
        val now = System.currentTimeMillis()
        PlaybackHostHealth.detach()
        PlaybackHostHealth.attach(dir)
        PlaybackHostHealth.note("CDN-A.example", ok = false, latencyMs = 40L, nowMs = now)
        PlaybackHostHealth.detach()
        PlaybackHostHealth.attach(dir)
        assertEquals(-1_000, PlaybackHostHealth.score("cdn-a.example", nowMs = now))
        dir.deleteRecursively()
    }

    @Test
    fun providerScoreUsesCdnNotSiteUrl() {
        PlaybackHostHealth.detach()
        PlaybackHostHealth.note("cdn.good", ok = true, latencyMs = 50L, nowMs = 1_000L)
        PlaybackHostHealth.note("site.example", ok = false, nowMs = 1_000L)
        PlaybackHostHealth.noteProviderCdn("anipm", "cdn.good")
        assertTrue(
            PlaybackHostHealth.scoreProvider("anipm", "https://site.example/", nowMs = 1_000L) > 0,
        )
        // Unknown provider falls back to site host mark.
        assertEquals(
            -1_000,
            PlaybackHostHealth.scoreProvider("other", "https://site.example/", nowMs = 1_000L),
        )
    }

    @Test
    fun aZeroLatencyOkNoteKeepsTheStampedResolveRtt() {
        PlaybackHostHealth.detach()
        PlaybackHostHealth.note("cdn.fast", ok = true, latencyMs = 420L, nowMs = 1_000L)
        PlaybackHostHealth.noteProviderCdn("anikoto", "cdn.fast")
        // Playback ready / shadow arm used to wipe this to 0.
        PlaybackHostHealth.note("cdn.fast", ok = true, latencyMs = 0L, nowMs = 1_200L)
        assertEquals(
            420L,
            PlaybackHostHealth.providerLatencyMs("anikoto", "https://site.example/", nowMs = 1_200L),
        )
        // A real new sample still replaces the old one.
        PlaybackHostHealth.note("cdn.fast", ok = true, latencyMs = 90L, nowMs = 1_400L)
        assertEquals(
            90L,
            PlaybackHostHealth.providerLatencyMs("anikoto", "https://site.example/", nowMs = 1_400L),
        )
    }
}
