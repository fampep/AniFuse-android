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

    @Test
    fun plexEdgeZonesKeepCenterFreeForScrub() {
        assertEquals(PlayerEdgeZone.Brightness, playerEdgeZone(x = 10f, width = 1000))
        assertEquals(PlayerEdgeZone.Volume, playerEdgeZone(x = 950f, width = 1000))
        assertEquals(PlayerEdgeZone.Center, playerEdgeZone(x = 500f, width = 1000))
    }

    @Test
    fun gestureScrubFractionTracksHorizontalDrag() {
        assertEquals(0.5f, gestureScrubFraction(originMs = 30_000L, deltaX = 0f, width = 1000, durationMs = 60_000L), 0.001f)
        assertEquals(0.75f, gestureScrubFraction(originMs = 30_000L, deltaX = 250f, width = 1000, durationMs = 60_000L), 0.001f)
        assertEquals(0f, gestureScrubFraction(originMs = 5_000L, deltaX = -9_000f, width = 1000, durationMs = 60_000L), 0.001f)
    }

    @Test
    fun glassSkipLabelAndDockClearance() {
        assertEquals("Skip Intro", glassSkipLabel("intro"))
        assertEquals("Skip Outro", glassSkipLabel("credits"))
        assertEquals(22, glassSkipBottomPaddingDp(chromeUp = false, casting = false).value.toInt())
        assertEquals(128, glassSkipBottomPaddingDp(chromeUp = true, casting = false).value.toInt())
        assertEquals(136, glassSkipBottomPaddingDp(chromeUp = true, casting = true).value.toInt())
    }
}
