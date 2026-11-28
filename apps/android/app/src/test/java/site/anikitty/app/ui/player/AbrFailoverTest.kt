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
    fun softReleaseSitsBetweenTheCapAndAnUncappedLadder() {
        val seed = 1_500_000
        val release = abrFailoverReleaseBitrate(seed)
        assertTrue(release > abrFailoverCapBitrate(seed))
        assertTrue(release < Int.MAX_VALUE)
        assertEquals(2_400_000, release)
        assertEquals(1_200_000, abrFailoverReleaseBitrate(400_000))
        assertEquals(4_000_000, abrFailoverReleaseBitrate(5_000_000))
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
