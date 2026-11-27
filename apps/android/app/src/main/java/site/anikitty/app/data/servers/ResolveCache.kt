// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Fast rematch of AniList → provider show pages across episodes / sessions. */
class ResolveCache(context: Context) {
    private val prefs = context.getSharedPreferences("anikitty_resolve_cache", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, ShowMap>()
    private val episodeMemory = ConcurrentHashMap<String, Pair<Long, List<CachedEpisode>>>()

    data class ShowMap(val siteId: String, val watchUrl: String)
    data class CachedEpisode(
        val number: Int,
        val ids: String,
        val hasSub: Boolean,
        val hasDub: Boolean,
        val imageUrl: String? = null,
        val title: String? = null,
    )

    fun getShow(provider: String, anilistId: Int): ShowMap? {
        val key = key(provider, anilistId)
        memory[key]?.let { return it }
        val siteId = prefs.getString("$key.site", null) ?: return null
        val watchUrl = prefs.getString("$key.url", null) ?: return null
        return ShowMap(siteId, watchUrl).also { memory[key] = it }
    }

    fun putShow(provider: String, anilistId: Int, siteId: String, watchUrl: String) {
        val key = key(provider, anilistId)
        val map = ShowMap(siteId, watchUrl)
        memory[key] = map
        prefs.edit()
            .putString("$key.site", siteId)
            .putString("$key.url", watchUrl)
            .apply()
    }

    fun getEpisodes(provider: String, siteId: String): List<CachedEpisode>? {
        val k = epKey(provider, siteId)
        episodeMemory[k]?.let { hit ->
            if (System.currentTimeMillis() - hit.first <= EPISODE_TTL_MS) return hit.second
            episodeMemory.remove(k)
        }
        val raw = prefs.getString("$k.json", null) ?: return null
        val stamped = prefs.getLong("$k.at", 0L)
        if (stamped <= 0L || System.currentTimeMillis() - stamped > EPISODE_TTL_MS) {
            prefs.edit().remove("$k.json").remove("$k.at").apply()
            return null
        }
        val parsed = decodeEpisodes(raw) ?: return null
        episodeMemory[k] = stamped to parsed
        return parsed
    }

    fun putEpisodes(provider: String, siteId: String, episodes: List<CachedEpisode>) {
        val k = epKey(provider, siteId)
        val now = System.currentTimeMillis()
        episodeMemory[k] = now to episodes
        prefs.edit()
            .putString("$k.json", encodeEpisodes(episodes))
            .putLong("$k.at", now)
            .apply()
    }

    private fun key(provider: String, anilistId: Int) = "$provider:$anilistId"
    private fun epKey(provider: String, siteId: String) = "$provider:eps:$siteId"

    private fun encodeEpisodes(episodes: List<CachedEpisode>): String {
        val arr = JSONArray()
        for (ep in episodes) {
            arr.put(
                JSONObject()
                    .put("n", ep.number)
                    .put("ids", ep.ids)
                    .put("sub", ep.hasSub)
                    .put("dub", ep.hasDub)
                    .put("img", ep.imageUrl ?: JSONObject.NULL)
                    .put("t", ep.title ?: JSONObject.NULL),
            )
        }
        return arr.toString()
    }

    private fun decodeEpisodes(raw: String): List<CachedEpisode>? {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val out = ArrayList<CachedEpisode>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += CachedEpisode(
                number = o.optInt("n"),
                ids = o.optString("ids"),
                hasSub = o.optBoolean("sub", true),
                hasDub = o.optBoolean("dub", false),
                imageUrl = o.optString("img").takeIf { it.isNotBlank() && it != "null" },
                title = o.optString("t").takeIf { it.isNotBlank() && it != "null" },
            )
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private companion object {
        /** Episode lists survive process death for half an hour — cold start Watch stays instant. */
        const val EPISODE_TTL_MS = 30 * 60 * 1000L
    }
}
