// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import org.json.JSONArray
import org.json.JSONObject
import site.anikitty.app.data.anilist.AnimeSummary
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Title to AniList id for the latest-episode row. Memory is the source of truth;
 * disk flushes are debounced so a burst of Home latest cards never rewrite the
 * whole JSON on every put.
 */
internal class TitleResolveStore(
    private val file: File,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val ttlMs: Long = 14L * 24 * 60 * 60 * 1000,
    private val flushDelayMs: Long = 450L,
) {
    private val lock = Any()
    private val rows = LinkedHashMap<String, Stored>()
    private var loaded = false
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "title-resolve-flush").apply { isDaemon = true }
    }
    private val pendingFlush = AtomicReference<ScheduledFuture<*>?>(null)

    fun get(key: String): AnimeSummary? {
        if (key.isBlank()) return null
        val row = synchronized(lock) {
            ensureLoaded()
            rows[key]
        } ?: return null
        if (now() - row.savedAt > ttlMs) return null
        return row.toSummary()
    }

    fun put(key: String, anime: AnimeSummary) {
        if (key.isBlank() || anime.id <= 0) return
        synchronized(lock) {
            ensureLoaded()
            rows[key] = Stored(
                id = anime.id,
                title = anime.title,
                romaji = anime.romajiTitle,
                native = anime.nativeTitle,
                cover = anime.coverImageUrl,
                episodes = anime.episodes,
                showFormat = anime.format,
                score = anime.averageScore,
                savedAt = now(),
            )
            while (rows.size > 400) {
                val oldest = rows.keys.firstOrNull() ?: break
                rows.remove(oldest)
            }
        }
        scheduleFlush()
    }

    /** Force a synchronous write — tests and process shutdown. */
    fun flushNow() {
        pendingFlush.getAndSet(null)?.cancel(false)
        val snapshot = synchronized(lock) {
            ensureLoaded()
            LinkedHashMap(rows)
        }
        write(snapshot)
    }

    private fun scheduleFlush() {
        pendingFlush.getAndSet(
            scheduler.schedule(
                {
                    val snapshot = synchronized(lock) { LinkedHashMap(rows) }
                    write(snapshot)
                },
                flushDelayMs,
                TimeUnit.MILLISECONDS,
            ),
        )?.cancel(false)
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        rows.putAll(read())
    }

    private fun read(): LinkedHashMap<String, Stored> {
        if (!file.exists()) return LinkedHashMap()
        val text = try {
            file.readText()
        } catch (_: Exception) {
            return LinkedHashMap()
        }
        val arr = try {
            JSONObject(text).optJSONArray("entries")
        } catch (_: Exception) {
            null
        } ?: return LinkedHashMap()
        val out = LinkedHashMap<String, Stored>()
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val key = row.optString("k")
            val id = row.optInt("id")
            if (key.isBlank() || id <= 0) continue
            out[key] = Stored(
                id = id,
                title = row.optString("title").ifBlank { key },
                romaji = row.optString("romaji").takeIf { it.isNotBlank() },
                native = row.optString("native").takeIf { it.isNotBlank() },
                cover = row.optString("cover").takeIf { it.isNotBlank() },
                episodes = row.optInt("episodes").takeIf { it > 0 },
                showFormat = row.optString("format").takeIf { it.isNotBlank() },
                score = row.optInt("score").takeIf { it > 0 },
                savedAt = row.optLong("at"),
            )
        }
        return out
    }

    private fun write(rows: Map<String, Stored>) {
        val arr = JSONArray()
        rows.entries.toList().takeLast(400).forEach { entry ->
            val key = entry.key
            val row = entry.value
            arr.put(
                JSONObject()
                    .put("k", key)
                    .put("id", row.id)
                    .put("title", row.title)
                    .put("romaji", row.romaji ?: "")
                    .put("native", row.native ?: "")
                    .put("cover", row.cover ?: "")
                    .put("episodes", row.episodes ?: 0)
                    .put("format", row.showFormat ?: "")
                    .put("score", row.score ?: 0)
                    .put("at", row.savedAt),
            )
        }
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(JSONObject().put("entries", arr).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(JSONObject().put("entries", arr).toString())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private data class Stored(
        val id: Int,
        val title: String,
        val romaji: String?,
        val native: String?,
        val cover: String?,
        val episodes: Int?,
        val showFormat: String?,
        val score: Int?,
        val savedAt: Long,
    ) {
        fun toSummary() = AnimeSummary(
            id = id,
            title = title,
            coverImageUrl = cover,
            episodes = episodes,
            format = showFormat,
            averageScore = score,
            romajiTitle = romaji,
            nativeTitle = native,
        )
    }
}
