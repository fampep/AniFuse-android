// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * When the playhead crosses this fraction of the runtime, the next episode's
 * resolver race should already be in flight so autoplay never waits on a cold CDN.
 * 42% leaves ~14 minutes on a typical 24m episode for resolve + caption prefetch.
 */
internal const val NEXT_EPISODE_WARM_FRACTION = 0.42f

/**
 * Caption listing alone — fire before the CDN race so autoplay opens with
 * dialogue already in SubtitleLibrary memory/disk, not waiting on resolve.
 */
internal const val NEXT_CAPTION_WARM_FRACTION = 0.25f

/** Credits window: re-check the next file and arm a standby CDN for this one. */
internal const val CREDITS_STANDBY_FRACTION = 0.70f

internal enum class StreamWarmAction { None, PrefetchCaptions, ResolveNext, ArmCredits }

/**
 * Caption prefetch for the next episode may start as soon as resolve warms —
 * even when the CDN list is empty, dialogue still needs to be hot for autoplay.
 */
internal fun shouldWarmNextCaptions(
    hasNext: Boolean,
    nextWarmed: Boolean,
): Boolean = hasNext && !nextWarmed

/**
 * 25% pulls the next caption listing. 42% starts the CDN race. 70% revalidates
 * it and arms a different host for the file already playing. Short clips never warm.
 */
internal fun streamWarmAction(
    positionMs: Long,
    durationMs: Long,
    hasNext: Boolean,
    nextWarmed: Boolean,
    creditsArmed: Boolean,
    captionsWarmed: Boolean = nextWarmed,
): StreamWarmAction {
    if (durationMs < 60_000L || positionMs < 0L || durationMs <= 0L) return StreamWarmAction.None
    val fraction = positionMs.toFloat() / durationMs.toFloat()
    if (!creditsArmed && fraction >= CREDITS_STANDBY_FRACTION) return StreamWarmAction.ArmCredits
    if (shouldWarmNextCaptions(hasNext, nextWarmed) && fraction >= NEXT_EPISODE_WARM_FRACTION) {
        return StreamWarmAction.ResolveNext
    }
    if (shouldWarmNextCaptions(hasNext, captionsWarmed) && fraction >= NEXT_CAPTION_WARM_FRACTION) {
        return StreamWarmAction.PrefetchCaptions
    }
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
