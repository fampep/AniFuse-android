// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Re-probe remembered CDN hosts so Race dock / resolve ranking are hot
 * before the user hits Play. Never probes scrape siteUrl — only CDN keys
 * already stamped into [PlaybackHostHealth].
 *
 * Soft miss on failure: a HEAD timeout or HEAD-rejecting edge must not sink
 * a last-good CDN for [PlaybackHostHealth.HOLD_MS].
 */

internal data class HostProbeSample(
    val host: String,
    val ok: Boolean,
    val latencyMs: Long,
)

/** HTTPS origin for a bare CDN host. Null when the key is not a hostname. */
internal fun hostProbeUrl(host: String): String? {
    val key = host.trim().lowercase()
    if (key.isEmpty() || '.' !in key || key.contains('/') || key.contains(' ')) return null
    return "https://$key/"
}

/**
 * Any HTTP answer means the edge is up — 403/404 still count.
 * Connection/DNS failure is the only soft-miss signal.
 */
internal fun hostProbeOutcome(httpCode: Int?, connected: Boolean): Boolean =
    connected && httpCode != null

/** Background probes only refresh on success — never wipe a working mark. */
internal fun shouldStampHostProbe(ok: Boolean): Boolean = ok

internal enum class HostProbeMethod { Head, RangeGet }

/**
 * HEAD answers replace the ledger (live edge truth for ABR soft-cap).
 * Range-GET fallback never blows away a faster resolve/HEAD stamp.
 */
internal fun hostProbeLatencyToStamp(
    previousMs: Long?,
    sampleMs: Long,
    method: HostProbeMethod = HostProbeMethod.RangeGet,
): Long {
    val sample = sampleMs.coerceAtLeast(0L)
    if (previousMs == null || previousMs <= 0L) return sample
    return when (method) {
        HostProbeMethod.Head -> sample
        HostProbeMethod.RangeGet -> minOf(previousMs, sample)
    }
}

internal data class HostProbeHit(
    val code: Int,
    val latencyMs: Long,
    val method: HostProbeMethod,
)

/**
 * Probe budget: preferred CDN first (even when not yet in the healthy ledger),
 * then affinity/warm seeds, then freshest healthy marks. Sunk marks must not
 * steal slots — callers pass healthyOnly hosts.
 */
internal fun hostsForProbeWave(
    healthyHostsNewestFirst: List<String>,
    preferredHost: String?,
    seedHosts: List<String> = emptyList(),
    limit: Int = HOST_PROBE_LIMIT,
): List<String> {
    val preferred = preferredHost?.trim()?.lowercase()
        ?.takeIf { hostProbeUrl(it) != null }
    val seeds = seedHosts.mapNotNull { raw ->
        raw.trim().lowercase().takeIf { hostProbeUrl(it) != null }
    }
    val healthy = healthyHostsNewestFirst.mapNotNull { raw ->
        raw.trim().lowercase().takeIf { it.isNotEmpty() }
    }
    return buildList {
        if (preferred != null) add(preferred)
        for (seed in seeds) {
            if (seed !in this) add(seed)
        }
        for (host in healthy) {
            if (host !in this) add(host)
        }
    }.take(limit.coerceAtLeast(0))
}

internal const val HOST_PROBE_LIMIT = 6
internal const val HOST_PROBE_TIMEOUT_MS = 1_200L

/** True when a second refresh should join the in-flight probe wave. */
internal fun shouldReuseProbeFlight(flightActive: Boolean): Boolean = flightActive

/**
 * Empty/stale coalesce: a flight started before a resolve stamp must not
 * poison joiners after [PlaybackHostHealth.ledgerGeneration] advances.
 * Generation only moves when a **new** host is noted — probe RTT refreshes
 * of hosts already in the ledger keep the active flight joinable.
 */
internal fun shouldInvalidateProbeFlight(
    flightActive: Boolean,
    flightGeneration: Long,
    ledgerGeneration: Long,
): Boolean = flightActive && flightGeneration != ledgerGeneration

/**
 * A Race-dock / post-resolve kick with a preferred CDN must not join a
 * background wave that never put that host first (or at all) in the six.
 */
internal fun shouldInvalidateProbeFlightForPreferred(
    flightActive: Boolean,
    flightPreferred: String?,
    requestedPreferred: String?,
): Boolean {
    if (!flightActive) return false
    val want = requestedPreferred?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        ?: return false
    val have = flightPreferred?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    return have != want
}

internal fun shouldStartProbeFlight(
    flightActive: Boolean,
    flightGeneration: Long,
    ledgerGeneration: Long,
): Boolean = !flightActive ||
    shouldInvalidateProbeFlight(flightActive, flightGeneration, ledgerGeneration)

internal object HostHealthProbe {
    private val refreshGate = Mutex()
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var refreshFlight: Deferred<List<HostProbeSample>>? = null
    @Volatile
    private var refreshFlightGeneration: Long = -1L
    @Volatile
    private var refreshFlightPreferred: String? = null

    /**
     * Home, Extension Store, and cold Play used to stamp three parallel probe
     * waves. Coalesce into one flight so Race dock RTT is not thrashed.
     * A resolve stamp that advances the ledger starts a fresh flight instead
     * of joining an empty/stale wave. A preferred CDN kick also starts fresh
     * when the active wave does not already lead with that host.
     */
    suspend fun refreshRemembered(
        limit: Int = HOST_PROBE_LIMIT,
        timeoutMs: Long = HOST_PROBE_TIMEOUT_MS,
        nowMs: Long = System.currentTimeMillis(),
        preferredHost: String? = null,
        seedHosts: List<String> = emptyList(),
        /** host → providerId — rebind Race dock LEAD after HOLD expires. */
        seedProviders: Map<String, String> = emptyMap(),
    ): List<HostProbeSample> {
        val preferred = preferredHost?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val seeds = seedHosts.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val providers = seedProviders.mapKeys { it.key.trim().lowercase() }
            .mapValues { it.value.trim().lowercase() }
            .filter { it.key.isNotEmpty() && it.value.isNotEmpty() }
        val work = refreshGate.withLock {
            val active = refreshFlight?.takeIf { it.isActive }
            val ledgerGen = PlaybackHostHealth.ledgerGeneration()
            if (active != null &&
                !shouldInvalidateProbeFlight(true, refreshFlightGeneration, ledgerGen) &&
                !shouldInvalidateProbeFlightForPreferred(
                    true,
                    refreshFlightPreferred,
                    preferred,
                )
            ) {
                return@withLock active
            }
            // Drop the empty/stale / wrong-preferred wave.
            active?.cancel()
            refreshScope.async {
                val hosts = hostsForProbeWave(
                    healthyHostsNewestFirst = PlaybackHostHealth.rememberedHosts(
                        nowMs = nowMs,
                        healthyOnly = true,
                    ),
                    preferredHost = preferred,
                    seedHosts = seeds,
                    limit = limit,
                )
                if (hosts.isEmpty()) {
                    emptyList()
                } else {
                    probeHosts(hosts, timeoutMs, nowMs, seedProviders = providers)
                }
            }.also {
                refreshFlight = it
                refreshFlightGeneration = ledgerGen
                refreshFlightPreferred = preferred
            }
        }
        return try {
            work.await()
        } finally {
            refreshGate.withLock {
                if (refreshFlight === work) {
                    refreshFlight = null
                    refreshFlightGeneration = -1L
                    refreshFlightPreferred = null
                }
            }
        }
    }

    /** Fire-and-forget after a resolve stamp so Race dock gets real CDN RTT. */
    fun kickRemembered(
        preferredHost: String? = null,
        seedHosts: List<String> = emptyList(),
        seedProviders: Map<String, String> = emptyMap(),
    ) {
        refreshScope.launch {
            runCatching {
                refreshRemembered(
                    preferredHost = preferredHost,
                    seedHosts = seedHosts,
                    seedProviders = seedProviders,
                )
            }
        }
    }

    suspend fun probeHosts(
        hosts: List<String>,
        timeoutMs: Long = HOST_PROBE_TIMEOUT_MS,
        nowMs: Long = System.currentTimeMillis(),
        seedProviders: Map<String, String> = emptyMap(),
    ): List<HostProbeSample> = coroutineScope {
        hosts.map { host ->
            async(Dispatchers.IO) {
                probeOne(host, timeoutMs, nowMs, seedProviders = seedProviders)
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun probeOne(
        host: String,
        timeoutMs: Long,
        nowMs: Long,
        seedProviders: Map<String, String> = emptyMap(),
    ): HostProbeSample? {
        val url = hostProbeUrl(host) ?: return null
        val hit = withContext(Dispatchers.IO) {
            runCatching { probeHit(url, timeoutMs) }.getOrNull()
        }
        val ok = hostProbeOutcome(hit?.code, connected = hit != null)
        val sampleMs = hit?.latencyMs ?: 0L
        if (shouldStampHostProbe(ok) && hit != null) {
            val stamped = hostProbeLatencyToStamp(
                previousMs = PlaybackHostHealth.latencyMs(host, nowMs),
                sampleMs = sampleMs,
                method = hit.method,
            )
            PlaybackHostHealth.note(host, ok = true, latencyMs = stamped, nowMs = nowMs)
            // Affinity seeds outlive HOLD — rebind provider → CDN so LEAD · ms
            // reads the fresh stamp instead of a cold/expired providerCdns map.
            val provider = seedProviders[host.trim().lowercase()]
            if (!provider.isNullOrBlank()) {
                PlaybackHostHealth.noteProviderCdn(provider, host, nowMs = nowMs)
            }
        }
        return HostProbeSample(host = host, ok = ok, latencyMs = sampleMs)
    }

    /**
     * Time only the attempt that answers. A HEAD timeout must not inflate
     * the GET sample that follows.
     */
    private fun probeHit(url: String, timeoutMs: Long): HostProbeHit? {
        val client = probeClient(timeoutMs)
        val headStart = android.os.SystemClock.elapsedRealtime()
        val headCode = runCatching {
            client.newCall(Request.Builder().url(url).head().build()).execute().use { it.code }
        }.getOrNull()
        if (headCode != null && headCode != 405 && headCode != 501) {
            val latency = (android.os.SystemClock.elapsedRealtime() - headStart).coerceAtLeast(0L)
            return HostProbeHit(headCode, latency, HostProbeMethod.Head)
        }
        val getStart = android.os.SystemClock.elapsedRealtime()
        val get = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-0")
            .build()
        val getCode = client.newCall(get).execute().use { it.code }
        val latency = (android.os.SystemClock.elapsedRealtime() - getStart).coerceAtLeast(0L)
        return HostProbeHit(getCode, latency, HostProbeMethod.RangeGet)
    }

    private fun probeClient(timeoutMs: Long): OkHttpClient =
        PlaybackHttp.client.newBuilder()
            .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()
}
