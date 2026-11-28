// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

internal const val HOST_PROBE_LIMIT = 6
internal const val HOST_PROBE_TIMEOUT_MS = 1_200L

internal object HostHealthProbe {
    suspend fun refreshRemembered(
        limit: Int = HOST_PROBE_LIMIT,
        timeoutMs: Long = HOST_PROBE_TIMEOUT_MS,
        nowMs: Long = System.currentTimeMillis(),
    ): List<HostProbeSample> {
        val hosts = PlaybackHostHealth.rememberedHosts(nowMs).take(limit)
        if (hosts.isEmpty()) return emptyList()
        return probeHosts(hosts, timeoutMs, nowMs)
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
        val started = android.os.SystemClock.elapsedRealtime()
        val code = withContext(Dispatchers.IO) {
            runCatching { probeCode(url, timeoutMs) }.getOrNull()
        }
        val latency = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
        val ok = hostProbeOutcome(code, connected = code != null)
        if (shouldStampHostProbe(ok)) {
            PlaybackHostHealth.note(host, ok = true, latencyMs = latency, nowMs = nowMs)
        }
        return HostProbeSample(host = host, ok = ok, latencyMs = latency)
    }

    private fun probeCode(url: String, timeoutMs: Long): Int? {
        val client = probeClient(timeoutMs)
        val head = Request.Builder().url(url).head().build()
        val headCode = runCatching {
            client.newCall(head).execute().use { it.code }
        }.getOrNull()
        if (headCode != null && headCode != 405 && headCode != 501) return headCode
        // Many CDNs reject HEAD — ranged GET is the same soft check PlayableWarmProbe uses.
        val get = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-0")
            .build()
        return client.newCall(get).execute().use { it.code }
    }

    private fun probeClient(timeoutMs: Long): OkHttpClient =
        PlaybackHttp.client.newBuilder()
            .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()
}
