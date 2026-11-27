// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionPolicyTest {
    @Test
    fun movieAudioAsksForMediaFocus() {
        val audio = movieAudioAttributes()
        assertEquals(C.USAGE_MEDIA, audio.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MOVIE, audio.contentType)
    }

    @Test
    fun foregroundStaysUpOnlyWhileThePhoneIsPlaying() {
        assertTrue(
            shouldHoldPlaybackForeground(
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_READY,
                suppressed = false,
            ),
        )
        assertTrue(
            shouldHoldPlaybackForeground(
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_BUFFERING,
                suppressed = false,
            ),
        )
        assertFalse(
            shouldHoldPlaybackForeground(
                playWhenReady = false,
                playbackState = PLAYBACK_STATE_READY,
                suppressed = false,
            ),
        )
        assertFalse(
            shouldHoldPlaybackForeground(
                playWhenReady = true,
                playbackState = PLAYBACK_STATE_READY,
                suppressed = true,
            ),
        )
        assertFalse(
            shouldHoldPlaybackForeground(
                playWhenReady = true,
                playbackState = 1,
                suppressed = false,
            ),
        )
    }

    @Test
    fun onlySystemAndThisAppMayBindTheSession() {
        assertTrue(sessionTrusted("site.anikitty.app", "site.anikitty.app", notificationController = false))
        assertTrue(sessionTrusted("com.example.other", "site.anikitty.app", notificationController = true))
        assertTrue(sessionTrusted("com.android.systemui", "site.anikitty.app", notificationController = false))
        assertTrue(sessionTrusted("com.android.bluetooth", "site.anikitty.app", notificationController = false))
        assertFalse(sessionTrusted("com.example.other", "site.anikitty.app", notificationController = false))
    }
}
