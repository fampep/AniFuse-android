// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.cast

import site.anikitty.app.data.local.WatchHistoryEntity
import site.anikitty.app.data.local.shouldMarkWatched
import site.anikitty.app.data.skip.SkipInterval
import site.anikitty.app.data.skip.SkipTimes

/** What the cast service keeps writing after the player screen is gone. */
internal data class CastProgressTarget(
    val anilistId: Int,
    val title: String,
    val episode: Int,
    val totalEpisodes: Int,
    val outroStartMs: Long?,
)

/**
 * Playhead to store while Chromecast keeps going.
 * A later episode already on the row is left alone. Reaching the credits, or
 * the last 90 seconds, stores the following episode so resume does not replay
 * the one that just finished.
 * Returns null when there is nothing new to write.
 */
internal fun castHistoryUpdate(
    existing: WatchHistoryEntity?,
    target: CastProgressTarget,
    positionMs: Long,
    durationMs: Long,
    now: Long,
): WatchHistoryEntity? {
    if (target.anilistId <= 0 || target.episode <= 0 || positionMs <= 0L) return null
    if (existing != null && existing.episode > target.episode) return null
    val base = existing ?: WatchHistoryEntity(
        anilistId = target.anilistId,
        title = target.title,
        coverImageUrl = null,
        episode = target.episode,
        positionMs = 0L,
        durationMs = 0L,
        updatedAtEpochMs = now,
        listStatus = "CURRENT",
    )
    val outro = target.outroStartMs?.takeIf { it > 0L }?.let { start ->
        SkipInterval(start / 1000.0, start / 1000.0)
    }
    val finished = shouldMarkWatched(positionMs, durationMs, SkipTimes(outro = outro))
    val title = target.title.ifBlank { base.title }
    return if (finished) {
        base.copy(
            title = title,
            episode = target.episode + 1,
            positionMs = 0L,
            durationMs = 0L,
            updatedAtEpochMs = now,
            listStatus = base.listStatus ?: "CURRENT",
        )
    } else {
        base.copy(
            title = title,
            episode = target.episode,
            positionMs = positionMs,
            durationMs = maxOf(durationMs, 0L),
            updatedAtEpochMs = now,
            listStatus = base.listStatus ?: "CURRENT",
        )
    }
}

/**
 * Progress the cast service should keep after the screen reports a target.
 * Prefer a screen target that matches the TV item. When the screen navigates
 * ahead (or behind) mid-cast, keep writing the item the receiver still plays
 * instead of clearing — otherwise Continue / AniList freeze until settle.
 */
internal data class CastProgressCommit(
    val target: CastProgressTarget?,
    val reportedThrough: Int,
)

internal fun commitCastProgress(
    screen: CastProgressTarget?,
    playing: CastItemKey?,
    current: CastProgressTarget?,
    reportedThrough: Int,
): CastProgressCommit {
    if (playing == null) {
        return CastProgressCommit(target = null, reportedThrough = -1)
    }
    if (screen != null && castProgressMatches(screen, playing)) {
        val sameEpisode = current != null &&
            current.anilistId == screen.anilistId &&
            current.episode == screen.episode
        return CastProgressCommit(
            target = screen,
            reportedThrough = if (sameEpisode) reportedThrough else -1,
        )
    }
    // Screen moved on — keep the TV row alive through the handoff gap.
    if (current != null && castProgressMatches(current, playing)) {
        return CastProgressCommit(target = current, reportedThrough = reportedThrough)
    }
    return CastProgressCommit(target = null, reportedThrough = -1)
}

/**
 * While Chromecast owns the episode, progress always comes from the receiver
 * clock — even with the player screen open. Dual writers used to stamp the
 * navigation episode with a stale phone playhead during handoff.
 */
internal fun castServiceOwnsProgress(
    casting: Boolean,
    loadInFlight: Boolean,
): Boolean = casting && !loadInFlight

internal fun castProgressMatches(target: CastProgressTarget, item: CastItemKey): Boolean {
    if (target.anilistId <= 0 || item.anilistId <= 0) return false
    return target.anilistId == item.anilistId && target.episode == item.episode
}
internal fun castProgressToReport(before: WatchHistoryEntity?, written: WatchHistoryEntity, watchedEpisode: Int): Int? {
    if (written.episode <= watchedEpisode) return null
    if (before != null && before.episode > watchedEpisode) return null
    return watchedEpisode
}
