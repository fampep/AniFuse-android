// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.CancellationException

/**
 * How many different hosts to probe before giving up on a live shadow.
 * The primary is already playing; this stays a short background check.
 */
internal const val SHADOW_PROBE_LIMIT = 3

/**
 * Runner-up sources on a host other than the one already playing.
 * A recent success outranks an unknown CDN. A recent failure sinks to the bottom
 * and is then dropped by [deadHosts].
 */
internal fun rankShadowCandidates(
    sources: List<PlayableSource>,
    playingHost: String,
    deadHosts: Set<String> = emptySet(),
    score: (String) -> Int = { 0 },
): List<PlayableSource> {
    val playing = playingHost.trim().lowercase()
    val dead = deadHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    return sources
        .mapNotNull { source ->
            val host = playableHost(source.url)
            if (host.isEmpty() || host == playing || host in dead) null else host to source
        }
        .sortedByDescending { (host, _) -> score(host) }
        .map { it.second }
}

/**
 * Probe a different host and prefetch it while the primary plays.
 * A sibling URL in [known] is enough. A full provider race runs only when every
 * known URL shares the host that is already on screen.
 */
internal suspend fun armLiveShadow(
    known: List<PlayableSource>,
    playingHost: String,
    deadHosts: Set<String> = emptySet(),
    resolve: suspend () -> List<PlayableSource>,
    probe: suspend (PlayableSource) -> Boolean = { PlayableWarmProbe.alive(it) },
    prefetch: suspend (PlayableSource) -> Boolean = { StreamWarmPrefetch.touch(it) },
    score: (String) -> Int = { PlaybackHostHealth.score(it) },
    nowMs: () -> Long = { System.currentTimeMillis() },
): List<PlayableSource> {
    val local = probeFirstAlive(known, playingHost, deadHosts, probe, prefetch, score, nowMs)
    if (local.isNotEmpty()) return local
    val fresh = try {
        resolve()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        emptyList()
    }
    if (fresh.isEmpty()) return emptyList()
    return probeFirstAlive(fresh, playingHost, deadHosts, probe, prefetch, score, nowMs)
}

private suspend fun probeFirstAlive(
    sources: List<PlayableSource>,
    playingHost: String,
    deadHosts: Set<String>,
    probe: suspend (PlayableSource) -> Boolean,
    prefetch: suspend (PlayableSource) -> Boolean,
    score: (String) -> Int,
    nowMs: () -> Long,
): List<PlayableSource> {
    val ranked = rankShadowCandidates(sources, playingHost, deadHosts, score)
    for (candidate in ranked.take(SHADOW_PROBE_LIMIT)) {
        val host = playableHost(candidate.url)
        val started = nowMs()
        val alive = try {
            probe(candidate)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        val latency = (nowMs() - started).coerceAtLeast(0L)
        if (host.isNotEmpty()) PlaybackHostHealth.note(host, ok = alive, latencyMs = latency, nowMs = nowMs())
        if (!alive) continue
        runCatching { prefetch(candidate) }
        val rest = ranked.filter { it.url != candidate.url }
        return listOf(candidate) + rest
    }
    return emptyList()
}
