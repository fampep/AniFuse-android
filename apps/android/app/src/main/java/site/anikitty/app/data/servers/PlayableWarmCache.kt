// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

/**
 * In-process TTL cache for a resolved next-episode race.
 *
 * Warming at ~65% of the current episode stores the winning [PlayableSource]
 * list here so autoplay / "Play now" can skip a second CDN resolve. CDN signed
 * URLs rot, so entries expire after [TTL_MS] and [take] consumes them.
 */
object PlayableWarmCache {
    data class Entry(
        val sources: List<PlayableSource>,
        val savedAtMs: Long,
    )

    /** Signed CDN links rarely survive longer than this in practice. */
    internal const val TTL_MS = 12L * 60L * 1000L
    private const val MAX = 24

    private val lock = Any()
    private val map = LinkedHashMap<String, Entry>(MAX, 0.75f, true)

    fun put(
        anilistId: Int,
        episode: Int,
        audio: String,
        sources: List<PlayableSource>,
        lane: String = LANE_NEXT,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (anilistId <= 0 || episode <= 0 || sources.isEmpty()) return
        val key = key(anilistId, episode, audio, lane)
        synchronized(lock) {
            map[key] = Entry(sources = sources, savedAtMs = nowMs)
            while (map.size > MAX) {
                val oldest = map.keys.firstOrNull() ?: break
                map.remove(oldest)
            }
        }
    }

    fun peek(
        anilistId: Int,
        episode: Int,
        audio: String,
        lane: String = LANE_NEXT,
        nowMs: Long = System.currentTimeMillis(),
    ): List<PlayableSource>? {
        if (anilistId <= 0 || episode <= 0) return null
        val key = key(anilistId, episode, audio, lane)
        synchronized(lock) {
            val entry = map[key] ?: return null
            if (nowMs - entry.savedAtMs > TTL_MS) {
                map.remove(key)
                return null
            }
            return entry.sources
        }
    }

    /** Consume a warm hit so a stale URL is never replayed after playback starts. */
    fun take(
        anilistId: Int,
        episode: Int,
        audio: String,
        lane: String = LANE_NEXT,
        nowMs: Long = System.currentTimeMillis(),
    ): List<PlayableSource>? {
        if (anilistId <= 0 || episode <= 0) return null
        val key = key(anilistId, episode, audio, lane)
        synchronized(lock) {
            val entry = map.remove(key) ?: return null
            if (nowMs - entry.savedAtMs > TTL_MS) return null
            return entry.sources
        }
    }

    fun drop(
        anilistId: Int,
        episode: Int,
        audio: String,
        lane: String = LANE_NEXT,
    ) {
        if (anilistId <= 0 || episode <= 0) return
        synchronized(lock) { map.remove(key(anilistId, episode, audio, lane)) }
    }

    fun clear() {
        synchronized(lock) { map.clear() }
    }

    internal fun key(anilistId: Int, episode: Int, audio: String, lane: String = LANE_NEXT): String {
        val lang = audio.trim().lowercase().ifBlank { "sub" }
        val slot = lane.trim().lowercase().ifBlank { LANE_NEXT }
        return "$anilistId:$episode:$lang:$slot"
    }

    const val LANE_NEXT = "next"
    const val LANE_STANDBY = "standby"
    /** Probed runner-up for the episode that is playing right now. */
    const val LANE_SHADOW = "shadow"
    /**
     * Last successful resolve for the episode the user just opened.
     * Leave the player and come back within [TTL_MS] → probe + play, no race.
     */
    const val LANE_LIVE = "live"
}

/**
 * Consume the freshest warm lane that still answers. Live reopen wins over a
 * next-episode prefetch so a mid-watch return never races cold CDNs again.
 */
suspend fun takeProbedWarm(
    anilistId: Int,
    episode: Int,
    audio: String,
    lanes: List<String> = listOf(PlayableWarmCache.LANE_LIVE, PlayableWarmCache.LANE_NEXT),
    nowMs: Long = System.currentTimeMillis(),
    probe: suspend (List<PlayableSource>) -> List<PlayableSource> = { PlayableWarmProbe.filterAlive(it) },
): List<PlayableSource> {
    for (lane in lanes) {
        val raw = PlayableWarmCache.take(anilistId, episode, audio, lane, nowMs) ?: continue
        val alive = probe(raw)
        if (alive.isNotEmpty()) return alive
    }
    return emptyList()
}
