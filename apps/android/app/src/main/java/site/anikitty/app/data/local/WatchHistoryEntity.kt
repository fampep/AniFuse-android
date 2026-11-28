// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/** On-device watch history — optionally mirrored from / to AniList. */
@Immutable
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val anilistId: Int,
    val title: String,
    val coverImageUrl: String?,
    val episode: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
    /** AniList MediaListStatus when synced (CURRENT, COMPLETED, …). */
    val listStatus: String? = null,
    val malId: Int? = null,
) {
    /**
     * Episodes actually finished. Opening a title from the catalog seeds episode 1
     * with no playback and no list status; that is not progress. Mid-watch on
     * episode E counts E−1. At rest the field is AniList progress (or the local
     * next-frontier after mark — both share this number).
     */
    fun episodesFinished(): Int {
        val openedOnly = listStatus.isNullOrBlank() &&
            positionMs == 0L &&
            durationMs == 0L &&
            episode <= 1
        if (openedOnly) return 0
        // Live scrub on this episode — the current one is not finished yet.
        if (positionMs > 2_000L) return (episode - 1).coerceAtLeast(0)
        return episode.coerceAtLeast(0)
    }
}
