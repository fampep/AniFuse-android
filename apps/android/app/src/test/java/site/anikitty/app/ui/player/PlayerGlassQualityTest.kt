// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerGlassQualityTest {
    @Test
    fun resolutionChipLabelsStayCompact() {
        assertEquals("Auto", glassResolutionChipLabel("Auto (best available)"))
        assertEquals("Auto · 1080p", glassResolutionChipLabel("Auto · 1080p"))
        assertEquals("1080p", glassResolutionChipLabel("Full HD 1080p"))
        assertEquals("720p", glassResolutionChipLabel("720P"))
        assertEquals("1080p, selected", glassResolutionChipDescription("1080p", selected = true))
        assertEquals("720p", glassResolutionChipDescription("720p", selected = false))
    }

    @Test
    fun sourceMetaAndRttStayReadable() {
        assertEquals(
            "AniPM · 1080p · SUB",
            glassSourceMetaLine("AniPM", "1080p", "SUB"),
        )
        assertEquals(
            "English",
            glassSourceMetaLine(null, null, null, subtitleFallback = "English"),
        )
        assertEquals("120ms", glassSourceRttLabel(120L))
        assertEquals("1.2s", glassSourceRttLabel(1200L))
        assertNull(glassSourceRttLabel(null))
        assertNull(glassSourceRttLabel(0L))
    }

    @Test
    fun sourcePulseAndShadowStandbyMatchRaceDock() {
        assertEquals(
            "LEAD · 40ms",
            glassSourcePulseLabel(selected = true, latencyMs = 40L, score = 900),
        )
        assertEquals(
            "42ms",
            glassSourcePulseLabel(selected = false, latencyMs = 42L, score = 900),
        )
        assertEquals(
            "HOT",
            glassSourcePulseLabel(selected = false, latencyMs = null, score = 900),
        )
        assertEquals(
            "DOWN",
            glassSourcePulseLabel(selected = false, latencyMs = null, score = -1, cold = true),
        )
        assertEquals("STANDBY", glassShadowStandbyLabel(null))
        assertEquals("STANDBY · 55ms", glassShadowStandbyLabel(55L))
        assertEquals(
            "AniPM · 1080p · SUB · failover",
            glassShadowStandbyMeta("AniPM", "1080p", "SUB"),
        )
        assertEquals("Failover standby", glassShadowStandbyMeta(null, null, null))
        assertEquals(
            "ani.pm · 1080p · SUB · STANDBY · 55ms",
            glassTheaterStatusWithShadow(
                baseParts = listOf("ani.pm", "1080p · SUB"),
                shadowReady = true,
                standbyLatencyMs = 55L,
            ),
        )
        assertEquals(
            "ani.pm · 1080p · SUB",
            glassTheaterStatusWithShadow(
                baseParts = listOf("ani.pm", "1080p · SUB"),
                shadowReady = false,
                standbyLatencyMs = 55L,
            ),
        )
    }

    @Test
    fun sourceRowsPinPlayingThenRaceByScore() {
        assertEquals(emptyList<Int>(), glassSourceRowsOrdered(0, playingIndex = 0) { 0 })
        // Playing index 2 stays first even when its score is cold.
        assertEquals(
            listOf(2, 1, 0, 3),
            glassSourceRowsOrdered(4, playingIndex = 2) { index ->
                when (index) {
                    0 -> 10
                    1 -> 900
                    2 -> -1
                    3 -> 0
                    else -> 0
                }
            },
        )
        // Out-of-range playing → pure score order (higher rises).
        assertEquals(
            listOf(1, 0, 2),
            glassSourceRowsOrdered(3, playingIndex = -1) { index ->
                when (index) {
                    0 -> 50
                    1 -> 200
                    else -> 10
                }
            },
        )
        // Compose key tracks playlist identity, not visual position after reorder.
        assertEquals(2, glassSourceRowKey(2))
        assertEquals(0, glassSourceRowKey(0))
    }
}
