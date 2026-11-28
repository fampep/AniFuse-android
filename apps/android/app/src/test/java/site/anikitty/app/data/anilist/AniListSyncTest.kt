// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anilist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.local.WatchHistoryEntity

class AniListSyncTest {
    @Test
    fun pullKeepsALocalEpisodeThatIsAheadOfAniList() {
        val local = history(episode = 8, positionMs = 40_000L, durationMs = 1_400_000L)
        val merged = merge(local, remoteProgress = 5, pending = null)
        assertEquals(8, merged.episode)
        assertEquals(40_000L, merged.positionMs)
        assertEquals(1_400_000L, merged.durationMs)
        assertEquals("CURRENT", merged.listStatus)
    }

    @Test
    fun pullTakesProgressFromAnotherDeviceWhenThePhoneIsBehind() {
        val local = history(episode = 4, positionMs = 80_000L, durationMs = 1_400_000L)
        val merged = merge(local, remoteProgress = 10, pending = null)
        assertEquals(10, merged.episode)
        assertEquals(0L, merged.positionMs)
        assertEquals(0L, merged.durationMs)
    }

    @Test
    fun pullKeepsTheScrubOnTheSameEpisode() {
        val local = history(episode = 5, positionMs = 12_000L, durationMs = 1_400_000L)
        val merged = merge(local, remoteProgress = 5, pending = null)
        assertEquals(5, merged.episode)
        assertEquals(12_000L, merged.positionMs)
    }

    @Test
    fun aNewerFailureStaysQueuedWhenAnOlderPushSucceeds() {
        assertFalse(shouldDeletePendingPush(queuedUpdatedAtEpochMs = 200L, pushStartedAtEpochMs = 100L))
        assertTrue(shouldDeletePendingPush(queuedUpdatedAtEpochMs = 100L, pushStartedAtEpochMs = 200L))
        assertTrue(shouldDeletePendingPush(queuedUpdatedAtEpochMs = 100L, pushStartedAtEpochMs = 100L))
        // Mid-drain upsert of a newer episode must survive even if clocks race.
        assertFalse(
            shouldDeletePendingPush(
                queuedUpdatedAtEpochMs = 100L,
                pushStartedAtEpochMs = 200L,
                queuedEpisode = 6,
                sentEpisode = 5,
            ),
        )
        assertTrue(
            shouldDeletePendingPush(
                queuedUpdatedAtEpochMs = 100L,
                pushStartedAtEpochMs = 200L,
                queuedEpisode = 5,
                sentEpisode = 5,
            ),
        )
    }

    @Test
    fun offlineFirstMarkLandsOnlyAfterTheQueueClears() {
        assertTrue(pushProgressLanded(stillPending = false))
        assertFalse(pushProgressLanded(stillPending = true))
        assertEquals("SYNC · 3", aniListPendingPushPill(3))
        assertEquals(null, aniListPendingPushPill(0))
    }

    @Test
    fun aFinishedSeriesIsCompletedAndAPartialCountStaysCurrent() {
        assertEquals(AniListPushPlan(12, "COMPLETED"), aniListPushPlan(episode = 12, totalEpisodes = 12))
        assertEquals(AniListPushPlan(12, "COMPLETED"), aniListPushPlan(episode = 13, totalEpisodes = 12))
        assertEquals(AniListPushPlan(4, "CURRENT"), aniListPushPlan(episode = 4, totalEpisodes = 12))
        assertEquals(AniListPushPlan(0, "CURRENT"), aniListPushPlan(episode = 0, totalEpisodes = 12))
        assertEquals(AniListPushPlan(3, "CURRENT"), aniListPushPlan(episode = 3, totalEpisodes = 0))
        assertEquals(AniListPushPlan(3, "CURRENT"), aniListPushPlan(episode = 3, totalEpisodes = null))
    }

    @Test
    fun aSuccessfulPushDoesNotRewindALaterFrontierOrALivePlayhead() {
        val later = history(episode = 8, positionMs = 0L, durationMs = 0L)
        val kept = historyAfterAniListPush(later, progress = 5, status = "CURRENT")
        assertEquals(8, kept.episode)
        assertEquals(0L, kept.positionMs)
        assertEquals("CURRENT", kept.listStatus)

        val live = history(episode = 4, positionMs = 2_001L, durationMs = 1_400_000L)
        val stillThere = historyAfterAniListPush(live, progress = 5, status = "COMPLETED")
        assertEquals(4, stillThere.episode)
        assertEquals(2_001L, stillThere.positionMs)
        assertEquals("COMPLETED", stillThere.listStatus)

        val caughtUp = history(episode = 4, positionMs = 2_000L, durationMs = 1_400_000L)
        val moved = historyAfterAniListPush(caughtUp, progress = 5, status = "CURRENT")
        assertEquals(5, moved.episode)
        assertEquals(2_000L, moved.positionMs)
    }

    @Test
    fun aPendingPushThatAlreadyMatchesAniListDoesNotFreezeAnOlderPhone() {
        val local = history(episode = 3, positionMs = 90_000L, durationMs = 1_400_000L)
        val merged = merge(local, remoteProgress = 6, pending = 6)
        assertEquals(6, merged.episode)
        assertEquals(0L, merged.positionMs)
    }

    @Test
    fun aFailedPushBlocksAStaleCloudCopyFromUndoingAnUnwatch() {
        val local = history(episode = 3, positionMs = 0L, durationMs = 0L, status = "CURRENT")
        val merged = merge(local, remoteProgress = 8, pending = 2)
        assertEquals(3, merged.episode)
        assertEquals("CURRENT", merged.listStatus)
    }

    @Test
    fun warmPullSkipsViewerWhenUserIdIsCached() {
        val warm = aniListPullIdentity(
            cachedUserId = 42,
            cachedUsername = "fampep",
            viewer = null,
        )
        assertEquals(42, warm!!.userId)
        assertEquals("fampep", warm.username)
        assertEquals(
            null,
            aniListPullIdentity(cachedUserId = 0, cachedUsername = null, viewer = null),
        )
    }

    @Test
    fun mediaListCacheKeyIsStablePerUser() {
        assertEquals("mediaList:v1:42", mediaListCacheKey(42))
        assertEquals("mediaList:v1:7", mediaListCacheKey(7))
    }

    @Test
    fun postDrainMediaListAlwaysBypassesTheWarmTtl() {
        assertTrue(mediaListForceRefreshAfterDrain(interactiveForceRefresh = false, drainedAccepted = 1))
        assertTrue(mediaListForceRefreshAfterDrain(interactiveForceRefresh = true, drainedAccepted = 0))
        assertTrue(mediaListForceRefreshAfterDrain(interactiveForceRefresh = true, drainedAccepted = 3))
        assertFalse(mediaListForceRefreshAfterDrain(interactiveForceRefresh = false, drainedAccepted = 0))
        assertFalse(aniListMediaListUsesBackgroundPace(interactive = true))
        assertTrue(aniListMediaListUsesBackgroundPace(interactive = false))
    }

    @Test
    fun cloudFrontierAdvanceDropsStaleWarm() {
        assertTrue(shouldDropWarmOnFrontierAdvance(previousEpisode = 4, nextEpisode = 7))
        assertFalse(shouldDropWarmOnFrontierAdvance(previousEpisode = 7, nextEpisode = 7))
        assertFalse(shouldDropWarmOnFrontierAdvance(previousEpisode = 8, nextEpisode = 7))
        assertFalse(shouldDropWarmOnFrontierAdvance(previousEpisode = null, nextEpisode = 3))
        assertFalse(shouldDropWarmOnFrontierAdvance(previousEpisode = 0, nextEpisode = 2))
    }

    @Test
    fun frontierAdvanceIdsCoalesceWarmDrops() {
        val previous = mapOf(
            1 to history(episode = 4, positionMs = 0L, durationMs = 0L).copy(anilistId = 1),
            2 to history(episode = 9, positionMs = 0L, durationMs = 0L).copy(anilistId = 2),
            3 to history(episode = 1, positionMs = 0L, durationMs = 0L).copy(anilistId = 3),
        )
        val merged = listOf(
            history(episode = 7, positionMs = 0L, durationMs = 0L).copy(anilistId = 1),
            history(episode = 9, positionMs = 0L, durationMs = 0L).copy(anilistId = 2),
            history(episode = 2, positionMs = 0L, durationMs = 0L).copy(anilistId = 3),
        )
        assertEquals(listOf(1, 3), frontierAdvanceIds(merged, previous))
    }

    @Test
    fun syncResultLabelSurfacesDrainAndFrontier() {
        assertEquals(
            "Synced 12 from AniList",
            aniListSyncResultLabel(AniListSync.Result(imported = 12, username = "x")),
        )
        assertEquals(
            "Synced 12 · pushed 3 · 2 fresher from AniList",
            aniListSyncResultLabel(
                AniListSync.Result(imported = 12, username = "x", pushed = 3, frontierDrops = 2),
            ),
        )
    }

    @Test
    fun drainFlushKeepsLivePlayheadFromFreshRoom() {
        // Stale snapshot at accept time had no scrub; a playhead landed while
        // GraphQL retries ran. Flush must re-merge against the fresh row.
        val live = history(episode = 5, positionMs = 88_000L, durationMs = 1_400_000L)
        val plans = listOf(
            AniListDrainHistoryPlan(anilistId = 7, progress = 5, status = "CURRENT"),
            AniListDrainHistoryPlan(anilistId = 7, progress = 5, status = "CURRENT"),
        )
        val collapsed = drainHistoryPlansById(plans).values.toList()
        assertEquals(1, collapsed.size)
        val writes = drainHistoryWritesFromFresh(collapsed, mapOf(7 to live))
        assertEquals(1, writes.size)
        assertEquals(5, writes[0].episode)
        assertEquals(88_000L, writes[0].positionMs)
        assertEquals("CURRENT", writes[0].listStatus)
    }

    private fun merge(local: WatchHistoryEntity?, remoteProgress: Int, pending: Int?) = mergedPullEntry(
        existing = local,
        anilistId = 7,
        title = "Serial",
        coverImageUrl = "https://cdn.example/cover.jpg",
        malId = 42,
        remoteProgress = remoteProgress,
        remoteStatus = "CURRENT",
        pendingProgress = pending,
        syncedAtEpochMs = 50L,
    )

    private fun history(
        episode: Int,
        positionMs: Long,
        durationMs: Long,
        status: String? = "CURRENT",
    ) = WatchHistoryEntity(
        anilistId = 7,
        title = "Serial",
        coverImageUrl = null,
        episode = episode,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAtEpochMs = 10L,
        listStatus = status,
        malId = 42,
    )
}
