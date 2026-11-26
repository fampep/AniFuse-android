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
}
