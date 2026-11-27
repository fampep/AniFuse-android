// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeWarmTest {
    @Test
    fun warmsAfterFiftyFivePercentOnARealEpisode() {
        assertFalse(shouldWarmNextEpisode(10_000L, 1_200_000L, hasNext = true, alreadyWarmed = false))
        assertFalse(shouldWarmNextEpisode(640_000L, 1_200_000L, hasNext = true, alreadyWarmed = false))
        assertTrue(shouldWarmNextEpisode(700_000L, 1_200_000L, hasNext = true, alreadyWarmed = false))
        assertFalse(shouldWarmNextEpisode(700_000L, 1_200_000L, hasNext = true, alreadyWarmed = true))
        assertFalse(shouldWarmNextEpisode(700_000L, 1_200_000L, hasNext = false, alreadyWarmed = false))
        assertFalse(shouldWarmNextEpisode(40_000L, 50_000L, hasNext = true, alreadyWarmed = false))
    }

    @Test
    fun creditsArmAfterTheNextEpisodeIsAlreadyWarm() {
        val duration = 1_200_000L
        assertEquals(
            StreamWarmAction.None,
            streamWarmAction(640_000L, duration, hasNext = true, nextWarmed = false, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.ResolveNext,
            streamWarmAction(700_000L, duration, hasNext = true, nextWarmed = false, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.None,
            streamWarmAction(800_000L, duration, hasNext = true, nextWarmed = true, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.ArmCredits,
            streamWarmAction(950_000L, duration, hasNext = true, nextWarmed = true, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.ResolveNext,
            streamWarmAction(950_000L, duration, hasNext = true, nextWarmed = false, creditsArmed = true),
        )
        assertEquals(
            StreamWarmAction.ArmCredits,
            streamWarmAction(950_000L, duration, hasNext = false, nextWarmed = false, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.None,
            streamWarmAction(40_000L, 50_000L, hasNext = true, nextWarmed = false, creditsArmed = false),
        )
    }
}