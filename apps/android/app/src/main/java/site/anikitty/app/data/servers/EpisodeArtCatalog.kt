// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One episode still scraped from a live site / AniZip mapping. */
data class SiteEpisodeArt(
    val number: Int,
    val imageUrl: String?,
    val title: String? = null,
    val overview: String? = null,
)

/**
 * Episode thumbnails from the sites themselves.
 *
 * AniGato's `/api/episodes/{anilistId}` already returns TVDB screencaps.
 * AniZip (`api.ani.zip`) is the same image graph most scrapers reuse when a
 * site list has no `<img>`. Results are cached on-device for a day.
 */
class EpisodeArtCatalog(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("anikitty_episode_art", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<Int, Pair<Long, Map<Int, SiteEpisodeArt>>>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun load(anilistId: Int): Map<Int, SiteEpisodeArt> {
        if (anilistId <= 0) return emptyMap()
        memory[anilistId]?.let { (at, map) ->
            if (System.currentTimeMillis() - at < MEMORY_TTL_MS && map.isNotEmpty()) return map
        }
        prefs.getString(cacheKey(anilistId), null)?.let { packed ->
            val parsed = unpack(packed)
            if (parsed.isNotEmpty()) {
                memory[anilistId] = System.currentTimeMillis() to parsed
                return parsed
            }
        }
        val fromSite = runCatching { fetchAnigato(anilistId) }.getOrDefault(emptyMap())
        val fromZip = if (fromSite.values.count { !it.imageUrl.isNullOrBlank() } >= 3) {
            emptyMap()
        } else {
            runCatching { fetchAniZip(anilistId) }.getOrDefault(emptyMap())
        }
        val merged = mergeArt(fromSite, fromZip)
        if (merged.isNotEmpty()) {
            memory[anilistId] = System.currentTimeMillis() to merged
            prefs.edit().putString(cacheKey(anilistId), pack(merged)).apply()
        }
        return merged
    }

    private fun fetchAnigato(anilistId: Int): Map<Int, SiteEpisodeArt> {
        val url = anigatoEpisodesUrl(anilistId)
        if (url.isEmpty()) return emptyMap()
        val body = get(
            url,
            mapOf(
                "Accept" to "application/json",
                "Referer" to "${AnigatoServer.ORIGIN}/",
                "Origin" to AnigatoServer.ORIGIN,
                "User-Agent" to AnigatoServer.USER_AGENT,
            ),
        ) ?: return emptyMap()
        return anigatoEpisodeArt(body)
    }

    private fun fetchAniZip(anilistId: Int): Map<Int, SiteEpisodeArt> {
        val body = get(
            "https://api.ani.zip/mappings?anilist_id=$anilistId",
            mapOf(
                "Accept" to "application/json",
                "User-Agent" to AnigatoServer.USER_AGENT,
            ),
        ) ?: return emptyMap()
        return aniZipEpisodeArt(body)
    }

    private fun get(url: String, headers: Map<String, String>): String? {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = runCatching { http.newCall(builder.build()).execute() }.getOrNull() ?: return null
        return response.use {
            if (it.code !in 200..299) null else it.body?.string()
        }
    }

    private fun cacheKey(anilistId: Int) = "art2:$anilistId"

    private fun pack(map: Map<Int, SiteEpisodeArt>): String {
        val root = JSONObject()
        root.put("at", System.currentTimeMillis())
        val rows = JSONObject()
        map.values.forEach { art ->
            rows.put(
                art.number.toString(),
                JSONObject()
                    .put("image", art.imageUrl.orEmpty())
                    .put("title", art.title.orEmpty())
                    .put("overview", art.overview.orEmpty()),
            )
        }
        root.put("episodes", rows)
        return root.toString()
    }

    private fun unpack(raw: String): Map<Int, SiteEpisodeArt> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val at = root.optLong("at", 0L)
        if (at > 0L && System.currentTimeMillis() - at > DISK_TTL_MS) return emptyMap()
        val rows = root.optJSONObject("episodes") ?: return emptyMap()
        val out = LinkedHashMap<Int, SiteEpisodeArt>()
        val keys = rows.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val number = key.toIntOrNull() ?: continue
            val row = rows.optJSONObject(key) ?: continue
            val image = row.optString("image").trim().takeIf { it.startsWith("http") }
            val title = row.optString("title").trim().takeIf { it.isNotEmpty() }
            val overview = row.optString("overview").trim().takeIf { it.isNotEmpty() }
            out[number] = SiteEpisodeArt(number, image, title, overview)
        }
        return out
    }

    companion object {
        private const val MEMORY_TTL_MS = 30 * 60 * 1000L
        private const val DISK_TTL_MS = 24 * 60 * 60 * 1000L

        @Volatile private var instance: EpisodeArtCatalog? = null

        fun shared(context: Context): EpisodeArtCatalog {
            return instance ?: synchronized(this) {
                instance ?: EpisodeArtCatalog(context.applicationContext).also { instance = it }
            }
        }
    }
}

internal fun anigatoEpisodeArt(json: String): Map<Int, SiteEpisodeArt> {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
    if (!root.optBoolean("success", false)) return emptyMap()
    val rows = root.optJSONArray("episodes") ?: return emptyMap()
    val out = LinkedHashMap<Int, SiteEpisodeArt>()
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        val number = row.optInt("number", 0)
        if (number <= 0) continue
        val image = row.optString("thumbnail").trim()
            .ifBlank { row.optString("image").trim() }
            .takeIf { it.startsWith("http") }
        val title = row.optString("title").trim().takeIf { it.isNotEmpty() }
        val overview = row.optString("description").trim()
            .ifBlank { row.optString("overview").trim() }
            .takeIf { it.isNotEmpty() }
        out[number] = SiteEpisodeArt(number, image, title, overview)
    }
    return out
}

internal fun aniZipEpisodeArt(json: String): Map<Int, SiteEpisodeArt> {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
    val rows = root.optJSONObject("episodes") ?: return emptyMap()
    val out = LinkedHashMap<Int, SiteEpisodeArt>()
    val keys = rows.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val number = key.toIntOrNull()?.takeIf { it > 0 } ?: continue
        val row = rows.optJSONObject(key) ?: continue
        val image = row.optString("image").trim()
            .ifBlank { row.optString("thumbnail").trim() }
            .takeIf { it.startsWith("http") }
        val titles = row.optJSONObject("title")
        val title = listOf("en", "x-jat", "ja")
            .mapNotNull { lang -> titles?.optString(lang)?.trim()?.takeIf { it.isNotEmpty() } }
            .firstOrNull()
            ?: row.optString("title").trim().takeIf { it.isNotEmpty() && !it.startsWith("{") }
        val overview = row.optString("overview").trim()
            .ifBlank { row.optString("summary").trim() }
            .takeIf { it.isNotEmpty() }
        out[number] = SiteEpisodeArt(number, image, title, overview)
    }
    return out
}

internal fun mergeArt(
    primary: Map<Int, SiteEpisodeArt>,
    secondary: Map<Int, SiteEpisodeArt>,
): Map<Int, SiteEpisodeArt> {
    if (primary.isEmpty()) return secondary
    if (secondary.isEmpty()) return primary
    val out = LinkedHashMap<Int, SiteEpisodeArt>()
    (primary.keys + secondary.keys).toSortedSet().forEach { number ->
        val a = primary[number]
        val b = secondary[number]
        out[number] = SiteEpisodeArt(
            number = number,
            imageUrl = a?.imageUrl?.takeIf { it.startsWith("http") }
                ?: b?.imageUrl?.takeIf { it.startsWith("http") },
            title = a?.title?.takeIf { it.isNotBlank() } ?: b?.title,
            overview = a?.overview?.takeIf { it.isNotBlank() } ?: b?.overview,
        )
    }
    return out
}
