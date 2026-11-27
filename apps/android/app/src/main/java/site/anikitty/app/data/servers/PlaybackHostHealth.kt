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
    private var dir: File? = null
    private var lastBody: String? = null

    @Volatile
    private var playing: String? = null

    /** The CDN the player is reading right now. Downloads on this host wait. */
    fun notePlaying(host: String?) {
        playing = host?.trim()?.lowercase()?.ifBlank { null }
    }

    fun playingHost(): String? = playing

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
            marks[key] = Mark(ok = ok, atMs = nowMs, latencyMs = latencyMs.coerceAtLeast(0L))
            while (marks.size > MAX) {
                val oldest = marks.entries.minByOrNull { it.value.atMs }?.key ?: break
                marks.remove(oldest)
            }
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

    fun clear() {
        synchronized(lock) {
            marks.clear()
            lastBody = null
            val root = dir ?: return
            runCatching { File(root, FILE_NAME).delete() }
        }
    }

    /** Tests keep the ledger off disk. */
    fun detach() {
        playing = null
        synchronized(lock) {
            dir = null
            lastBody = null
            marks.clear()
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
