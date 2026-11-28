// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * Bucket scrub positions so SeekPreview does not decode a frame on every
 * pixel of finger motion. One-second buckets match typical VTT cue cadence
 * without starving the glass preview.
 */
internal const val SCRUB_PREVIEW_BUCKET_MS = 1_000L

internal fun scrubPreviewBucketMs(
    positionMs: Long,
    bucketMs: Long = SCRUB_PREVIEW_BUCKET_MS,
): Long {
    val step = bucketMs.coerceAtLeast(1L)
    return (positionMs.coerceAtLeast(0L) / step) * step
}
