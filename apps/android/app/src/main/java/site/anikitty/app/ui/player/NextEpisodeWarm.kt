// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * When the playhead crosses this fraction of the runtime, the next episode's
 * resolver race should already be in flight so autoplay never waits on a cold CDN.
 * 65% leaves ~8 minutes on a typical 24m episode for resolve + caption prefetch.
 */
internal const val NEXT_EPISODE_WARM_FRACTION = 0.65f

/** Credits window: re-check the next file and arm a standby CDN for this one. */
internal const val CREDITS_STANDBY_FRACTION = 0.82f

internal enum class StreamWarmAction { None, ResolveNext, ArmCredits }

/**
 * 65% starts the next episode's resolve. 82% revalidates it and arms a
 * different host for the file already playing, so a credits-time 403 is not
 * a cold race. A short clip never warms.
 */
internal fun streamWarmAction(
    positionMs: Long,
    durationMs: Long,
    hasNext: Boolean,
    nextWarmed: Boolean,
    creditsArmed: Boolean,
): StreamWarmAction {
    if (durationMs < 60_000L || positionMs < 0L || durationMs <= 0L) return StreamWarmAction.None
    val fraction = positionMs.toFloat() / durationMs.toFloat()
    if (!creditsArmed && fraction >= CREDITS_STANDBY_FRACTION) return StreamWarmAction.ArmCredits
    if (hasNext && !nextWarmed && fraction >= NEXT_EPISODE_WARM_FRACTION) return StreamWarmAction.ResolveNext
    return StreamWarmAction.None
}

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
