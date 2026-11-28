// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiringInboxKeepTest {
    @Test
    fun keepsPremiereAndNextFrontierEpisodes() {
        // PLANNING / seeded frontier 1 — premiere must still ping.
        assertTrue(airingInboxKeep(scheduleEpisode = 1, playFrontier = 1))
        // After finishing ep 5 the play frontier is 6.
        assertTrue(airingInboxKeep(scheduleEpisode = 6, playFrontier = 6))
        assertFalse(airingInboxKeep(scheduleEpisode = 5, playFrontier = 6))
        assertFalse(airingInboxKeep(scheduleEpisode = 4, playFrontier = 6))
        assertTrue(airingInboxKeep(scheduleEpisode = 1, playFrontier = 0))
    }
}
