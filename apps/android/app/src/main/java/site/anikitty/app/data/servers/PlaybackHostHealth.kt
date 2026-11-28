// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import java.io.File

/**
 * In-memory ledger of CDN hosts that just played or just died.
 *
 * The resolver race used to re-read a pulse file on every attempt. This ledger
 * stays in memory, ranks a shadow standby without touching disk, and flushes
 * only when the marks actually change. A host that 403s mid-roll is sunk for
 * [HOLD_MS] so the next probe does not pick it again.
 */
internal object PlaybackHostHealth {
    data class Mark(val ok: Boolean, val atMs: Long, val latencyMs: Long)

    const val HOLD_MS = 3L * 60L * 1000L
    private const val FILE_NAME = "host-health.txt"
    private const val MAX = 64

    private val lock = Any()
    private val marks = LinkedHashMap<String, Mark>(MAX, 0.75f, true)
    /** Last playable CDN host per provider id — ranks the race window by CDN, not siteUrl. */
    private val providerCdns = LinkedHashMap<String, String>(MAX, 0.75f, true)
    private var dir: File? = null
    private var lastBody: String? = null

    @Volatile
    private var playing: String? = null

    @Volatile
    private var playingOwner: Any? = null

    /**
     * The CDN [owner] is reading. A newer player can replace it. The owner that
     * left must not clear a host the next episode already recorded.
     */
    fun notePlaying(host: String?, owner: Any) {
        synchronized(lock) {
            val key = host?.trim()?.lowercase()?.ifBlank { null }
            playing = key
            playingOwner = if (key == null) null else owner
        }
    }

    fun clearPlaying(owner: Any) {
        synchronized(lock) {
            if (playingOwner !== owner) return
            playing = null
            playingOwner = null
        }
    }

    fun playingHost(): String? = playing

    /** CDN hosts still inside [HOLD_MS] — used by background health probes. */
    fun rememberedHosts(nowMs: Long = System.currentTimeMillis()): List<String> =
        synchronized(lock) {
            marks.entries
                .filter { nowMs - it.value.atMs <= HOLD_MS }
                .sortedByDescending { it.value.atMs }
                .map { it.key }
        }

    fun attach(root: File) {
        synchronized(lock) {
            if (dir?.absolutePath == root.absolutePath && marks.isNotEmpty()) return
            dir = root
            loadLocked()
        }
    }

    fun note(host: String, ok: Boolean, latencyMs: Long = 0L, nowMs: Long = System.currentTimeMillis()) {
        val key = host.trim().lowercase()
        if (key.isEmpty()) return
        synchronized(lock) {
            val stale = marks.filterValues { nowMs - it.atMs > HOLD_MS }.keys
            stale.forEach { marks.remove(it) }
            // A later ok=true with no sample (playback ready / shadow arm) must
            // not wipe a resolve-stamped RTT — Race dock reads that latency.
            val previous = marks[key]
            val keptLatency = when {
                latencyMs > 0L -> latencyMs
                ok && previous != null && previous.ok && nowMs - previous.atMs <= HOLD_MS ->
                    previous.latencyMs
                else -> 0L
            }
            marks[key] = Mark(ok = ok, atMs = nowMs, latencyMs = keptLatency)
            while (marks.size > MAX) {
                val oldest = marks.entries.minByOrNull { it.value.atMs }?.key ?: break
                marks.remove(oldest)
            }
            persistLocked()
        }
    }

    /** Bind a provider id to the CDN host that just played so the next race ranks by CDN. */
    fun noteProviderCdn(providerId: String, host: String) {
        val id = providerId.trim().lowercase()
        val key = host.trim().lowercase()
        if (id.isEmpty() || key.isEmpty()) return
        synchronized(lock) {
            providerCdns[id] = key
            while (providerCdns.size > MAX) {
                val oldest = providerCdns.keys.firstOrNull() ?: break
                providerCdns.remove(oldest)
            }
        }
    }

    fun score(host: String, nowMs: Long = System.currentTimeMillis()): Int {
        val key = host.trim().lowercase()
        if (key.isEmpty()) return 0
        val mark = synchronized(lock) { marks[key] } ?: return 0
        if (nowMs - mark.atMs > HOLD_MS) return 0
        if (!mark.ok) return -1_000
        val latency = mark.latencyMs.coerceIn(0L, 5_000L)
        return (1_000 - (latency / 5L).toInt()).coerceAtLeast(1)
    }

    /**
     * Prefer the last CDN this provider delivered. Fall back to [siteUrl] only when
     * no CDN has been recorded yet — site hosts and CDN hosts are different keys.
     */
    fun scoreProvider(
        providerId: String,
        siteUrl: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Int {
        val id = providerId.trim().lowercase()
        val cdn = synchronized(lock) { providerCdns[id] }
        val cdnScore = if (cdn != null) score(cdn, nowMs) else 0
        if (cdnScore != 0) return cdnScore
        return score(playableHost(siteUrl.orEmpty()), nowMs)
    }

    /** Last measured CDN RTT for [providerId], or null when cold / sunk / unknown. */
    fun providerLatencyMs(
        providerId: String,
        siteUrl: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Long? {
        val id = providerId.trim().lowercase()
        val host = synchronized(lock) { providerCdns[id] }
            ?: playableHost(siteUrl.orEmpty()).ifBlank { null }
            ?: return null
        val mark = synchronized(lock) { marks[host] } ?: return null
        if (nowMs - mark.atMs > HOLD_MS || !mark.ok) return null
        return mark.latencyMs.takeIf { it > 0L }
    }

    fun clear() {
        synchronized(lock) {
            marks.clear()
            providerCdns.clear()
            lastBody = null
            val root = dir ?: return
            runCatching { File(root, FILE_NAME).delete() }
        }
    }

    /** Tests keep the ledger off disk. */
    fun detach() {
        synchronized(lock) {
            playing = null
            playingOwner = null
            dir = null
            lastBody = null
            marks.clear()
            providerCdns.clear()
        }
    }

    private fun loadLocked() {
        val root = dir ?: return
        val file = File(root, FILE_NAME)
        if (!file.isFile) return
        val nowMs = System.currentTimeMillis()
        runCatching {
            file.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 4) return@forEach
                val host = parts[0].trim().lowercase()
                val at = parts[1].toLongOrNull() ?: return@forEach
                if (host.isEmpty() || nowMs - at > HOLD_MS) return@forEach
                val latency = parts[3].toLongOrNull() ?: 0L
                val previous = marks[host]
                if (previous == null || at >= previous.atMs) {
                    marks[host] = Mark(ok = parts[2] == "1", atMs = at, latencyMs = latency)
                }
            }
        }
        lastBody = null
    }

    private fun persistLocked() {
        val root = dir ?: return
        val body = marks.entries.joinToString("\n") { (host, mark) ->
            "$host\t${mark.atMs}\t${if (mark.ok) 1 else 0}\t${mark.latencyMs}"
        }
        if (body == lastBody) return
        lastBody = body
        runCatching {
            root.mkdirs()
            File(root, FILE_NAME).writeText(body)
        }
    }
}
