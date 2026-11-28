// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
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
}
