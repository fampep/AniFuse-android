// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.servers.SubtitleTrack

class CastSubtitlesTest {
    @Test
    fun activeIdsEmptyWhenCaptionsOff() {
        assertTrue(
            castActiveTextTrackIds(
                configs = emptyList(),
                tracks = emptyList(),
                enabled = false,
            ).isEmpty(),
        )
    }

    @Test
    fun pickTextTrackIdEmptyWhenOffOrMissing() {
        assertTrue(castPickTextTrackId(emptyList(), enabled = true).isEmpty())
        assertTrue(castPickTextTrackId(emptyList(), enabled = false).isEmpty())
    }

    @Test
    fun finishedIdleIsEpisodeEnd() {
        // MediaStatus.PLAYER_STATE_IDLE == 1, IDLE_REASON_FINISHED == 1.
        assertTrue(castPlaybackFinished(playerState = 1, idleReason = 1))
        assertTrue(!castPlaybackFinished(playerState = 1, idleReason = 2))
        assertTrue(!castPlaybackFinished(playerState = 2, idleReason = 1))
        assertTrue(!castPlaybackFinished(playerState = 1, idleReason = 4))
    }

    @Test
    fun leftoverFinishDoesNotEndTheNextLoad() {
        assertTrue(!castItemEndedAfterStart(sawPlayback = false, playerState = 1, idleReason = 1))
        assertTrue(castItemEndedAfterStart(sawPlayback = true, playerState = 1, idleReason = 1))
        assertTrue(!castItemEndedAfterStart(sawPlayback = true, playerState = 2, idleReason = 1))
    }

    @Test
    fun volumeNudgeStepsSixPercent() {
        assertEquals(0.56f, castVolumeAfterNudge(0.50f, 0.02f), 0.001f)
        assertEquals(0.44f, castVolumeAfterNudge(0.50f, -0.02f), 0.001f)
        assertEquals(0.50f, castVolumeAfterNudge(0.50f, 0.001f), 0.001f)
        assertEquals(1f, castVolumeAfterNudge(0.98f, 0.2f), 0.001f)
        assertEquals(0f, castVolumeAfterNudge(0.02f, -0.2f), 0.001f)
        assertEquals(0.06f, CAST_VOLUME_STEP, 0.0001f)
    }

    @Test
    fun continuousGestureMapsFromStartLevel() {
        assertEquals(0.70f, castVolumeFromGesture(0.50f, 0.20f), 0.001f)
        assertEquals(0.30f, castVolumeFromGesture(0.50f, -0.20f), 0.001f)
        assertEquals(1f, castVolumeFromGesture(0.90f, 0.50f), 0.001f)
        assertEquals(0f, castVolumeFromGesture(0.10f, -0.50f), 0.001f)
        // Same finger delta twice must not stair-step like nudge does.
        assertEquals(
            castVolumeFromGesture(0.40f, 0.25f),
            castVolumeFromGesture(0.40f, 0.25f),
            0.001f,
        )
    }

    @Test
    fun staleHoldStillWritesWhenLiveLevelDiffers() {
        // Default hold 1f vs live session 0.94 — nudge to 1f must still write.
        assertTrue(castVolumeNeedsWrite(previous = 0.94f, next = 1f))
        assertFalse(castVolumeNeedsWrite(previous = 1f, next = 1f))
        assertFalse(castVolumeNeedsWrite(previous = 0.50f, next = 0.5002f))
    }

    @Test
    fun theLoadLeavesThePictureTrackOn() {
        assertTrue(castLoadActiveTrackIds(longArrayOf()) == null)
        assertTrue(castLoadActiveTrackIds(longArrayOf(1L)) == null)
        assertEquals(CAST_HLS_CONTENT_TYPE, "application/vnd.apple.mpegurl")
    }

    @Test
    fun lateLibraryCaptionsArePushedOnce() {
        val english = SubtitleTrack(url = "http://143.246.203.212/api/file?id=" + "a".repeat(64), label = "English")
        assertFalse(castShouldPushCaptions(casting = false, sentKey = "", tracks = listOf(english)))
        assertFalse(castShouldPushCaptions(casting = true, sentKey = "", tracks = emptyList()))
        assertTrue(castShouldPushCaptions(casting = true, sentKey = "", tracks = listOf(english)))
        assertFalse(castShouldPushCaptions(casting = true, sentKey = castCaptionKey(listOf(english)), tracks = listOf(english)))
    }

    @Test
    fun lateCaptionPushBudgetIsTwoSeconds() {
        assertEquals(2_000L, castCaptionPushBudgetMs())
        assertEquals(50L, CAST_CAPTION_PUSH_POLL_MS)
        assertEquals(40, CAST_CAPTION_PUSH_MAX_POLLS)
    }

    @Test
    fun librarySubripWithoutExtensionStillRelays() {
        val library = "http://143.246.203.212/api/file?id=" + "b".repeat(64)
        assertTrue(castRelaySubtitleEligible(library, "application/x-subrip"))
        assertTrue(castRelaySubtitleEligible("file:///data/captions/en.srt", "text/vtt"))
        assertFalse(castRelaySubtitleEligible("ftp://cdn/en.vtt", "text/vtt"))
        assertFalse(castRelaySubtitleEligible(library, "application/json"))
    }

    @Test
    fun corsStillAllowsSubtitleFetches() {
        // /sub responses reuse the same CORS lines as the playlist.
        val lines = castRelayCorsLines()
        assertTrue(lines.any { it.contains("Access-Control-Allow-Origin") })
        assertEquals(
            true,
            lines.any { it == "Access-Control-Allow-Private-Network: true" },
        )
    }
}
