// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeWarmTest {
    @Test
    fun warmsAfterSixtyFivePercentOnARealEpisode() {
        assertFalse(shouldWarmNextEpisode(10_000L, 1_200_000L, hasNext = true, alreadyWarmed = false))
        assertFalse(shouldWarmNextEpisode(700_000L, 1_200_000L, hasNext = true, alreadyWarmed = false))
        assertTrue(shouldWarmNextEpisode(780_000L, 1_200_000L, hasNext = true, alreadyWarmed = false))
        assertFalse(shouldWarmNextEpisode(780_000L, 1_200_000L, hasNext = true, alreadyWarmed = true))
        assertFalse(shouldWarmNextEpisode(780_000L, 1_200_000L, hasNext = false, alreadyWarmed = false))
        assertFalse(shouldWarmNextEpisode(40_000L, 50_000L, hasNext = true, alreadyWarmed = false))
    }
}