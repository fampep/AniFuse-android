// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerAudioTest {
    @Test
    fun labelUsesLanguageAndChannels() {
        assertEquals("JA · Stereo", audioTrackLabel("ja", null, 2, 1))
        assertEquals("Commentary · EN · Stereo", audioTrackLabel("en", "Commentary", 2, 1))
        assertEquals("EN · Surround", audioTrackLabel("en", null, 6, 2))
    }

    @Test
    fun labelFallsBackToTrackNumber() {
        assertEquals("Track 3", audioTrackLabel(null, "  ", 0, 3))
        assertEquals("Track 1", audioTrackLabel("und", null, 0, 1))
    }

    @Test
    fun rematchKeepsAKnownTrackAndDropsAMissingOne() {
        val options = listOf(
            AudioOption(id = "lang:ja|ja · stereo", label = "JA · Stereo"),
        )
        assertEquals("auto", rematchAudioId("auto", options))
        assertEquals(options[0].id, rematchAudioId(options[0].id, options))
        assertEquals("auto", rematchAudioId("lang:en|en", options))
    }
}
