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
}
