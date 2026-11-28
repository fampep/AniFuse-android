// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * Bucket scrub positions so SeekPreview does not decode a frame on every
 * pixel of finger motion. One-second buckets match typical VTT cue cadence
 * without starving the glass preview.
 */
internal const val SCRUB_PREVIEW_BUCKET_MS = 1_000L

/** Crossfade between VTT plates; snap when reduced-motion is on. */
internal const val SCRUB_PREVIEW_CROSSFADE_MS = 140

internal fun scrubPreviewBucketMs(
    positionMs: Long,
    bucketMs: Long = SCRUB_PREVIEW_BUCKET_MS,
): Long {
    val step = bucketMs.coerceAtLeast(1L)
    return (positionMs.coerceAtLeast(0L) / step) * step
}

/**
 * Prefer the live VTT cue span when it is known so long cues do not
 * thrash decode and short cues stay responsive.
 *
 * Prefer [scrubPreviewCueKey] when the cue start is known — a global grid
 * of the span can snap into the previous cue near boundaries.
 */
internal fun scrubPreviewBucketForCues(
    positionMs: Long,
    cueSpanMs: Long?,
): Long {
    val step = (cueSpanMs ?: SCRUB_PREVIEW_BUCKET_MS).coerceIn(250L, 2_000L)
    return scrubPreviewBucketMs(positionMs, step)
}

/**
 * Stable SeekPreview key for the cue under the finger. Pins to [cueStartMs]
 * so every position inside the cue shares one plate and never snaps into the
 * previous cue's window (a 3s cue at 3000ms must not decode at 2000ms).
 * Falls back to 1s buckets when the VTT index is still cold.
 */
internal fun scrubPreviewCueKey(
    positionMs: Long,
    cueStartMs: Long?,
): Long {
    if (cueStartMs != null && cueStartMs >= 0L) return cueStartMs
    return scrubPreviewBucketMs(positionMs)
}

/** Stable crop LRU key for a sprite sheet cell. */
internal fun thumbnailCropCacheKey(
    spriteUrl: String,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    sample: Int,
): String = "$spriteUrl#$x,$y,$w,$h@$sample"

/** Keep the last decoded plate while the next bucket is still cold. */
internal fun <T> scrubPreviewRetainFrame(previous: T?, decoded: T?): T? = decoded ?: previous

/** Only wipe when the finger leaves the bar — never on a decode miss. */
internal fun scrubPreviewShouldClear(scrubbing: Boolean, scrubBucketMs: Long): Boolean =
    !scrubbing || scrubBucketMs < 0L

internal fun scrubPreviewCrossfadeMs(reducedMotion: Boolean): Int =
    if (reducedMotion) 0 else SCRUB_PREVIEW_CROSSFADE_MS

/**
 * Warm-around runs once when scrub starts — not keyed on every bucket —
 * so it does not race [ThumbnailLoader.frame] into duplicate sprite downloads.
 */
internal fun scrubPreviewWarmOnScrubStartOnly(
    scrubbing: Boolean,
    previousScrubbing: Boolean,
): Boolean = scrubbing && !previousScrubbing
