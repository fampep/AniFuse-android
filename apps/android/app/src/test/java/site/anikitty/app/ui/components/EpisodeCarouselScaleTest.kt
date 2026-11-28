// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeCarouselScaleTest {
    @Test
    fun centerFrameStaysFullScale() {
        assertEquals(1f, episodeCarouselScale(distanceFromCenterPx = 0f, reduced = false), 0f)
        assertEquals(1f, episodeCarouselScale(distanceFromCenterPx = 400f, reduced = true), 0f)
    }

    @Test
    fun edgesDipHarderThanTheOldEightPercent() {
        val edge = episodeCarouselScale(distanceFromCenterPx = 900f, reduced = false)
        assertTrue(edge <= 0.86f + 0.001f)
        assertTrue(edge >= 0.86f - 0.001f)
    }
}
