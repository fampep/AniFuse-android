// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.anilist.FrontierWarmTarget

class FrontierWarmTest {
    @Test
    fun streamHintCarriesMalWhenKnown() {
        assertEquals("Frieren", frontierStreamHint("Frieren", null))
        assertEquals("Frieren || mal:52991", frontierStreamHint("Frieren", 52991))
        assertEquals("mal:21", frontierStreamHint("  ", 21))
        assertEquals("", frontierStreamHint("", null))
    }

    @Test
    fun warmEligibleGuardsIds() {
        assertTrue(
            frontierWarmEligible(
                FrontierWarmTarget(anilistId = 1, episode = 2, title = "A", malId = null),
            ),
        )
        assertFalse(
            frontierWarmEligible(
                FrontierWarmTarget(anilistId = 0, episode = 2, title = "A", malId = null),
            ),
        )
        assertFalse(
            frontierWarmEligible(
                FrontierWarmTarget(anilistId = 1, episode = 0, title = "A", malId = null),
            ),
        )
    }
}
