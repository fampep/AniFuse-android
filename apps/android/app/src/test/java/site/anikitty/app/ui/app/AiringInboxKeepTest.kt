// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiringInboxKeepTest {
    @Test
    fun dropsEpisodesAlreadyFinishedLocally() {
        assertFalse(airingInboxKeep(scheduleEpisode = 5, watchedThrough = 5))
        assertFalse(airingInboxKeep(scheduleEpisode = 4, watchedThrough = 5))
        assertTrue(airingInboxKeep(scheduleEpisode = 6, watchedThrough = 5))
        assertTrue(airingInboxKeep(scheduleEpisode = 1, watchedThrough = 0))
    }
}
