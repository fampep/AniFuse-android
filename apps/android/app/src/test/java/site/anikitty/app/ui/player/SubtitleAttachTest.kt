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
    fun identityDriftAbortsAttachAfterFailover() {
        assertTrue(
            subtitleAttachMediaIdentityDrifted(
                expectedUri = "https://cdn-a.example/ep.m3u8",
                liveUri = "https://cdn-b.example/ep.m3u8",
            ),
        )
        assertFalse(
            subtitleAttachMediaIdentityDrifted(
                expectedUri = "https://cdn-a.example/ep.m3u8",
                liveUri = "https://cdn-a.example/ep.m3u8",
            ),
        )
        assertTrue(subtitleAttachMediaIdentityDrifted(expectedUri = "", liveUri = "https://x"))
        assertTrue(subtitleAttachMediaIdentityDrifted(expectedUri = "https://x", liveUri = null))
    }

    @Test
    fun attachActionRearmsOnDriftAndChoosesSoftOrHard() {
        val next = listOf("https://lib.example/a.vtt")
        // CDN failover under us — re-arm against the live URI once.
        assertEquals(
            SubtitleAttachAction.ReArm,
            subtitleAttachAction(
                expectedUri = "https://cdn-a.example/ep.m3u8",
                liveUri = "https://cdn-b.example/ep.m3u8",
                liveItemPresent = true,
                existingUris = emptyList(),
                nextUris = next,
                playbackState = PLAYBACK_STATE_READY,
                itemIndex = 0,
                mediaItemCount = 1,
            ),
        )
        assertEquals(
            SubtitleAttachAction.None,
            subtitleAttachAction(
                expectedUri = "https://cdn-a.example/ep.m3u8",
                liveUri = "https://cdn-a.example/ep.m3u8",
                liveItemPresent = true,
                existingUris = next,
                nextUris = next,
                playbackState = PLAYBACK_STATE_READY,
                itemIndex = 0,
                mediaItemCount = 1,
            ),
        )
        assertEquals(
            SubtitleAttachAction.SoftReplace,
            subtitleAttachAction(
                expectedUri = "https://cdn-a.example/ep.m3u8",
                liveUri = "https://cdn-a.example/ep.m3u8",
                liveItemPresent = true,
                existingUris = emptyList(),
                nextUris = next,
                playbackState = PLAYBACK_STATE_READY,
                itemIndex = 0,
                mediaItemCount = 1,
            ),
        )
        assertEquals(
            SubtitleAttachAction.HardReload,
            subtitleAttachAction(
                expectedUri = "https://cdn-a.example/ep.m3u8",
                liveUri = "https://cdn-a.example/ep.m3u8",
                liveItemPresent = true,
                existingUris = emptyList(),
                nextUris = next,
                playbackState = PLAYBACK_STATE_IDLE,
                itemIndex = 0,
                mediaItemCount = 1,
            ),
        )
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

    @Test
    fun statusPillTracksColdAttachPhases() {
        assertEquals("CC · WARM", subtitleAttachStatusPill(SubtitleAttachPhase.Fetch))
        assertEquals("CC · LIVE", subtitleAttachStatusPill(SubtitleAttachPhase.Soft))
        assertEquals("CC · LIVE", subtitleAttachStatusPill(SubtitleAttachPhase.Bake))
        assertEquals(null, subtitleAttachStatusPill(SubtitleAttachPhase.Idle))
        assertEquals(null, subtitleAttachStatusPill(SubtitleAttachPhase.Ready))
        assertEquals(
            SubtitleAttachPhase.Soft,
            subtitleAttachPhaseAfterAction(SubtitleAttachAction.SoftReplace),
        )
        assertEquals(
            SubtitleAttachPhase.Bake,
            subtitleAttachPhaseAfterAction(SubtitleAttachAction.HardReload),
        )
        // Unchanged / already baked must clear CC · WARM.
        assertEquals(
            SubtitleAttachPhase.Ready,
            subtitleAttachPhaseAfterAction(SubtitleAttachAction.None),
        )
        assertEquals(null, subtitleAttachPhaseAfterAction(SubtitleAttachAction.ReArm))
        assertEquals(SubtitleAttachPhase.Idle, subtitleAttachPhaseAfterFetch(0))
        assertEquals(SubtitleAttachPhase.Fetch, subtitleAttachPhaseAfterFetch(2))
        assertTrue(SUBTITLE_ATTACH_MAX_REARMS >= 1)
    }

    private fun track(url: String, lang: String) = SubtitleTrack(
        url = url,
        language = lang,
        label = lang,
    )
}
