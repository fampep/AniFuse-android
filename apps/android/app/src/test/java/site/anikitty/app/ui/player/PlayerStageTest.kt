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
    fun phoneAndTelevisionAlwaysOpenAniFuseChrome() {
        assertTrue(playerOpensInTheater(television = true, savedTheater = false))
        assertTrue(playerOpensInTheater(television = true, savedTheater = true))
        // Old page shell is gone — ignore the saved start_theater preference.
        assertTrue(playerOpensInTheater(television = false, savedTheater = false))
        assertTrue(playerOpensInTheater(television = false, savedTheater = true))
    }

    @Test
    fun chromeStaysLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            playerRequestedOrientation(television = true, theater = true),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            playerRequestedOrientation(television = false, theater = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            playerRequestedOrientation(television = false, theater = true),
        )
    }

    @Test
    fun leavingChromeAlwaysClosesThePlayer() {
        assertTrue(leavingTheaterClosesPlayer(television = true))
        assertTrue(leavingTheaterClosesPlayer(television = false))
    }

    @Test
    fun menuGateClaimsASingleTarget() {
        val gate = MenuFocusGate()
        assertTrue(gate.claim())
        assertFalse(gate.claim())
    }
}
