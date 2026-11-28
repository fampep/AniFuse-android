// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeWarmTest {
    @Test
    fun warmsAfterFortyTwoPercentOnARealEpisode() {
        assertFalse(shouldWarmNextEpisode(10_000L, 1_200_000L, hasNext = true, alreadyWarmed = false))
        assertFalse(shouldWarmNextEpisode(500_000L, 1_200_000L, hasNext = true, alreadyWarmed = false))
        assertTrue(shouldWarmNextEpisode(510_000L, 1_200_000L, hasNext = true, alreadyWarmed = false))
        assertFalse(shouldWarmNextEpisode(510_000L, 1_200_000L, hasNext = true, alreadyWarmed = true))
        assertFalse(shouldWarmNextEpisode(510_000L, 1_200_000L, hasNext = false, alreadyWarmed = false))
        assertFalse(shouldWarmNextEpisode(40_000L, 50_000L, hasNext = true, alreadyWarmed = false))
    }

    @Test
    fun creditsArmAfterTheNextEpisodeIsAlreadyWarm() {
        val duration = 1_200_000L
        assertEquals(
            StreamWarmAction.PrefetchCaptions,
            streamWarmAction(300_000L, duration, hasNext = true, nextWarmed = false, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.None,
            streamWarmAction(
                300_000L,
                duration,
                hasNext = true,
                nextWarmed = false,
                creditsArmed = false,
                captionsWarmed = true,
            ),
        )
        assertEquals(
            StreamWarmAction.ResolveNext,
            streamWarmAction(510_000L, duration, hasNext = true, nextWarmed = false, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.None,
            streamWarmAction(800_000L, duration, hasNext = true, nextWarmed = true, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.ArmCredits,
            streamWarmAction(840_000L, duration, hasNext = true, nextWarmed = true, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.ResolveNext,
            streamWarmAction(840_000L, duration, hasNext = true, nextWarmed = false, creditsArmed = true),
        )
        assertEquals(
            StreamWarmAction.ArmCredits,
            streamWarmAction(840_000L, duration, hasNext = false, nextWarmed = false, creditsArmed = false),
        )
        assertEquals(
            StreamWarmAction.None,
            streamWarmAction(40_000L, 50_000L, hasNext = true, nextWarmed = false, creditsArmed = false),
        )
    }

    @Test
    fun nextCaptionsWarmWhileResolveIsStillDue() {
        assertTrue(shouldWarmNextCaptions(hasNext = true, nextWarmed = false))
        assertFalse(shouldWarmNextCaptions(hasNext = true, nextWarmed = true))
        assertFalse(shouldWarmNextCaptions(hasNext = false, nextWarmed = false))
        assertEquals(0.25f, NEXT_CAPTION_WARM_FRACTION)
    }
}
