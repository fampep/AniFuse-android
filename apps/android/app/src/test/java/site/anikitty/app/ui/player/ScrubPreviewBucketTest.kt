// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
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
}
