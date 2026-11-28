// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import site.anikitty.app.data.skip.SkipInterval
import site.anikitty.app.data.skip.SkipTimes

class ScrubChapterLabelTest {
    @Test
    fun labelsActiveSkipWindows() {
        val skip = SkipTimes(
            intro = SkipInterval(0.0, 90.0),
            outro = SkipInterval(1_200.0, 1_260.0),
            recap = SkipInterval(90.0, 120.0),
            preview = SkipInterval(1_260.0, 1_300.0),
        )
        assertEquals("Intro", scrubChapterLabel(skip, positionMs = 1_000L))
        assertEquals("Recap", scrubChapterLabel(skip, positionMs = 100_000L))
        assertEquals("Credits", scrubChapterLabel(skip, positionMs = 1_230_000L))
        assertEquals("Preview", scrubChapterLabel(skip, positionMs = 1_270_000L))
        assertNull(scrubChapterLabel(skip, positionMs = 600_000L))
        assertNull(scrubChapterLabel(SkipTimes(), positionMs = 1_000L))
    }
}
