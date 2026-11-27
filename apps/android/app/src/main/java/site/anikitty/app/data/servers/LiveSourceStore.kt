// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Survives process death for the episode the user just left.
 * In-memory [PlayableWarmCache] is faster; this is the reopen path after
 * the OS kills the app mid-watch. Signed CDN links still get probed.
 */
class LiveSourceStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remember(
        anilistId: Int,
        episode: Int,
        audio: String,
        sources: List<PlayableSource>,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (anilistId <= 0 || episode <= 0 || sources.isEmpty()) return
        val payload = JSONObject()
            .put("at", nowMs)
            .put("sources", encode(sources.take(4)))
            .toString()
        prefs.edit().putString(key(anilistId, episode, audio), payload).apply()
        trim()
    }

    fun recall(
        anilistId: Int,
        episode: Int,
        audio: String,
        nowMs: Long = System.currentTimeMillis(),
        ttlMs: Long = PlayableWarmCache.TTL_MS,
    ): List<PlayableSource>? {
        if (anilistId <= 0 || episode <= 0) return null
        val k = key(anilistId, episode, audio)
        val raw = prefs.getString(k, null) ?: return null
        val obj = runCatching { JSONObject(raw) }.getOrNull()
        if (obj == null) {
            prefs.edit().remove(k).apply()
            return null
        }
        val at = obj.optLong("at", 0L)
        if (at <= 0L || nowMs - at > ttlMs) {
            prefs.edit().remove(k).apply()
            return null
        }
        val sources = decode(obj.optJSONArray("sources"))
        if (sources.isEmpty()) {
            prefs.edit().remove(k).apply()
            return null
        }
        return sources
    }

    fun forget(anilistId: Int, episode: Int, audio: String) {
        if (anilistId <= 0 || episode <= 0) return
        prefs.edit().remove(key(anilistId, episode, audio)).apply()
    }

    private fun trim() {
        val all = prefs.all
        if (all.size <= MAX) return
        val ranked = all.entries.mapNotNull { (k, v) ->
            val at = runCatching { JSONObject(v as String).optLong("at", 0L) }.getOrDefault(0L)
            k to at
        }.sortedBy { it.second }
        val drop = ranked.take(all.size - MAX).map { it.first }
        if (drop.isEmpty()) return
        val editor = prefs.edit()
        drop.forEach { editor.remove(it) }
        editor.apply()
    }

    companion object {
        private const val PREFS = "anifuse_live_sources"
        private const val MAX = 48

        internal fun key(anilistId: Int, episode: Int, audio: String): String {
            val lang = audio.trim().lowercase().ifBlank { "sub" }
            return "$anilistId:$episode:$lang"
        }

        private fun encode(sources: List<PlayableSource>): JSONArray {
            val arr = JSONArray()
            for (source in sources) {
                val headers = JSONObject()
                source.headers.forEach { (k, v) -> headers.put(k, v) }
                arr.put(
                    JSONObject()
                        .put("url", source.url)
                        .put("label", source.label)
                        .put("quality", source.quality)
                        .put("audio", source.audio)
                        .put("audioLanguage", source.audioLanguage)
                        .put("serverName", source.serverName)
                        .put("headers", headers),
                )
            }
            return arr
        }

        private fun decode(arr: JSONArray?): List<PlayableSource> {
            if (arr == null || arr.length() == 0) return emptyList()
            val out = ArrayList<PlayableSource>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url").trim()
                if (url.isEmpty()) continue
                val headerObj = obj.optJSONObject("headers")
                val headers = linkedMapOf<String, String>()
                if (headerObj != null) {
                    val keys = headerObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = headerObj.optString(k)
                        if (k.isNotBlank() && v.isNotBlank()) headers[k] = v
                    }
                }
                out += PlayableSource(
                    url = url,
                    headers = headers,
                    label = obj.optString("label").ifBlank { "Stream" },
                    quality = obj.optString("quality").ifBlank { null },
                    audio = obj.optString("audio").ifBlank { null },
                    audioLanguage = obj.optString("audioLanguage").ifBlank { null },
                    serverName = obj.optString("serverName").ifBlank { null },
                )
            }
            return out
        }
    }
}
