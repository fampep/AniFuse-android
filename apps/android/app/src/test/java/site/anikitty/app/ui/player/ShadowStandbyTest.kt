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

    @Test
    fun castStallBudgetIsLooserAndGatesOnReceiverClock() {
        assertTrue(CAST_STALL_MS > SHADOW_STALL_MS)
        assertTrue(CAST_COLD_STALL_MS > COLD_STALL_MS)
        assertTrue(CAST_SEEK_STALL_GRACE_MS > CAST_STALL_MS)
        assertEquals(CAST_STALL_MS, castStallBudgetMs(standbyFresh = true))
        assertEquals(CAST_COLD_STALL_MS, castStallBudgetMs(standbyFresh = false))
        assertTrue(
            castTrickleStallAllowed(
                isCasting = true,
                castItemStarted = true,
                castClockStale = false,
            ),
        )
        assertFalse(
            castTrickleStallAllowed(
                isCasting = true,
                castItemStarted = false,
                castClockStale = false,
            ),
        )
        assertFalse(
            castTrickleStallAllowed(
                isCasting = true,
                castItemStarted = true,
                castClockStale = true,
            ),
        )
        assertFalse(
            castTrickleStallAllowed(
                isCasting = false,
                castItemStarted = true,
                castClockStale = false,
            ),
        )
        assertFalse(
            castTrickleStallAllowed(
                isCasting = true,
                castItemStarted = true,
                castClockStale = false,
                seekingOrLoadInFlight = true,
            ),
        )
        // Cast trickle still trips the shared detector on a wider budget.
        val arm = trickleStallTick(
            prevBuffered = -1L,
            stallSince = 0L,
            nowBuffered = 500L,
            nowMs = 1_000L,
            stalled = true,
            budgetMs = CAST_STALL_MS,
        )
        val trip = trickleStallTick(
            prevBuffered = arm.stallBuffered,
            stallSince = arm.stallSince,
            nowBuffered = 520L,
            nowMs = 1_000L + CAST_STALL_MS,
            stalled = true,
            budgetMs = CAST_STALL_MS,
        )
        assertTrue(trip.trip)
    }

    @Test
    fun castSeekGraceAndSourceChangeResetPreventFalseFailover() {
        assertTrue(castSeekSuppressesStall(seekArmedAtMs = 1_000L, nowMs = 1_000L + CAST_STALL_MS))
        assertFalse(
            castSeekSuppressesStall(
                seekArmedAtMs = 1_000L,
                nowMs = 1_000L + CAST_SEEK_STALL_GRACE_MS,
            ),
        )
        assertFalse(castSeekSuppressesStall(seekArmedAtMs = 0L, nowMs = 5_000L))

        // Same URL — keep armed stall state.
        val keep = castStallArmAfterSourceChange(
            prevUrl = "https://cdn/a.m3u8",
            nextUrl = "https://cdn/a.m3u8",
            castItemStarted = true,
            stallBuffered = 900L,
            stallSince = 4_000L,
        )
        assertTrue(keep.castItemStarted)
        assertEquals(900L, keep.stallBuffered)
        assertEquals(4_000L, keep.stallSince)

        // Successor URL — drop predecessor BUFFERING so it cannot burn the next source.
        val reset = castStallArmAfterSourceChange(
            prevUrl = "https://cdn/a.m3u8",
            nextUrl = "https://cdn/b.m3u8",
            castItemStarted = true,
            stallBuffered = 900L,
            stallSince = 4_000L,
        )
        assertFalse(reset.castItemStarted)
        assertEquals(-1L, reset.stallBuffered)
        assertEquals(0L, reset.stallSince)

        // Frozen Cast "buffer" (== playhead) while seeking must not be allowed to trip.
        assertFalse(
            castTrickleStallAllowed(
                isCasting = true,
                castItemStarted = true,
                castClockStale = false,
                seekingOrLoadInFlight = castSeekSuppressesStall(
                    seekArmedAtMs = 10_000L,
                    nowMs = 10_000L + CAST_STALL_MS,
                ),
            ),
        )
        // Same freeze after grace — detector may run again.
        assertTrue(
            castTrickleStallAllowed(
                isCasting = true,
                castItemStarted = true,
                castClockStale = false,
                seekingOrLoadInFlight = castSeekSuppressesStall(
                    seekArmedAtMs = 10_000L,
                    nowMs = 10_000L + CAST_SEEK_STALL_GRACE_MS,
                ),
            ),
        )
    }

    @Test
    fun trickleStallTripsWhenBufferedCreepsUnderEpsilon() {
        // First sample arms the clock.
        val arm = trickleStallTick(
            prevBuffered = -1L,
            stallSince = 0L,
            nowBuffered = 1_000L,
            nowMs = 10_000L,
            stalled = true,
            budgetMs = SHADOW_STALL_MS,
        )
        assertEquals(1_000L, arm.stallBuffered)
        assertEquals(10_000L, arm.stallSince)
        assertFalse(arm.trip)

        // Zombie CDN: +40ms per tick — under epsilon, clock keeps running
        // even as the absolute buffered value creeps upward.
        val mid = trickleStallTick(
            prevBuffered = arm.stallBuffered,
            stallSince = arm.stallSince,
            nowBuffered = 1_040L,
            nowMs = 10_000L + SHADOW_STALL_MS - 1,
            stalled = true,
            budgetMs = SHADOW_STALL_MS,
        )
        assertEquals(1_040L, mid.stallBuffered)
        assertEquals(10_000L, mid.stallSince)
        assertFalse(mid.trip)

        val creep = trickleStallTick(
            prevBuffered = mid.stallBuffered,
            stallSince = mid.stallSince,
            nowBuffered = 1_080L,
            nowMs = 10_000L + SHADOW_STALL_MS - 1,
            stalled = true,
            budgetMs = SHADOW_STALL_MS,
        )
        assertEquals(1_080L, creep.stallBuffered)
        assertEquals(10_000L, creep.stallSince)
        assertFalse(creep.trip)

        val trip = trickleStallTick(
            prevBuffered = creep.stallBuffered,
            stallSince = creep.stallSince,
            nowBuffered = 1_120L,
            nowMs = 10_000L + SHADOW_STALL_MS,
            stalled = true,
            budgetMs = SHADOW_STALL_MS,
        )
        assertTrue(trip.trip)
        assertEquals(10_000L, trip.stallSince)
    }

    @Test
    fun realBufferJumpResetsTrickleClockAndPlayingClearsIt() {
        val jump = trickleStallTick(
            prevBuffered = 1_000L,
            stallSince = 5_000L,
            nowBuffered = 1_000L + TRICKLE_STALL_EPSILON_MS + 1,
            nowMs = 8_000L,
            stalled = true,
            budgetMs = SHADOW_STALL_MS,
        )
        assertEquals(1_000L + TRICKLE_STALL_EPSILON_MS + 1, jump.stallBuffered)
        assertEquals(8_000L, jump.stallSince)
        assertFalse(jump.trip)

        val clear = trickleStallTick(
            prevBuffered = jump.stallBuffered,
            stallSince = jump.stallSince,
            nowBuffered = 2_000L,
            nowMs = 9_000L,
            stalled = false,
            budgetMs = SHADOW_STALL_MS,
        )
        assertEquals(0L, clear.stallSince)
        assertFalse(clear.trip)
    }
}
