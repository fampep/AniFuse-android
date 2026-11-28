// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * Plex / YouTube-style edge zones for brightness (left) and volume (right).
 * Center stays free for scrub + double-tap seek.
 */
internal const val PLAYER_EDGE_ZONE_FRACTION = 0.18f

internal enum class PlayerEdgeZone {
    Brightness,
    Volume,
    Center,
}

internal fun playerEdgeZone(x: Float, width: Int): PlayerEdgeZone {
    val w = width.coerceAtLeast(1).toFloat()
    val edge = w * PLAYER_EDGE_ZONE_FRACTION
    return when {
        x < edge -> PlayerEdgeZone.Brightness
        x > w - edge -> PlayerEdgeZone.Volume
        else -> PlayerEdgeZone.Center
    }
}

/** Horizontal scrub fraction while dragging across the theater surface. */
internal fun gestureScrubFraction(
    originMs: Long,
    deltaX: Float,
    width: Int,
    durationMs: Long,
): Float {
    val span = durationMs.coerceAtLeast(1L).toFloat()
    val deltaMs = (deltaX / width.coerceAtLeast(1).toFloat()) * span
    val target = (originMs + deltaMs.toLong()).coerceIn(0L, durationMs.coerceAtLeast(0L))
    return (target.toFloat() / span).coerceIn(0f, 1f)
}
