// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniFusePlayerChromeTest {
    @Test
    fun plexTransportShowsEpisodeSkipWhenSeriesAllows() {
        assertTrue(chromeShowsPreviousEpisode(showEpisodes = true, episode = 3))
        assertFalse(chromeShowsPreviousEpisode(showEpisodes = true, episode = 1))
        assertFalse(chromeShowsPreviousEpisode(showEpisodes = false, episode = 5))
        assertTrue(chromeShowsNextEpisode(showNext = true))
        assertFalse(chromeShowsNextEpisode(showNext = false))
    }

    @Test
    fun episodeSheetMetaKeepsWatchProgressReadable() {
        assertEquals(
            "24 episodes · watched through E12 · 8m left",
            episodeSelectorMetaLine(total = 24, watchedThrough = 12, minutesLeft = 8),
        )
        assertEquals(
            "12 episodes",
            episodeSelectorMetaLine(total = 12, watchedThrough = 0, minutesLeft = null),
        )
    }
}
