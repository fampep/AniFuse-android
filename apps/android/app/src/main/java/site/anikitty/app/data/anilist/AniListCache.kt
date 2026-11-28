// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anilist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk + memory cache for AniList GraphQL responses.
 * Bodies live as files under cacheDir — SharedPreferences never holds multi-KB JSON.
 */
class AniListCache(context: Context) {
    private val root = File(context.cacheDir, "anilist-cache").also { it.mkdirs() }
    private val legacy = context.getSharedPreferences("anikitty_anilist_cache", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, Entry>()
    private val migrateOnce = Any()
    @Volatile private var migrated = false

    data class Entry(val body: String, val savedAtMs: Long)

    fun get(key: String, ttlMs: Long): String? {
        migrateLegacyIfNeeded()
        val now = System.currentTimeMillis()
        memory[key]?.let { hit ->
            if (now - hit.savedAtMs <= ttlMs) return hit.body
            memory.remove(key)
        }
        val file = fileFor(key)
        if (!file.isFile) return null
        val at = file.lastModified()
        if (now - at > ttlMs) {
            file.delete()
            return null
        }
        val body = runCatching { file.readText() }.getOrNull() ?: return null
        return body.also { memory[key] = Entry(it, at) }
    }

    fun put(key: String, body: String) {
        migrateLegacyIfNeeded()
        val now = System.currentTimeMillis()
        memory[key] = Entry(body, now)
        runCatching {
            val file = fileFor(key)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(body)
            if (!tmp.renameTo(file)) {
                file.writeText(body)
                tmp.delete()
            }
            file.setLastModified(now)
        }
        trimIfNeeded()
    }

    fun getList(key: String, ttlMs: Long, parse: (JSONArray) -> List<AnimeSummary>): List<AnimeSummary>? {
        val body = get(key, ttlMs) ?: return null
        return runCatching {
            parse(JSONObject(body).getJSONObject("data").getJSONObject("Page").getJSONArray("media"))
        }.getOrNull()
    }

    fun putList(key: String, rawResponse: String) {
        put(key, rawResponse)
    }

    /** Drop feed/search/media entries so the next fetch hits the network. */
    fun clearAll() {
        memory.clear()
        root.listFiles()?.forEach { it.delete() }
        legacy.edit().clear().apply()
    }

    fun invalidate(key: String) {
        memory.remove(key)
        fileFor(key).delete()
        legacy.edit().remove("$key.body").remove("$key.at").apply()
    }

    /** Drop every entry whose key starts with [prefix] (e.g. mediaList after a push). */
    fun invalidatePrefix(prefix: String) {
        if (prefix.isEmpty()) return
        memory.keys.filter { it.startsWith(prefix) }.forEach { memory.remove(it) }
        val safePrefix = prefix.replace(Regex("[^A-Za-z0-9._-]"), "_")
        root.listFiles()?.forEach { file ->
            val name = file.name.removeSuffix(".json")
            if (name.startsWith(safePrefix)) file.delete()
        }
    }

    private fun migrateLegacyIfNeeded() {
        if (migrated) return
        synchronized(migrateOnce) {
            if (migrated) return
            migrated = true
            val all = legacy.all
            if (all.isEmpty()) return
            val ed = legacy.edit()
            all.keys
                .filter { it.endsWith(".body") }
                .forEach { bodyKey ->
                    val base = bodyKey.removeSuffix(".body")
                    val body = legacy.getString(bodyKey, null) ?: return@forEach
                    val at = legacy.getLong("$base.at", System.currentTimeMillis())
                    memory[base] = Entry(body, at)
                    runCatching {
                        val file = fileFor(base)
                        file.writeText(body)
                        file.setLastModified(at)
                    }
                    ed.remove(bodyKey).remove("$base.at")
                }
            ed.apply()
        }
    }

    private fun trimIfNeeded() {
        val files = root.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
        if (files.size <= MAX_ENTRIES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_ENTRIES)
            .forEach { file -> file.delete() }
    }

    private fun fileFor(key: String): File {
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "entry" }
        return File(root, "$safe.json")
    }

    companion object {
        const val TTL_FEED_MS = 30 * 60 * 1000L
        const val TTL_SEARCH_MS = 10 * 60 * 1000L
        const val TTL_MEDIA_MS = 6 * 60 * 60 * 1000L
        /** Short so a 5-minute background pull is often a cache hit. */
        const val TTL_LIST_MS = 2 * 60 * 1000L
        private const val MAX_ENTRIES = 120
    }
}
