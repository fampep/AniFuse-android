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
    /** Disk line prefix for provider→CDN maps (host mark lines stay unprefixed). */
    private const val CDN_MAP_PREFIX = ">"

    private val lock = Any()
    private val marks = LinkedHashMap<String, Mark>(MAX, 0.75f, true)
    /** Last playable CDN host per provider id — ranks the race window by CDN, not siteUrl. */
    private val providerCdns = LinkedHashMap<String, ProviderCdn>(MAX, 0.75f, true)
    private var dir: File? = null
    private var lastBody: String? = null

    data class ProviderCdn(val host: String, val atMs: Long)

    @Volatile
    private var playing: String? = null

    @Volatile
    private var playingOwner: Any? = null

    /**
     * Bumps only when a **new** CDN host enters the ledger — not on probe RTT
     * refreshes of hosts already remembered. That way an in-flight probe wave
     * can [PlaybackHostHealth.note] without invalidating its own coalesce.
     */
    @Volatile
    private var generation: Long = 0L

    fun ledgerGeneration(): Long = generation

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
            // Probe RTT updates must not thrash coalesce — only a new host key.
            if (previous == null) generation += 1L
            persistLocked()
        }
    }

    /** Bind a provider id to the CDN host that just played so the next race ranks by CDN. */
    fun noteProviderCdn(
        providerId: String,
        host: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val id = providerId.trim().lowercase()
        val key = host.trim().lowercase()
        if (id.isEmpty() || key.isEmpty()) return
        synchronized(lock) {
            val stale = providerCdns.filterValues { nowMs - it.atMs > HOLD_MS }.keys
            stale.forEach { providerCdns.remove(it) }
            val previous = providerCdns[id]
            providerCdns[id] = ProviderCdn(host = key, atMs = nowMs)
            while (providerCdns.size > MAX) {
                val oldest = providerCdns.entries.minByOrNull { it.value.atMs }?.key ?: break
                providerCdns.remove(oldest)
            }
            // New provider binding only — remaps / refreshes keep coalesce joinable.
            if (previous == null) generation += 1L
            persistLocked()
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

    /** Positive RTT for an ok host still inside [HOLD_MS], else null. */
    fun latencyMs(host: String, nowMs: Long = System.currentTimeMillis()): Long? {
        val key = host.trim().lowercase()
        if (key.isEmpty()) return null
        val mark = synchronized(lock) { marks[key] } ?: return null
        if (nowMs - mark.atMs > HOLD_MS || !mark.ok) return null
        return mark.latencyMs.takeIf { it > 0L }
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
        val cdn = synchronized(lock) {
            providerCdns[id]?.takeIf { nowMs - it.atMs <= HOLD_MS }?.host
        }
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
        val host = synchronized(lock) {
            providerCdns[id]?.takeIf { nowMs - it.atMs <= HOLD_MS }?.host
        } ?: playableHost(siteUrl.orEmpty()).ifBlank { null }
            ?: return null
        val mark = synchronized(lock) { marks[host] } ?: return null
        if (nowMs - mark.atMs > HOLD_MS || !mark.ok) return null
        return mark.latencyMs.takeIf { it > 0L }
    }

    fun clear() {
        synchronized(lock) {
            marks.clear()
            providerCdns.clear()
            generation += 1L
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
            generation = 0L
        }
    }

    private fun loadLocked() {
        val root = dir ?: return
        val file = File(root, FILE_NAME)
        if (!file.isFile) return
        val nowMs = System.currentTimeMillis()
        runCatching {
            file.readLines().forEach { line ->
                if (line.startsWith(CDN_MAP_PREFIX)) {
                    val parts = line.removePrefix(CDN_MAP_PREFIX).split('\t')
                    if (parts.size < 3) return@forEach
                    val id = parts[0].trim().lowercase()
                    val host = parts[1].trim().lowercase()
                    val at = parts[2].toLongOrNull() ?: return@forEach
                    if (id.isEmpty() || host.isEmpty() || nowMs - at > HOLD_MS) return@forEach
                    val previous = providerCdns[id]
                    if (previous == null || at >= previous.atMs) {
                        providerCdns[id] = ProviderCdn(host = host, atMs = at)
                    }
                    return@forEach
                }
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
        val markLines = marks.entries.map { (host, mark) ->
            "$host\t${mark.atMs}\t${if (mark.ok) 1 else 0}\t${mark.latencyMs}"
        }
        val mapLines = providerCdns.entries.map { (id, map) ->
            "$CDN_MAP_PREFIX$id\t${map.host}\t${map.atMs}"
        }
        val body = (markLines + mapLines).joinToString("\n")
        if (body == lastBody) return
        lastBody = body
        runCatching {
            root.mkdirs()
            File(root, FILE_NAME).writeText(body)
        }
    }
}
