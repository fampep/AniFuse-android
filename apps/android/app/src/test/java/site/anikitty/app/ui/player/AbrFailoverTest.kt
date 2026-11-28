// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbrFailoverTest {
    @Test
    fun capTracksTheSeedWithoutExceedingTwelveHundred() {
        assertEquals(500_000, abrFailoverCapBitrate(200_000))
        assertEquals(1_000_000, abrFailoverCapBitrate(1_000_000))
        assertEquals(1_200_000, abrFailoverCapBitrate(4_000_000))
    }

    @Test
    fun holdIsShorterOnAModestSeed() {
        assertTrue(abrFailoverHoldMs(900_000) < abrFailoverHoldMs(3_500_000))
        assertEquals(2_000L, abrFailoverHoldMs(900_000))
        assertEquals(3_200L, abrFailoverHoldMs(3_500_000))
    }

    @Test
    fun hotRaceDockRttSkipsCapAndShortensHolds() {
        assertTrue(abrFailoverSkipCap(80L))
        assertFalse(abrFailoverSkipCap(200L))
        assertFalse(abrFailoverSkipCap(null))
        assertFalse(abrFailoverSkipCap(0L))
        assertEquals(600L, abrFailoverHoldMs(3_500_000, hostLatencyMs = 90L))
        assertTrue(abrFailoverHoldMs(3_500_000, hostLatencyMs = 200L) < abrFailoverHoldMs(3_500_000))
        assertEquals(700L, abrFailoverReleaseHoldMs(80L))
        assertEquals(500L, abrFailoverClimbHoldMs(80L))
        assertEquals(1_400L, abrFailoverReleaseHoldMs(null))
        assertEquals(1_100L, abrFailoverClimbHoldMs(null))
    }

    @Test
    fun softReleaseSitsBetweenTheCapAndAnUncappedLadder() {
        val seed = 1_500_000
        val release = abrFailoverReleaseBitrate(seed)
        assertTrue(release > abrFailoverCapBitrate(seed))
        assertTrue(release < Int.MAX_VALUE)
        assertEquals(2_400_000, release)
        assertEquals(1_200_000, abrFailoverReleaseBitrate(400_000))
        assertEquals(4_000_000, abrFailoverReleaseBitrate(5_000_000))
        val climb = abrFailoverClimbBitrate(seed)
        assertTrue(climb > release)
        assertTrue(climb < Int.MAX_VALUE)
        assertEquals(4_200_000, climb)
        assertEquals(1_400L, abrFailoverReleaseHoldMs())
        assertEquals(1_100L, abrFailoverClimbHoldMs())
        assertEquals("ABR · HOLD", abrFailoverStatusPill(AbrFailoverPhase.Cap))
        assertEquals("ABR · SOFT", abrFailoverStatusPill(AbrFailoverPhase.Soft))
        assertEquals("ABR · CLIMB", abrFailoverStatusPill(AbrFailoverPhase.Climb))
        assertEquals(null, abrFailoverStatusPill(AbrFailoverPhase.Idle))
    }

    @Test
    fun reseedMatchesTheSoftCap() {
        assertEquals(1_200_000L, abrFailoverReseedBitrate(4_000_000))
        assertEquals(500_000L, abrFailoverReseedBitrate(100_000))
        assertEquals(1_000_000L, abrFailoverReseedBitrate(1_000_000))
    }

    @Test
    fun reseedOnlyWhenTheCdnHostChanges() {
        assertTrue(abrFailoverShouldReseed("cdn-a.example", "cdn-b.example"))
        assertFalse(abrFailoverShouldReseed("cdn-a.example", "cdn-a.example"))
        assertFalse(abrFailoverShouldReseed(null, "cdn-a.example"))
        assertFalse(abrFailoverShouldReseed("cdn-a.example", null))
        assertFalse(abrFailoverShouldReseed(" CDN-A.example ", "cdn-a.example"))
    }
}
