// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchHistoryEpisodesFinishedTest {
    @Test
    fun catalogSeedIsZeroAndAniListProgressCounts() {
        assertEquals(
            0,
            row(episode = 1, positionMs = 0L, durationMs = 0L, listStatus = null).episodesFinished(),
        )
        assertEquals(
            5,
            row(episode = 5, positionMs = 0L, durationMs = 0L, listStatus = "CURRENT").episodesFinished(),
        )
    }

    @Test
    fun midWatchDoesNotCountTheEpisodeOnScreen() {
        assertEquals(
            4,
            row(episode = 5, positionMs = 12_000L, durationMs = 1_400_000L, listStatus = "CURRENT")
                .episodesFinished(),
        )
        assertEquals(
            0,
            row(episode = 1, positionMs = 3_000L, durationMs = 1_400_000L, listStatus = "CURRENT")
                .episodesFinished(),
        )
        // Barely started scrub still counts as mid-watch once past 2s.
        assertEquals(
            2,
            row(episode = 3, positionMs = 2_001L, durationMs = 1_400_000L, listStatus = "CURRENT")
                .episodesFinished(),
        )
    }

    private fun row(
        episode: Int,
        positionMs: Long,
        durationMs: Long,
        listStatus: String?,
    ) = WatchHistoryEntity(
        anilistId = 7,
        title = "Serial",
        coverImageUrl = null,
        episode = episode,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtEpochMs = 1L,
        listStatus = listStatus,
    )
}
