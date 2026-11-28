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
 * Probe budget: healthy hosts only, preferred CDN first, then freshest.
 * Sunk marks must not steal slots from the Race dock wave.
 */
internal fun hostsForProbeWave(
    healthyHostsNewestFirst: List<String>,
    preferredHost: String?,
    limit: Int = HOST_PROBE_LIMIT,
): List<String> {
    val preferred = preferredHost?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val rest = healthyHostsNewestFirst.filter { it != preferred }
    return buildList {
        if (preferred != null && preferred in healthyHostsNewestFirst) add(preferred)
        addAll(rest)
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

    /**
     * Home, Extension Store, and cold Play used to stamp three parallel probe
     * waves. Coalesce into one flight so Race dock RTT is not thrashed.
     * A resolve stamp that advances the ledger starts a fresh flight instead
     * of joining an empty/stale wave.
     */
    suspend fun refreshRemembered(
        limit: Int = HOST_PROBE_LIMIT,
        timeoutMs: Long = HOST_PROBE_TIMEOUT_MS,
        nowMs: Long = System.currentTimeMillis(),
        preferredHost: String? = null,
    ): List<HostProbeSample> {
        val work = refreshGate.withLock {
            val active = refreshFlight?.takeIf { it.isActive }
            val ledgerGen = PlaybackHostHealth.ledgerGeneration()
            if (active != null &&
                !shouldInvalidateProbeFlight(true, refreshFlightGeneration, ledgerGen)
            ) {
                return@withLock active
            }
            // Drop the empty/stale wave so it cannot keep joining after a resolve stamp.
            active?.cancel()
            refreshScope.async {
                val hosts = hostsForProbeWave(
                    healthyHostsNewestFirst = PlaybackHostHealth.rememberedHosts(
                        nowMs = nowMs,
                        healthyOnly = true,
                    ),
                    preferredHost = preferredHost,
                    limit = limit,
                )
                if (hosts.isEmpty()) emptyList() else probeHosts(hosts, timeoutMs, nowMs)
            }.also {
                refreshFlight = it
                refreshFlightGeneration = ledgerGen
            }
        }
        return try {
            work.await()
        } finally {
            refreshGate.withLock {
                if (refreshFlight === work) {
                    refreshFlight = null
                    refreshFlightGeneration = -1L
                }
            }
        }
    }

    /** Fire-and-forget after a resolve stamp so Race dock gets real CDN RTT. */
    fun kickRemembered(preferredHost: String? = null) {
        refreshScope.launch {
            runCatching { refreshRemembered(preferredHost = preferredHost) }
        }
    }

    suspend fun probeHosts(
        hosts: List<String>,
        timeoutMs: Long = HOST_PROBE_TIMEOUT_MS,
        nowMs: Long = System.currentTimeMillis(),
    ): List<HostProbeSample> = coroutineScope {
        hosts.map { host ->
            async(Dispatchers.IO) { probeOne(host, timeoutMs, nowMs) }
        }.awaitAll().filterNotNull()
    }

    private suspend fun probeOne(
        host: String,
        timeoutMs: Long,
        nowMs: Long,
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
