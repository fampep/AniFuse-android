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
        assertFalse(liveShadowArmAllowed(playbackLocal = true, playingOffline = false))
        // Progressive file:// arms a live CDN standby while the download plays.
        assertTrue(liveShadowArmAllowed(playbackLocal = true, playingOffline = true))
        assertFalse(liveShadowSwapAllowed(playbackLocal = false, playingOffline = true))
        assertTrue(liveShadowSwapAllowed(playbackLocal = false, playingOffline = false))
        assertFalse(liveShadowSwapAllowed(playbackLocal = true, playingOffline = false))
        assertTrue(offlinePlaybackHoldsSource(playingOffline = true))
        assertFalse(offlinePlaybackHoldsSource(playingOffline = false))
        assertTrue(liveShadowLeaveOfflineAllowed(playbackLocal = false, leaveOffline = true))
        // Progressive leaveOffline may use the armed CDN after the offline hold.
        assertTrue(liveShadowLeaveOfflineAllowed(playbackLocal = true, leaveOffline = true))
        assertFalse(liveShadowLeaveOfflineAllowed(playbackLocal = false, leaveOffline = false))
        assertEquals(LOCAL_SHADOW_HOST, shadowPlayingHost("file:///data/ep.mp4", playbackLocal = true))
        assertEquals("cdn.example", shadowPlayingHost("https://cdn.example/a.m3u8", playbackLocal = false))
        assertEquals(LOCAL_SHADOW_HOST, shadowPlayingHost("content://downloads/1", playbackLocal = true))
        // Progressive arm must avoid the saved CDN — not the synthetic "local" key.
        assertEquals(
            "cdn.saved",
            shadowArmAvoidHost(
                playbackLocal = true,
                sourceUrl = "https://cdn.saved/ep.mp4",
                armKey = LOCAL_SHADOW_HOST,
            ),
        )
        assertEquals(
            "cdn.live",
            shadowArmAvoidHost(
                playbackLocal = false,
                sourceUrl = "https://cdn.live/ep.m3u8",
                armKey = "cdn.live",
            ),
        )
        assertFalse(shadowShouldArmSegmentLane(playbackLocal = true))
        assertTrue(shadowShouldArmSegmentLane(playbackLocal = false))
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
        // Exhausted CDN with an armed standby must swap — not paint the error.
        assertEquals(
            FailureRoute.ShadowSwap,
            failureRoute(SourceRecovery.GiveUp, playlistHasHealthyNext = false, shadowReady = true),
        )
        assertEquals(
            FailureRoute.SameSource,
            failureRoute(SourceRecovery.GiveUp, playlistHasHealthyNext = false, shadowReady = false),
        )
    }
}
