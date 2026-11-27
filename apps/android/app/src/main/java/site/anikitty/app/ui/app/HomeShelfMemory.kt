// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.json.JSONArray
import org.json.JSONObject
import site.anikitty.app.data.anilist.AnimeSummary
import java.io.File

/**
 * Last Home shelves. Memory keeps a tab switch from painting shimmer.
 * The same snapshot is written beside the app files so a cold start paints
 * posters before AniList answers.
 */
internal object HomeShelfMemory {
    var trending: List<AnimeSummary> = emptyList()
        private set
    var seasonal: List<AnimeSummary> = emptyList()
        private set
    var popular: List<AnimeSummary> = emptyList()
        private set
    var topTen: List<AnimeSummary> = emptyList()
        private set
    private var savedAt: Long = 0L
    private var directory: File? = null

    fun fresh(now: Long = System.currentTimeMillis()): Boolean =
        trending.isNotEmpty() && now - savedAt < FRESH_MS

    fun bind(dir: File) {
        directory = dir
    }

    /** Reads the last snapshot once per process. A missing file is a cold install. */
    fun hydrate(dir: File) {
        directory = dir
        if (trending.isNotEmpty()) return
        val file = File(dir, FILE)
        if (!file.isFile) return
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        trending = decode(root.optJSONArray("trending"))
        seasonal = decode(root.optJSONArray("seasonal"))
        popular = decode(root.optJSONArray("popular"))
        topTen = decode(root.optJSONArray("topTen"))
        savedAt = root.optLong("savedAt")
    }

    fun save(
        trending: List<AnimeSummary>,
        seasonal: List<AnimeSummary>,
        popular: List<AnimeSummary>,
        topTen: List<AnimeSummary>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (trending.isEmpty()) return
        this.trending = trending
        this.seasonal = seasonal
        this.popular = popular
        this.topTen = topTen
        savedAt = now
        val dir = directory ?: return
        val payload = JSONObject()
            .put("savedAt", now)
            .put("trending", encode(trending))
            .put("seasonal", encode(seasonal))
            .put("popular", encode(popular))
            .put("topTen", encode(topTen))
        runCatching {
            val file = File(dir, FILE)
            val tmp = File(dir, "$FILE.tmp")
            tmp.writeText(payload.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(payload.toString())
                tmp.delete()
            }
        }
    }

    /** Drops the in-memory copy. The file stays, so hydrate can prove a restart. */
    fun forgetMemory() {
        trending = emptyList()
        seasonal = emptyList()
        popular = emptyList()
        topTen = emptyList()
        savedAt = 0L
    }

    private fun encode(list: List<AnimeSummary>): JSONArray {
        val array = JSONArray()
        list.forEach { anime ->
            array.put(
                JSONObject()
                    .put("id", anime.id)
                    .put("title", anime.title)
                    .put("cover", anime.coverImageUrl.orEmpty())
                    .put("banner", anime.bannerImageUrl.orEmpty())
                    .put("episodes", anime.episodes ?: -1)
                    .put("format", anime.format.orEmpty())
                    .put("score", anime.averageScore ?: -1)
                    .put("status", anime.status.orEmpty()),
            )
        }
        return array
    }

    private fun decode(array: JSONArray?): List<AnimeSummary> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optInt("id")
                if (id <= 0) continue
                add(
                    AnimeSummary(
                        id = id,
                        title = item.optString("title"),
                        coverImageUrl = item.optString("cover").ifBlank { null },
                        bannerImageUrl = item.optString("banner").ifBlank { null },
                        episodes = item.optInt("episodes").takeIf { it > 0 },
                        format = item.optString("format").ifBlank { null },
                        averageScore = item.optInt("score").takeIf { it > 0 },
                        status = item.optString("status").ifBlank { null },
                    ),
                )
            }
        }
    }

    private const val FRESH_MS = 30L * 60 * 1000
    private const val FILE = "home_shelves_v1.json"
}
