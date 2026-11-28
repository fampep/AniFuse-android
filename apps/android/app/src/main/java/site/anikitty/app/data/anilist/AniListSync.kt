// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anilist

import site.anikitty.app.data.local.PendingAniListPush
import site.anikitty.app.data.local.PendingAniListPushDao
import site.anikitty.app.data.local.WatchHistoryDao
import site.anikitty.app.data.local.WatchHistoryEntity
import site.anikitty.app.data.servers.PlayableWarmCache

/**
 * Pulls AniList MediaList into on-device history and pushes local episode progress back.
 */
class AniListSync(
    private val aniList: AniListClient,
    private val auth: AniListAuthStore,
    private val historyDao: WatchHistoryDao,
    private val pendingPushes: PendingAniListPushDao,
    /** Disk live-source recall for a title whose Continue frontier jumped. */
    private val onTitleFrontierAdvance: (anilistId: Int) -> Unit = {},
) {
    data class Result(
        val imported: Int,
        val username: String?,
        /** Pending marks AniList accepted during the pre-import drain. */
        val pushed: Int = 0,
        /** Titles whose Continue episode jumped — warm/disk recalls were cleared. */
        val frontierDrops: Int = 0,
    )

    /**
     * @param forceRefresh interactive Sync must bypass the 2-minute MediaList
     * cache so another device's progress lands immediately. Periodic background
     * pulls keep the warm body.
     * @param onPhase glass Sync subtitle — Pushing marks, then Importing list.
     */
    suspend fun pull(
        forceRefresh: Boolean = false,
        onPhase: (AniListSyncPhase) -> Unit = {},
    ): Result {
        val token = auth.accessToken ?: error("AniList not linked")
        // Drain before import so AniList reflects our queue and pending rows
        // still protect merge if a push failed. Never race drain with the list.
        // Interactive Sync paces drain as foreground so it is not stuck behind
        // the background GraphQL pacer.
        onPhase(AniListSyncPhase.Pushing)
        val drained = drainPendingPushes(interactive = forceRefresh)
        // Warm installs already know the user — skip Viewer so MediaList is the
        // only paced GraphQL hop on the critical sync path.
        onPhase(AniListSyncPhase.Importing)
        val identity = if (auth.userId > 0) {
            AniListPullIdentity(auth.userId, auth.username)
        } else {
            val viewer = aniList.viewer(token) ?: error("Could not load AniList profile")
            auth.saveViewer(token, viewer)
            AniListPullIdentity(viewer.id, viewer.name)
        }

        // Accepted pushes already invalidate mediaList:, but a racing put or a
        // background pull that drained mid-TTL must still hit the network so
        // the import cannot re-apply a pre-push MediaList body.
        val entries = aniList.mediaListEntries(
            accessToken = token,
            userId = identity.userId,
            forceRefresh = mediaListForceRefreshAfterDrain(
                interactiveForceRefresh = forceRefresh,
                drainedAccepted = drained,
            ),
            // Periodic pulls yield to Home shelf; Sync stays interactive.
            interactive = forceRefresh,
        )
        val now = System.currentTimeMillis()
        val entryIds = entries.map { it.media.id }.distinct()
        val existingById = if (entryIds.isEmpty()) {
            emptyMap()
        } else {
            historyDao.getByIds(entryIds).associateBy { it.anilistId }
        }
        val pendingById = pendingPushes.all().associateBy { it.anilistId }
        val merged = entries.mapIndexed { index, entry ->
            val media = entry.media
            mergedPullEntry(
                existing = existingById[media.id],
                anilistId = media.id,
                title = media.title,
                coverImageUrl = media.coverImageUrl,
                malId = media.malId,
                remoteProgress = entry.progress,
                remoteStatus = entry.status,
                pendingProgress = pendingById[media.id]?.episode,
                syncedAtEpochMs = now - index,
            )
        }
        if (merged.isNotEmpty()) historyDao.upsertAll(merged)
        // Another device moved the Continue frontier — kill warm/disk recalls for
        // the old episode so Home open races the new one instead of a rotten URL.
        val advanced = frontierAdvanceIds(merged, existingById)
        for (anilistId in advanced) {
            PlayableWarmCache.dropTitle(anilistId)
            onTitleFrontierAdvance(anilistId)
        }
        return Result(
            imported = merged.size,
            username = identity.username,
            pushed = drained,
            frontierDrops = advanced.size,
        )
    }

    /**
     * Send queued progress in multi-alias batches so one paced HTTP call covers
     * up to [ANILIST_PUSH_CHUNK] rows. A newer failure that lands while one
     * attempt is in flight stays queued. Returns how many rows AniList accepted.
     */
    suspend fun drainPendingPushes(interactive: Boolean = false): Int {
        val token = auth.accessToken ?: return 0
        if (token.isBlank()) return 0
        val rows = pendingPushesForDrain(pendingPushes.all()) { it.updatedAtEpochMs }
        if (rows.isEmpty()) return 0
        var sent = 0
        for (range in pendingPushChunks(rows.size, chunkSize = aniListPushChunkSize(interactive))) {
            val chunk = rows.subList(range.first, range.last + 1)
            val startedAt = System.currentTimeMillis()
            val items = chunk.map { row ->
                val plan = aniListPushPlan(row.episode, row.totalEpisodes)
                AniListBatchPushItem(
                    mediaId = row.anilistId,
                    progress = plan.progress,
                    status = plan.status,
                )
            }
            val accepted = aniList.saveProgressBatch(token, items, interactive = interactive)
            // Queue (id, progress, status) only — never snapshot entities across
            // GraphQL retries. Flush re-reads Room so a live playhead saved in
            // the window is kept by historyAfterAniListPush.
            val historyPlans = ArrayList<AniListDrainHistoryPlan>(chunk.size)
            try {
                for (row in chunk) {
                    if (row.anilistId !in accepted) continue
                    sent++
                    val queued = pendingPushes.get(row.anilistId)
                    if (queued != null && shouldDeletePendingPush(queued.updatedAtEpochMs, startedAt)) {
                        pendingPushes.delete(row.anilistId)
                    }
                    val plan = aniListPushPlan(row.episode, row.totalEpisodes)
                    historyPlans += AniListDrainHistoryPlan(row.anilistId, plan.progress, plan.status)
                }
                // Partial batch: rebatch rejects once, then cap singles so Sync
                // cannot burn N×700ms on a poisoned chunk.
                val rejectedRows = pendingPushRejected(chunk, accepted) { it.anilistId }
                if (pendingPushPartialSuccess(chunk.size, accepted.size) && rejectedRows.isNotEmpty()) {
                    val rejectedItems = rejectedRows.map { row ->
                        val plan = aniListPushPlan(row.episode, row.totalEpisodes)
                        AniListBatchPushItem(
                            mediaId = row.anilistId,
                            progress = plan.progress,
                            status = plan.status,
                        )
                    }
                    val plan = pendingPushRetryPlan(rejectedItems)
                    if (plan.rebatch.isNotEmpty()) {
                        val retryStarted = System.currentTimeMillis()
                        val reAccepted = aniList.saveProgressBatch(
                            token,
                            plan.rebatch,
                            interactive = interactive,
                        )
                        for (row in rejectedRows) {
                            if (row.anilistId !in reAccepted) continue
                            sent++
                            val queued = pendingPushes.get(row.anilistId)
                            if (queued != null && shouldDeletePendingPush(queued.updatedAtEpochMs, retryStarted)) {
                                pendingPushes.delete(row.anilistId)
                            }
                            val pushPlan = aniListPushPlan(row.episode, row.totalEpisodes)
                            historyPlans += AniListDrainHistoryPlan(
                                row.anilistId,
                                pushPlan.progress,
                                pushPlan.status,
                            )
                        }
                    }
                    for (item in plan.singles) {
                        val row = rejectedRows.firstOrNull { it.anilistId == item.mediaId } ?: continue
                        val retryStarted = System.currentTimeMillis()
                        val ok = aniList.saveProgress(
                            accessToken = token,
                            mediaId = item.mediaId,
                            progress = item.progress,
                            status = item.status,
                            interactive = interactive,
                        )
                        if (!ok) continue
                        sent++
                        val queued = pendingPushes.get(row.anilistId)
                        if (queued != null && shouldDeletePendingPush(queued.updatedAtEpochMs, retryStarted)) {
                            pendingPushes.delete(row.anilistId)
                        }
                        historyPlans += AniListDrainHistoryPlan(
                            row.anilistId,
                            item.progress,
                            item.status,
                        )
                    }
                    // plan.deferred stays queued for the next drain.
                }
            } finally {
                // Retries that throw must still land accepted marks — pending
                // rows were already deleted above.
                flushDrainHistoryPlans(historyDao, historyPlans)
            }
            // Total misses stay queued for the next drain.
        }
        return sent
    }

    /**
     * Push episodes finished to AniList. [episode] is the completed count, so 0
     * clears progress after unwatching the first episode. Returns false when
     * nothing was saved, including a failed request.
     */
    suspend fun pushProgress(anilistId: Int, episode: Int, totalEpisodes: Int?): Boolean {
        val token = auth.accessToken ?: return false
        if (anilistId <= 0 || episode < 0) return false
        val startedAt = System.currentTimeMillis()
        val plan = aniListPushPlan(episode, totalEpisodes)
        // Mark/auto-mark is user-facing — do not yield to Home shelf pacing.
        val saved = aniList.saveProgress(
            accessToken = token,
            mediaId = anilistId,
            progress = plan.progress,
            status = plan.status,
            interactive = true,
        )
        if (!saved) {
            pendingPushes.upsert(
                PendingAniListPush(
                    anilistId = anilistId,
                    episode = episode,
                    totalEpisodes = totalEpisodes?.takeIf { it > 0 },
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            return false
        }
        val queued = pendingPushes.get(anilistId)
        if (queued != null && shouldDeletePendingPush(queued.updatedAtEpochMs, startedAt)) {
            pendingPushes.delete(anilistId)
        }
        val existing = historyDao.get(anilistId) ?: return true
        historyDao.upsert(historyAfterAniListPush(existing, plan.progress, plan.status))
        return true
    }
}

/** Accepted drain mark waiting for a fresh Room merge at flush. */
internal data class AniListDrainHistoryPlan(
    val anilistId: Int,
    val progress: Int,
    val status: String,
)

/**
 * Re-read history at flush so a playhead that landed during GraphQL retries
 * is not overwritten by a stale snapshot. Last plan per id wins.
 */
internal suspend fun flushDrainHistoryPlans(
    historyDao: WatchHistoryDao,
    plans: List<AniListDrainHistoryPlan>,
) {
    if (plans.isEmpty()) return
    val latest = drainHistoryPlansById(plans)
    val fresh = historyDao.getByIds(latest.keys.toList()).associateBy { it.anilistId }
    val writes = drainHistoryWritesFromFresh(latest.values.toList(), fresh)
    if (writes.isNotEmpty()) historyDao.upsertAll(writes)
}

/** Collapse duplicate accepts so one Room write lands per title. */
internal fun drainHistoryPlansById(
    plans: List<AniListDrainHistoryPlan>,
): Map<Int, AniListDrainHistoryPlan> {
    val latest = LinkedHashMap<Int, AniListDrainHistoryPlan>(plans.size)
    for (plan in plans) latest[plan.anilistId] = plan
    return latest
}

/**
 * Apply accepted progress onto **fresh** Room rows. A live scrub that landed
 * after the GraphQL round-trip is preserved by [historyAfterAniListPush].
 */
internal fun drainHistoryWritesFromFresh(
    plans: List<AniListDrainHistoryPlan>,
    freshById: Map<Int, WatchHistoryEntity>,
): List<WatchHistoryEntity> {
    if (plans.isEmpty()) return emptyList()
    val writes = ArrayList<WatchHistoryEntity>(plans.size)
    for (plan in plans) {
        val existing = freshById[plan.anilistId] ?: continue
        writes += historyAfterAniListPush(existing, plan.progress, plan.status)
    }
    return writes
}

/** What one finished-episode count becomes on AniList. */
internal data class AniListPushPlan(val progress: Int, val status: String)

internal fun aniListPushPlan(episode: Int, totalEpisodes: Int?): AniListPushPlan {
    val completed = totalEpisodes != null && totalEpisodes > 0 && episode >= totalEpisodes
    return if (completed) {
        AniListPushPlan(progress = totalEpisodes ?: episode, status = "COMPLETED")
    } else {
        AniListPushPlan(progress = episode, status = "CURRENT")
    }
}

/**
 * A push that started before the queued row was written may delete it.
 * A newer failure that lands while that attempt is in flight stays queued.
 */
internal fun shouldDeletePendingPush(queuedUpdatedAtEpochMs: Long, pushStartedAtEpochMs: Long): Boolean =
    queuedUpdatedAtEpochMs <= pushStartedAtEpochMs

/**
 * A failed save used to still rewrite the phone. A later frontier (mark watched
 * stores the next episode) or a live playhead stays put.
 */
internal fun historyAfterAniListPush(
    existing: WatchHistoryEntity,
    progress: Int,
    status: String,
): WatchHistoryEntity {
    val keepFrontier = existing.episode > progress || existing.positionMs > 2_000L
    return if (keepFrontier) {
        existing.copy(listStatus = status)
    } else {
        existing.copy(listStatus = status, episode = progress.coerceAtLeast(existing.episode))
    }
}

/**
 * Cloud progress must not rewind a phone that is already further along, or
 * replace a row whose failed push has not landed yet.
 */
internal fun mergedPullEntry(
    existing: WatchHistoryEntity?,
    anilistId: Int,
    title: String,
    coverImageUrl: String?,
    malId: Int?,
    remoteProgress: Int,
    remoteStatus: String,
    pendingProgress: Int?,
    syncedAtEpochMs: Long,
): WatchHistoryEntity {
    val remoteEpisode = remoteProgress.coerceAtLeast(0)
    val localAhead = existing != null && existing.episode > remoteEpisode
    val pendingOwnsRow = existing != null &&
        pendingProgress != null &&
        pendingProgress != remoteEpisode
    val keepLocal = localAhead || pendingOwnsRow
    val keepScrub = existing != null &&
        !keepLocal &&
        existing.episode == remoteEpisode &&
        existing.positionMs > 2_000L
    return WatchHistoryEntity(
        anilistId = anilistId,
        title = title.ifBlank { existing?.title.orEmpty() },
        coverImageUrl = coverImageUrl ?: existing?.coverImageUrl,
        episode = if (keepLocal) existing!!.episode else remoteEpisode,
        positionMs = when {
            keepLocal -> existing!!.positionMs
            keepScrub -> existing!!.positionMs
            else -> 0L
        },
        durationMs = when {
            keepLocal -> existing!!.durationMs
            keepScrub -> existing!!.durationMs
            else -> 0L
        },
        updatedAtEpochMs = maxOf(existing?.updatedAtEpochMs ?: 0L, syncedAtEpochMs),
        listStatus = if (keepLocal) existing!!.listStatus ?: remoteStatus else remoteStatus,
        malId = malId ?: existing?.malId,
    )
}

/** Cached Viewer fields used to skip a GraphQL hop on warm AniList pulls. */
internal data class AniListPullIdentity(
    val userId: Int,
    val username: String?,
)

internal fun aniListPullIdentity(
    cachedUserId: Int,
    cachedUsername: String?,
    viewer: AniListViewer?,
): AniListPullIdentity? {
    if (cachedUserId > 0) return AniListPullIdentity(cachedUserId, cachedUsername)
    if (viewer != null) return AniListPullIdentity(viewer.id, viewer.name)
    return null
}

/**
 * After drain lands rows on AniList, the next MediaList read must not reuse a
 * warm 2-minute body — even on a non-interactive background pull.
 */
internal fun mediaListForceRefreshAfterDrain(
    interactiveForceRefresh: Boolean,
    drainedAccepted: Int,
): Boolean = interactiveForceRefresh || drainedAccepted > 0

/** Background MediaList pulls must not starve interactive mark/Sync GraphQL. */
internal fun aniListMediaListUsesBackgroundPace(interactive: Boolean): Boolean = !interactive

/** Cloud progress jumped past the phone's Continue episode. */
internal fun shouldDropWarmOnFrontierAdvance(
    previousEpisode: Int?,
    nextEpisode: Int,
): Boolean = previousEpisode != null && previousEpisode > 0 && nextEpisode > previousEpisode

/**
 * Titles whose Continue episode jumped during import — one pass for warm/disk
 * recall drops so Home open races the new frontier instead of a rotten URL.
 */
internal fun frontierAdvanceIds(
    merged: List<WatchHistoryEntity>,
    previousById: Map<Int, WatchHistoryEntity>,
): List<Int> = merged.mapNotNull { row ->
    val previous = previousById[row.anilistId]?.episode
    if (shouldDropWarmOnFrontierAdvance(previous, row.episode)) row.anilistId else null
}

/** Glass Sync phases — drain first, then MediaList import. */
enum class AniListSyncPhase {
    Pushing,
    Importing,
}

/** Result line after Sync finishes — surfaces drain + frontier wins. */
internal fun aniListSyncResultLabel(result: AniListSync.Result): String {
    val extras = buildList {
        if (result.pushed > 0) add("pushed ${result.pushed}")
        if (result.frontierDrops > 0) add("${result.frontierDrops} fresher")
    }
    return if (extras.isEmpty()) {
        "Synced ${result.imported} from AniList"
    } else {
        "Synced ${result.imported} · ${extras.joinToString(" · ")} from AniList"
    }
}
