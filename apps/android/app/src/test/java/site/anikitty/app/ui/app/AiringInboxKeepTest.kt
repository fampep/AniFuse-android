// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiringInboxKeepTest {
    @Test
    fun keepsUnfinishedScheduleSlots() {
        // Catalog seed / no progress — premiere still pings.
        assertTrue(airingInboxKeep(scheduleEpisode = 1, completedThrough = 0))
        // AniList progress 5 — drop ep 5, keep ep 6.
        assertFalse(airingInboxKeep(scheduleEpisode = 5, completedThrough = 5))
        assertTrue(airingInboxKeep(scheduleEpisode = 6, completedThrough = 5))
        assertFalse(airingInboxKeep(scheduleEpisode = 4, completedThrough = 5))
    }
}
