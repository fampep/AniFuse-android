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

    private fun file(name: String): File = File(root, safe(name))

    private fun safe(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "feed" }

    companion object {
        const val DOWN_FILE = "provider-down.txt"
        const val DOWN_MAX_AGE_MS = 30L * 60_000L
        const val STALE_FEED_MS = 6L * 60 * 60_000L
    }
}
