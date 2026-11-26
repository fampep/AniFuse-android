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

/**
 * What a playback error is allowed to do.
 * A dead CDN moves on. A decoder glitch after the file was already playing does not.
 */
internal enum class SourceRecovery {
    NextSource,
    RetrySame,
    SeekLive,
    GiveUp,
}

internal fun httpStatusOf(error: Throwable?): Int? {
    var current = error
    var guard = 0
    while (current != null && guard < 8) {
        val message = current.message.orEmpty()
        Regex("""(?:Response code|HTTP(?: status)?)[: ]+(\d{3})""").find(message)?.let {
            return it.groupValues[1].toInt()
        }
        current = current.cause
        guard++
    }
    return null
}

internal fun sourceRecovery(errorCode: Int, httpStatus: Int?, playedMs: Long): SourceRecovery {
    if (httpStatus != null) {
        return if (httpStatus == 408 || httpStatus == 429 || httpStatus in 500..599 || httpStatus in 400..499) {
            SourceRecovery.NextSource
        } else {
            SourceRecovery.GiveUp
        }
    }
    val played = playedMs.coerceAtLeast(0L)
    // Numbers are androidx.media3.common.PlaybackException error codes.
    return when (errorCode) {
        1002 -> SourceRecovery.SeekLive
        2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007 -> SourceRecovery.NextSource
        3001, 3002, 3003, 3004 -> SourceRecovery.NextSource
        4003, 4006, 5002, 5003 -> if (played > 10_000L) SourceRecovery.RetrySame else SourceRecovery.NextSource
        4001, 4002, 4004, 4005, 5001, 5004 -> if (played < 2_000L) SourceRecovery.NextSource else SourceRecovery.GiveUp
        else -> SourceRecovery.GiveUp
    }
}
