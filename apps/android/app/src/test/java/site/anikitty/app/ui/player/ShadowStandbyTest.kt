// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowStandbyTest {
    @Test
    fun armAllowsHlsCacheButSwapWaitsUntilOfflineEnds() {
        assertTrue(liveShadowArmAllowed(playbackLocal = false))
        assertFalse(liveShadowArmAllowed(playbackLocal = true))
        assertFalse(liveShadowSwapAllowed(playbackLocal = false, playingOffline = true))
        assertTrue(liveShadowSwapAllowed(playbackLocal = false, playingOffline = false))
        assertFalse(liveShadowSwapAllowed(playbackLocal = true, playingOffline = false))
        assertTrue(offlinePlaybackHoldsSource(playingOffline = true))
        assertFalse(offlinePlaybackHoldsSource(playingOffline = false))
    }

    @Test
    fun playbackReadyArmsOncePerHost() {
        assertEquals(ShadowArm.Hold, shadowArm(playbackReady = false, armedHost = null, playingHost = "cdn-a.example"))
        assertEquals(ShadowArm.Arm, shadowArm(playbackReady = true, armedHost = null, playingHost = "cdn-a.example"))
        assertEquals(ShadowArm.Hold, shadowArm(playbackReady = true, armedHost = "cdn-a.example", playingHost = "CDN-A.example"))
        assertEquals(ShadowArm.Arm, shadowArm(playbackReady = true, armedHost = "cdn-a.example", playingHost = "cdn-b.example"))
    }

    @Test
    fun aFreshDifferentHostIsReadyAndAStaleOrSameHostIsNot() {
        assertTrue(
            shadowStandbyReady(
                playingHost = "cdn-a.example",
                standbyHost = "cdn-b.example",
                standbyAgeMs = 1_000L,
            ),
        )
        assertFalse(
            shadowStandbyReady(
                playingHost = "cdn-a.example",
                standbyHost = "cdn-a.example",
                standbyAgeMs = 1_000L,
            ),
        )
        assertFalse(
            shadowStandbyReady(
                playingHost = "cdn-a.example",
                standbyHost = "cdn-b.example",
                standbyAgeMs = SHADOW_FRESH_MS + 1,
            ),
        )
    }

    @Test
    fun aProbedStandbyShortensTheStallAndTakesTheFailure() {
        assertEquals(COLD_STALL_MS, shadowStallBudgetMs(standbyFresh = false))
        assertEquals(SHADOW_STALL_MS, shadowStallBudgetMs(standbyFresh = true))
        assertEquals(
            FailureRoute.ShadowSwap,
            failureRoute(SourceRecovery.NextSource, playlistHasHealthyNext = true, shadowReady = true),
        )
        assertEquals(
            FailureRoute.NextInPlaylist,
            failureRoute(SourceRecovery.NextSource, playlistHasHealthyNext = true, shadowReady = false),
        )
        assertEquals(
            FailureRoute.ColdResolve,
            failureRoute(SourceRecovery.NextSource, playlistHasHealthyNext = false, shadowReady = false),
        )
        assertEquals(
            FailureRoute.SameSource,
            failureRoute(SourceRecovery.RetrySame, playlistHasHealthyNext = true, shadowReady = true),
        )
    }
}
