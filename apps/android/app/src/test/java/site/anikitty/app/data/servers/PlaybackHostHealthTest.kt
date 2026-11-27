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
}
