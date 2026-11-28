// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.compose.ui.graphics.Color
import site.anikitty.app.data.skip.SkipTimes

/**
 * Glass scrub chapter name for the active skip window, or null when the
 * finger is in the main episode body.
 */
internal fun scrubChapterLabel(skipTimes: SkipTimes, positionMs: Long): String? =
    when (scrubChapterKind(skipTimes, positionMs)) {
        "intro" -> "Intro"
        "credits" -> "Credits"
        "recap" -> "Recap"
        "preview" -> "Preview"
        else -> null
    }

/** Skip-window id for glass chrome accents — null in the episode body. */
internal fun scrubChapterKind(skipTimes: SkipTimes, positionMs: Long): String? =
    skipTimes.activeAt(positionMs)?.first

/**
 * Chapter chips get a brighter glass rim than the time stamp so Intro /
 * Credits read as section headers while scrubbing.
 */
internal fun seekChapterGlassBorder(kind: String?): Color = when (kind) {
    "intro" -> Color.White.copy(alpha = 0.58f)
    "credits" -> Color.White.copy(alpha = 0.50f)
    "recap" -> Color.White.copy(alpha = 0.42f)
    "preview" -> Color.White.copy(alpha = 0.48f)
    else -> Color.White.copy(alpha = 0.34f)
}

/** Top-edge glass sheen for the chapter chip — stronger on intro/credits. */
internal fun seekChapterGlassSheen(kind: String?): Float = when (kind) {
    "intro", "credits" -> 0.22f
    "recap", "preview" -> 0.16f
    else -> 0.12f
}
