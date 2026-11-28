// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGlassSubsTest {
    @Test
    fun audioModeChipsStayBinary() {
        assertEquals("sub", glassAudioModeSelectedId("sub"))
        assertEquals("sub", glassAudioModeSelectedId("SUB"))
        assertEquals("dub", glassAudioModeSelectedId("dub"))
        assertEquals("Sub", glassAudioModeChipLabel("Subtitled"))
        assertEquals("Dub", glassAudioModeChipLabel("Dubbed"))
        assertEquals("Audio", glassAudioModeChipLabel("   "))
    }

    @Test
    fun captionChipsTrackOffAutoAndLanguage() {
        assertEquals("off", glassSubsSelectedCaptionId(subsEnabled = false, selectedTextId = "en"))
        assertEquals("off", glassSubsSelectedCaptionId(subsEnabled = true, selectedTextId = "off"))
        assertEquals("auto", glassSubsSelectedCaptionId(subsEnabled = true, selectedTextId = "auto"))
        assertEquals("en", glassSubsSelectedCaptionId(subsEnabled = true, selectedTextId = "en"))
        assertEquals(
            listOf("off" to "Off", "auto" to "Auto", "en" to "English"),
            glassCaptionChipOptions(listOf("en" to "English")),
        )
        assertEquals(false to "off", glassCaptionSelectionAfterTap("off"))
        assertEquals(true to "auto", glassCaptionSelectionAfterTap("auto"))
        assertEquals(true to "ja", glassCaptionSelectionAfterTap("ja"))
        assertEquals("Off", glassCaptionChipLabel("off"))
        assertEquals("English, selected", glassSubsChipDescription("English", selected = true))
    }

    @Test
    fun captionsLoadingUsesGlassWarmChips() {
        assertEquals("Warming captions", glassCaptionsLoadingLabel())
        val chips = glassCaptionsLoadingChipOptions()
        assertEquals("cc-warm", chips.first().first)
        assertEquals("CC · WARM", chips.first().second)
        assertTrue(chips.size >= 3)
    }
}
