// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import site.anikitty.app.data.skip.SkipTimes

/**
 * Glass scrub chapter name for the active skip window, or null when the
 * finger is in the main episode body.
 */
internal fun scrubChapterLabel(skipTimes: SkipTimes, positionMs: Long): String? =
    when (skipTimes.activeAt(positionMs)?.first) {
        "intro" -> "Intro"
        "credits" -> "Credits"
        "recap" -> "Recap"
        "preview" -> "Preview"
        else -> null
    }
