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
}
