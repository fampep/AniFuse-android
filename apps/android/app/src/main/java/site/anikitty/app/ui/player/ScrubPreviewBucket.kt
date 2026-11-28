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

/** Keep the last decoded plate while the next bucket is still cold. */
internal fun <T> scrubPreviewRetainFrame(previous: T?, decoded: T?): T? = decoded ?: previous

/** Only wipe when the finger leaves the bar — never on a decode miss. */
internal fun scrubPreviewShouldClear(scrubbing: Boolean, scrubBucketMs: Long): Boolean =
    !scrubbing || scrubBucketMs < 0L

internal fun scrubPreviewCrossfadeMs(reducedMotion: Boolean): Int =
    if (reducedMotion) 0 else SCRUB_PREVIEW_CROSSFADE_MS
