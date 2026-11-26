// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * What to do after the current URL fails.
 * [nextIndex] is null when every source has already been tried.
 * [resumeMs] is the playhead the next item must open at.
 */
internal data class SourceFailover(
    val nextIndex: Int?,
    val resumeMs: Long,
)

internal fun sourceFailover(
    index: Int,
    count: Int,
    livePositionMs: Long,
    heldResumeMs: Long,
    historyMs: Long,
    allowHistory: Boolean,
): SourceFailover {
    val live = livePositionMs.coerceAtLeast(0L)
    val held = heldResumeMs.coerceAtLeast(0L)
    val history = historyMs.coerceAtLeast(0L)
    val resume = when {
        live > 2_000L -> live
        held > 2_000L -> held
        allowHistory && history > 2_000L -> history
        else -> maxOf(live, held)
    }
    val next = (index + 1).takeIf { count > 0 && it in 0 until count }
    return SourceFailover(next, resume)
}

/** True when a fresh resolve returned URLs this attempt has not already played. */
internal fun hasNewPlayableUrls(current: Collection<String>, next: Collection<String>): Boolean {
    if (next.isEmpty()) return false
    return next.toSet() != current.toSet()
}
