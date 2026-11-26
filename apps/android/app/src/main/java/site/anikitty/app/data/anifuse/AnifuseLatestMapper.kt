// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import kotlinx.coroutines.Dispatchers
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
 *
 * Titles are resolved in batched searches, then remembered. A cold start does
 * not fire one GraphQL search per row.
 */
object AnifuseLatestMapper {
    private val tipCache = ConcurrentHashMap<String, Int>()

    internal suspend fun toSlots(
        items: List<AnifuseLatestItem>,
        aniList: AniListClient,
        known: Map<Int, AnimeSummary> = emptyMap(),
        store: TitleResolveStore? = null,
    ): List<AiringSlot> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext emptyList()
        val resolved = HashMap<String, AnimeSummary>()
        val pending = ArrayList<AnifuseLatestItem>()
        for (item in items) {
            val cached = cachedAnime(item.title, known, store)
            if (cached != null) resolved[item.title] = cached else pending += item
        }
        if (pending.isNotEmpty()) {
            val hits = runCatching {
                aniList.searchMany(pending.map { it.title }.distinct())
            }.getOrDefault(emptyMap())
            for (item in pending) {
                val match = ProviderLatestMapper.pickMatch(item.title, hits[item.title].orEmpty())
                    ?: continue
                resolved[item.title] = match
                remember(item.title, match, store)
            }
        }
        slotsFromResolved(items, resolved)
    }

    internal fun slotsFromResolved(
        items: List<AnifuseLatestItem>,
        resolved: Map<String, AnimeSummary>,
    ): List<AiringSlot> {
        return items.mapNotNull { item ->
            val anime = resolved[item.title] ?: return@mapNotNull null
            AiringSlot(
                anime = anime.copy(coverImageUrl = anime.coverImageUrl ?: item.imageUrl),
                episode = item.episode.coerceAtLeast(1),
                airingAtEpochSec = 0L,
                subEpisodes = null,
                dubEpisodes = null,
            )
        }.distinctBy { it.anime.id to it.episode }
    }

    private fun cachedAnime(
        title: String,
        known: Map<Int, AnimeSummary>,
        store: TitleResolveStore?,
    ): AnimeSummary? {
        val key = ProviderLatestMapper.normalize(title)
        if (key.isBlank()) return null
        tipCache[key]?.let { id -> known[id]?.let { return it } }
        known.values.firstOrNull { ProviderLatestMapper.pickMatch(title, listOf(it)) != null }
            ?.let { match ->
                tipCache[key] = match.id
                return match
            }
        val stored = store?.get(key) ?: return null
        tipCache[key] = stored.id
        return stored
    }

    private fun remember(title: String, anime: AnimeSummary, store: TitleResolveStore?) {
        val key = ProviderLatestMapper.normalize(title)
        if (key.isBlank()) return
        tipCache[key] = anime.id
        store?.put(key, anime)
    }
}
