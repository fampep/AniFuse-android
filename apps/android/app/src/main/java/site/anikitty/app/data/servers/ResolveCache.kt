// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Fast rematch of AniList → provider show pages across episodes / sessions. */
class ResolveCache(context: Context) {
    private val prefs = context.getSharedPreferences("anikitty_resolve_cache", Context.MODE_PRIVATE)
    private val episodeRoot = File(context.cacheDir, "resolve-episodes").also { it.mkdirs() }
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
        val file = episodeFile(k)
        if (!file.isFile) {
            // One-shot lift from the old SharedPreferences blob.
            val raw = prefs.getString("$k.json", null) ?: return null
            val stamped = prefs.getLong("$k.at", 0L)
            if (stamped <= 0L || System.currentTimeMillis() - stamped > EPISODE_TTL_MS) {
                prefs.edit().remove("$k.json").remove("$k.at").apply()
                return null
            }
            val parsed = decodeEpisodes(raw) ?: return null
            episodeMemory[k] = stamped to parsed
            writeEpisodeFile(file, raw, stamped)
            prefs.edit().remove("$k.json").remove("$k.at").apply()
            return parsed
        }
        val stamped = file.lastModified()
        if (System.currentTimeMillis() - stamped > EPISODE_TTL_MS) {
            file.delete()
            return null
        }
        val raw = runCatching { file.readText() }.getOrNull() ?: return null
        val parsed = decodeEpisodes(raw) ?: return null
        episodeMemory[k] = stamped to parsed
        return parsed
    }

    fun putEpisodes(provider: String, siteId: String, episodes: List<CachedEpisode>) {
        val k = epKey(provider, siteId)
        val now = System.currentTimeMillis()
        episodeMemory[k] = now to episodes
        val raw = encodeEpisodes(episodes)
        writeEpisodeFile(episodeFile(k), raw, now)
        // Drop any leftover prefs blob for this key.
        prefs.edit().remove("$k.json").remove("$k.at").apply()
        trimEpisodeFiles()
    }

    private fun writeEpisodeFile(file: File, raw: String, stamped: Long) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(raw)
            if (!tmp.renameTo(file)) {
                file.writeText(raw)
                tmp.delete()
            }
            file.setLastModified(stamped)
        }
    }

    private fun trimEpisodeFiles() {
        val files = episodeRoot.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()
        if (files.size <= MAX_EPISODE_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_EPISODE_FILES)
            .forEach { it.delete() }
    }

    private fun key(provider: String, anilistId: Int) = "$provider:$anilistId"
    private fun epKey(provider: String, siteId: String) = "$provider:eps:$siteId"

    private fun episodeFile(epKey: String): File {
        val safe = epKey.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "eps" }
        return File(episodeRoot, "$safe.json")
    }

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
        const val MAX_EPISODE_FILES = 64
    }
}
