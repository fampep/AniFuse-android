// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.servers.SubtitleTrack

class SubtitleAttachTest {
    @Test
    fun bakeInMergesProviderAndLibrary() {
        val provider = listOf(track("https://cdn.example/a.vtt", "en"))
        val library = listOf(
            track("https://cdn.example/a.vtt", "en"),
            track("https://lib.example/b.vtt", "es"),
        )
        val merged = playbackSubtitleTracks(provider, library)
        assertEquals(2, merged.size)
        assertEquals("https://cdn.example/a.vtt", merged[0].url)
        assertEquals("https://lib.example/b.vtt", merged[1].url)
    }

    @Test
    fun emptyLibraryKeepsProviderOnly() {
        val provider = listOf(track("https://cdn.example/a.vtt", "en"))
        assertEquals(provider, playbackSubtitleTracks(provider, emptyList()))
    }

    @Test
    fun attachProceedsOnceBufferingOrReady() {
        assertTrue(subtitleAttachMayProceed(PLAYBACK_STATE_BUFFERING))
        assertTrue(subtitleAttachMayProceed(PLAYBACK_STATE_READY))
        assertTrue(subtitleAttachMayProceed(PLAYBACK_STATE_ENDED))
        assertFalse(subtitleAttachMayProceed(PLAYBACK_STATE_IDLE))
        assertTrue(subtitleAttachShouldHardReload(PLAYBACK_STATE_IDLE))
        assertFalse(subtitleAttachShouldHardReload(PLAYBACK_STATE_READY))
    }

    @Test
    fun unchangedUriListsShortCircuit() {
        assertTrue(subtitleConfigsUnchanged(listOf("a"), listOf("a")))
        assertFalse(subtitleConfigsUnchanged(listOf("a"), listOf("b")))
    }

    @Test
    fun pollBudgetStaysUnderTwoSeconds() {
        assertTrue(SUBTITLE_ATTACH_MAX_POLLS * SUBTITLE_ATTACH_POLL_MS <= 2_000L)
        assertTrue(SUBTITLE_ATTACH_MAX_POLLS * SUBTITLE_ATTACH_POLL_MS <= 1_250L)
    }

    @Test
    fun fetchWhenPeekIsUnknownEvenIfProviderHasTracks() {
        val provider = listOf(track("https://cdn.example/a.vtt", "en"))
        assertTrue(shouldFetchLibraryCaptions(peeked = null, anilistId = 1, episode = 1))
        assertFalse(shouldFetchLibraryCaptions(peeked = emptyList(), anilistId = 1, episode = 1))
        assertFalse(
            shouldFetchLibraryCaptions(
                peeked = listOf(track("https://lib.example/b.vtt", "es")),
                anilistId = 1,
                episode = 1,
            ),
        )
        assertFalse(shouldFetchLibraryCaptions(peeked = null, anilistId = 0, episode = 1))
        assertEquals(provider, captionsAfterPeek(provider, peeked = null))
        val filled = captionsAfterPeek(provider, listOf(track("https://lib.example/b.vtt", "es")))
        assertEquals(2, filled.size)
    }

    private fun track(url: String, lang: String) = SubtitleTrack(
        url = url,
        language = lang,
        label = lang,
    )
}
