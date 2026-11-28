// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.skip.SkipInterval
import site.anikitty.app.data.skip.SkipTimes

class ScrubMarkerHapticsTest {
    @Test
    fun edgesLandAtIntroAndCredits() {
        val skip = SkipTimes(
            intro = SkipInterval(0.0, 90.0),
            outro = SkipInterval(1_200.0, 1_260.0),
        )
        val edges = scrubMarkerEdges(skip, durationMs = 1_260_000L)
        assertEquals(4, edges.size)
        assertEquals(0f, edges[0], 0.0001f)
        assertEquals(90f / 1_260f, edges[1], 0.0001f)
        assertEquals(1_200f / 1_260f, edges[2], 0.0001f)
        assertEquals(1f, edges[3], 0.0001f)
        assertTrue(scrubMarkerEdges(SkipTimes(), durationMs = 1_000L).isEmpty())
    }

    @Test
    fun crossingAMarkerTicks() {
        val edges = listOf(0.1f, 0.5f, 0.9f)
        assertTrue(scrubCrossedMarker(from = 0.08f, to = 0.12f, edges = edges))
        assertTrue(scrubCrossedMarker(from = 0.55f, to = 0.45f, edges = edges))
        assertFalse(scrubCrossedMarker(from = 0.2f, to = 0.3f, edges = edges))
        assertFalse(scrubCrossedMarker(from = 0.5f, to = 0.5f, edges = edges))
    }
}
