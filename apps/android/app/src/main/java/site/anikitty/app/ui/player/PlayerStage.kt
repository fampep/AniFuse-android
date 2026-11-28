// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import android.content.pm.ActivityInfo

/**
 * Phone and television both open the fullscreen AniFuse chrome.
 * The old portrait page shell is gone — [savedTheater] is ignored.
 */
internal fun playerOpensInTheater(television: Boolean, savedTheater: Boolean): Boolean = true

/**
 * Fullscreen chrome stays landscape so a phone never falls back to the
 * old portrait page card.
 */
internal fun playerRequestedOrientation(television: Boolean, theater: Boolean): Int =
    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

/** Back from fullscreen chrome always leaves the player. */
internal fun leavingTheaterClosesPlayer(television: Boolean): Boolean = true
