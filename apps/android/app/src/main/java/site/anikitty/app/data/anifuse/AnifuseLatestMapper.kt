// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import site.anikitty.app.data.anilist.AiringSlot
import site.anikitty.app.data.anilist.AniListClient
import site.anikitty.app.data.anilist.AnimeSummary
import site.anikitty.app.data.servers.ProviderLatestMapper
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps the resolver API's aggregated `/v1/latest` rows onto AniList titles so
 * the home feed opens the right show. Several providers can report the same
 * episode; duplicates collapse to one row per title+episode.
 */
object AnifuseLatestMapper {
    private val tipCache = ConcurrentHashMap<String, Int>()
    private val tipMutex = Mutex()

    suspend fun toSlots(
        items: List<AnifuseLatestItem>,
        aniList: AniListClient,
        known: Map<Int, AnimeSummary> = emptyMap(),
    ): List<AiringSlot> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext emptyList()
        coroutineScope {
            items.map { item ->
                async {
                    val anime = resolveAnime(item, aniList, known) ?: return@async null
                    val episode = item.episode.coerceAtLeast(1)
                    AiringSlot(
                        anime = anime.copy(
                            coverImageUrl = anime.coverImageUrl ?: item.imageUrl,
                        ),
                        episode = episode,
                        // The API feed has no per-row airing timestamp.
                        airingAtEpochSec = 0L,
                        subEpisodes = null,
                        dubEpisodes = null,
                    ) to item.provider
                }
            }.awaitAll()
                .filterNotNull()
                .distinctBy { (slot, _) -> slot.anime.id to slot.episode }
                .map { it.first }
        }
    }

    private suspend fun resolveAnime(
        item: AnifuseLatestItem,
        aniList: AniListClient,
        known: Map<Int, AnimeSummary>,
    ): AnimeSummary? {
        tipCache[item.title]?.let { cachedId ->
            val cached = known[cachedId] ?: runCatching { aniList.media(cachedId) }.getOrNull()
            if (cached != null) return cached
            tipCache.remove(item.title)
        }
        val hits = runCatching { aniList.search(item.title, perPage = 8) }.getOrDefault(emptyList())
        val match = ProviderLatestMapper.pickMatch(item.title, hits) ?: return null
        tipMutex.withLock { tipCache[item.title] = match.id }
        return match
    }
}
