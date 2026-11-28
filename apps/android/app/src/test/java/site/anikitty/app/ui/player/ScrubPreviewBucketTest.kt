// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubPreviewBucketTest {
    @Test
    fun bucketsCollapsePixelNoise() {
        assertEquals(0L, scrubPreviewBucketMs(0L))
        assertEquals(0L, scrubPreviewBucketMs(999L))
        assertEquals(1_000L, scrubPreviewBucketMs(1_000L))
        assertEquals(1_000L, scrubPreviewBucketMs(1_499L))
        assertEquals(2_000L, scrubPreviewBucketMs(2_001L))
        assertEquals(0L, scrubPreviewBucketMs(-50L))
    }

    @Test
    fun seekPreviewCardIsCompactOnPhone() {
        assertEquals(118f, seekPreviewCardWidthDp(360f))
        assertEquals(118f, seekPreviewCardWidthDp(479f))
        assertEquals(156f, seekPreviewCardWidthDp(480f))
        assertEquals(156f, seekPreviewCardWidthDp(900f))
        // 16:9 plane stays reserved — cold scrub must not collapse to footer-only.
        assertEquals(66.375f, seekPreviewMediaHeightDp(118f), 0.001f)
        assertEquals(87.75f, seekPreviewMediaHeightDp(156f), 0.001f)
        assertTrue(seekPreviewShowsMediaPlane(scrubbing = true))
        assertFalse(seekPreviewShowsMediaPlane(scrubbing = false))
    }

    @Test
    fun retainKeepsLastPlateOnDecodeMiss() {
        assertEquals("prev", scrubPreviewRetainFrame("prev", null))
        assertEquals("next", scrubPreviewRetainFrame("prev", "next"))
        assertNull(scrubPreviewRetainFrame<String>(null, null))
        assertTrue(scrubPreviewShouldClear(scrubbing = false, scrubBucketMs = 1_000L))
        assertFalse(scrubPreviewShouldClear(scrubbing = true, scrubBucketMs = 1_000L))
        assertTrue(scrubPreviewShouldClear(scrubbing = true, scrubBucketMs = -1L))
        assertEquals(0, scrubPreviewCrossfadeMs(reducedMotion = true))
        assertEquals(SCRUB_PREVIEW_CROSSFADE_MS, scrubPreviewCrossfadeMs(reducedMotion = false))
    }

    @Test
    fun adaptiveBucketsPreferCueSpan() {
        assertEquals(0L, scrubPreviewBucketForCues(400L, cueSpanMs = 500L))
        assertEquals(500L, scrubPreviewBucketForCues(500L, cueSpanMs = 500L))
        assertEquals(0L, scrubPreviewBucketForCues(900L, cueSpanMs = null))
        assertEquals(1_000L, scrubPreviewBucketForCues(1_200L, cueSpanMs = null))
        // Clamp absurd spans into 250ms…2s.
        assertEquals(0L, scrubPreviewBucketForCues(100L, cueSpanMs = 50L))
        assertEquals(0L, scrubPreviewBucketForCues(1_500L, cueSpanMs = 10_000L))
        assertEquals(2_000L, scrubPreviewBucketForCues(2_000L, cueSpanMs = 10_000L))
    }

    @Test
    fun seekPreviewFooterAndTalkBackStayReadable() {
        assertEquals("01:12", seekPreviewFooter("01:12", null))
        assertEquals("Intro · 01:12", seekPreviewFooter("01:12", "Intro"))
        assertEquals("Seek preview, 01:12", seekPreviewDescription("01:12", null))
        assertEquals(
            "Seek preview, Intro, 01:12",
            seekPreviewDescription("01:12", "Intro"),
        )
        assertEquals(
            "https://cdn/s.jpg#10,20,160,90@2",
            thumbnailCropCacheKey("https://cdn/s.jpg", 10, 20, 160, 90, 2),
        )
        assertTrue(scrubPreviewWarmOnScrubStartOnly(scrubbing = true, previousScrubbing = false))
        assertFalse(scrubPreviewWarmOnScrubStartOnly(scrubbing = true, previousScrubbing = true))
        assertFalse(scrubPreviewWarmOnScrubStartOnly(scrubbing = false, previousScrubbing = true))
    }
}
