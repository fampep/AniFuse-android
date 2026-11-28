// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import site.anikitty.app.data.anilist.FrontierWarmTarget

/** Cap parallel frontier warms so a big Sync does not stampede resolvers. */
internal const val FRONTIER_WARM_PARALLEL = 2

/** Title hint for Player API lookup — MAL when known. */
internal fun frontierStreamHint(title: String, malId: Int?): String {
    val clean = title.trim()
    val mal = malId?.takeIf { it > 0 } ?: return clean
    return if (clean.isEmpty()) "mal:$mal" else "$clean || mal:$mal"
}

/** True when a frontier warm target is worth a resolve race. */
internal fun frontierWarmEligible(target: FrontierWarmTarget): Boolean =
    target.anilistId > 0 && target.episode > 0

/**
 * Re-fill [PlayableWarmCache] for a Continue episode after AniList frontier
 * advance dropped the old recall — same pipeline as player next-ep warm.
 */
internal suspend fun warmFrontierContinue(
    catalog: ServerCatalog,
    target: FrontierWarmTarget,
    audio: String,
    appContext: Context? = null,
): Boolean {
    if (!frontierWarmEligible(target)) return false
    val lane = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
    val hint = frontierStreamHint(target.title, target.malId)
    return coroutineScope {
        val captions = async {
            SubtitleLibrary.prefetch(target.anilistId, target.episode)
        }
        val existing = PlayableWarmCache.peek(
            anilistId = target.anilistId,
            episode = target.episode,
            audio = lane,
        )
        val chosen = warmPlayable(
            existing = existing,
            resolve = {
                catalog.resolveWithFailover(
                    anilistId = target.anilistId,
                    episode = target.episode,
                    audio = lane,
                    titleHint = hint,
                    warmLead = true,
                ).sources
            },
        )
        if (chosen.isEmpty()) {
            PlayableWarmCache.drop(
                anilistId = target.anilistId,
                episode = target.episode,
                audio = lane,
            )
            captions.await()
            return@coroutineScope false
        }
        PlayableWarmCache.put(
            anilistId = target.anilistId,
            episode = target.episode,
            audio = lane,
            sources = chosen,
        )
        StreamWarmPrefetch.touch(chosen.first())
        if (appContext != null) {
            SourceBadgeCache.remember(
                appContext,
                target.anilistId,
                target.episode,
                chosen,
            )
        }
        captions.await()
        true
    }
}

/** Run frontier warms with a small parallel gate. */
internal suspend fun warmFrontierContinues(
    catalog: ServerCatalog,
    targets: List<FrontierWarmTarget>,
    audio: String,
    appContext: Context? = null,
    parallel: Int = FRONTIER_WARM_PARALLEL,
) {
    if (targets.isEmpty()) return
    val gate = Semaphore(parallel.coerceAtLeast(1))
    coroutineScope {
        targets.map { target ->
            async {
                gate.withPermit {
                    runCatching {
                        warmFrontierContinue(catalog, target, audio, appContext)
                    }
                }
            }
        }.awaitAll()
    }
}
