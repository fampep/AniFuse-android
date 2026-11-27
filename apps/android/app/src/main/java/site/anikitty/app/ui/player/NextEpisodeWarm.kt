// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * When the playhead crosses this fraction of the runtime, the next episode's
 * resolver race should already be in flight so autoplay never waits on a cold CDN.
 * 65% leaves ~8 minutes on a typical 24m episode for resolve + caption prefetch.
 */
internal const val NEXT_EPISODE_WARM_FRACTION = 0.65f

internal fun shouldWarmNextEpisode(
    positionMs: Long,
    durationMs: Long,
    hasNext: Boolean,
    alreadyWarmed: Boolean,
): Boolean {
    if (!hasNext || alreadyWarmed) return false
    if (durationMs < 60_000L) return false
    if (positionMs < 0L || durationMs <= 0L) return false
    return positionMs.toFloat() / durationMs.toFloat() >= NEXT_EPISODE_WARM_FRACTION
}
