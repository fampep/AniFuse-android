// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import java.io.File

/**
 * Disk copy of the resolver feeds. A process restart can paint Latest and
 * skip providers the API already marked down, without waiting on the network.
 */
internal class AnifuseFeedCache(
    private val root: File,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun read(name: String, maxAgeMs: Long): String? {
        val file = file(name)
        if (!file.isFile) return null
        if (now() - file.lastModified() > maxAgeMs) return null
        return try {
            file.readText()
        } catch (_: Exception) {
            null
        }
    }

    fun ageMs(name: String): Long? {
        val file = file(name)
        if (!file.isFile) return null
        return (now() - file.lastModified()).coerceAtLeast(0L)
    }

    fun write(name: String, body: String) {
        try {
            root.mkdirs()
            val file = file(name)
            file.writeText(body)
            file.setLastModified(now())
        } catch (_: Exception) {
        }
    }

    fun downNames(): Set<String> {
        val text = read(DOWN_FILE, DOWN_MAX_AGE_MS) ?: return emptySet()
        return text.lineSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun writeDown(names: Collection<String>) {
        write(DOWN_FILE, names.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().joinToString("\n"))
    }

    fun clear() {
        root.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    /**
     * Local success/failure marks. A remote health snapshot rewrites
     * [DOWN_FILE]; these marks keep a just-failed provider sunk for [LOCAL_HOLD_MS].
     */
    fun notePulse(keys: Collection<String>, ok: Boolean) {
        val stamped = now()
        val drop = keys.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (drop.isEmpty()) return
        synchronized(pulseLock) {
            val keep = readPulse().filter { stamped - it.atMs <= DOWN_MAX_AGE_MS && it.key !in drop }
            val next = keep + drop.map { PulseMark(it, stamped, ok) }
            writePulse(next)
        }
    }

    fun pulseDownKeys(holdMs: Long = LOCAL_HOLD_MS): Set<String> = synchronized(pulseLock) {
        val stamped = now()
        val latest = HashMap<String, PulseMark>()
        for (mark in readPulse()) {
            val prev = latest[mark.key]
            if (prev == null || mark.atMs >= prev.atMs) latest[mark.key] = mark
        }
        latest.values
            .filter { !it.ok && stamped - it.atMs <= holdMs }
            .map { it.key }
            .toSet()
    }

    private fun readPulse(): List<PulseMark> {
        val file = File(root, PULSE_FILE)
        if (!file.isFile) return emptyList()
        return try {
            file.readLines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@mapNotNull null
                val at = parts[1].toLongOrNull() ?: return@mapNotNull null
                val key = parts[0].trim().lowercase()
                if (key.isEmpty()) return@mapNotNull null
                PulseMark(key, at, ok = parts[2] == "1")
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writePulse(marks: List<PulseMark>) {
        try {
            root.mkdirs()
            val body = marks.joinToString("\n") { mark ->
                "${mark.key}\t${mark.atMs}\t${if (mark.ok) 1 else 0}"
            }
            File(root, PULSE_FILE).writeText(body)
        } catch (_: Exception) {
        }
    }

    private data class PulseMark(val key: String, val atMs: Long, val ok: Boolean)

    private val pulseLock = Any()

    private fun file(name: String): File = File(root, safe(name))

    private fun safe(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "feed" }

    companion object {
        const val DOWN_FILE = "provider-down.txt"
        const val PULSE_FILE = "provider-pulse.txt"
        const val DOWN_MAX_AGE_MS = 30L * 60_000L
        const val LOCAL_HOLD_MS = 3L * 60_000L
        const val STALE_FEED_MS = 6L * 60 * 60_000L
    }
}
