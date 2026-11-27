// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test
import site.anikitty.app.data.servers.SubtitleTrack

class SubtitleMergeTest {
    @Test
    fun libraryFillsGapsWithoutDuplicatingProviderUrls() {
        val provider = listOf(SubtitleTrack(url = "https://cdn/a.vtt", label = "Eng"))
        val library = listOf(
            SubtitleTrack(url = "https://cdn/a.vtt", label = "English"),
            SubtitleTrack(url = "https://lib/b.vtt", label = "Spa"),
        )
        val merged = mergeSubtitleTracks(provider, library)
        assertEquals(2, merged.size)
        assertEquals("Eng", merged[0].label)
        assertEquals("https://lib/b.vtt", merged[1].url)
    }
}
