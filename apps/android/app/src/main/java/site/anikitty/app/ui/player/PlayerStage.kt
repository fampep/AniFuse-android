// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import android.content.pm.ActivityInfo

/** Television always opens the fullscreen theater. Phones keep the saved choice. */
internal fun playerOpensInTheater(television: Boolean, savedTheater: Boolean): Boolean =
    television || savedTheater

/**
 * The phone page is portrait. Television and theater stay landscape so a TV
 * does not get a portrait window with a small video card.
 */
internal fun playerRequestedOrientation(television: Boolean, theater: Boolean): Int = when {
    television || theater -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

/** Leaving theater on a television closes the player. On a phone it returns to the page. */
internal fun leavingTheaterClosesPlayer(television: Boolean): Boolean = television
