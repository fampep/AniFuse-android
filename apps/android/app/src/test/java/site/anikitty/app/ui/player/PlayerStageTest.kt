// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStageTest {
    @Test
    fun televisionAlwaysOpensTheater() {
        assertTrue(playerOpensInTheater(television = true, savedTheater = false))
        assertTrue(playerOpensInTheater(television = true, savedTheater = true))
    }

    @Test
    fun phoneKeepsTheSavedChoice() {
        assertFalse(playerOpensInTheater(television = false, savedTheater = false))
        assertTrue(playerOpensInTheater(television = false, savedTheater = true))
    }

    @Test
    fun televisionStaysLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            playerRequestedOrientation(television = true, theater = true),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            playerRequestedOrientation(television = true, theater = false),
        )
    }

    @Test
    fun phonePageStaysPortrait() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            playerRequestedOrientation(television = false, theater = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            playerRequestedOrientation(television = false, theater = true),
        )
    }

    @Test
    fun leavingTheaterOnTelevisionClosesThePlayer() {
        assertTrue(leavingTheaterClosesPlayer(television = true))
        assertFalse(leavingTheaterClosesPlayer(television = false))
    }

    @Test
    fun menuGateClaimsASingleTarget() {
        val gate = MenuFocusGate()
        assertTrue(gate.claim())
        assertFalse(gate.claim())
    }
}
