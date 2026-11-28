// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import site.anikitty.app.data.skip.SkipInterval
import site.anikitty.app.data.skip.SkipTimes

/**
 * Survives process death for the episode the user just left.
 * In-memory [PlayableWarmCache] is faster; this is the reopen path after
 * the OS kills the app mid-watch. Signed CDN links still get probed.
 * Playback unwrap fields (Gotaku / Flix / HLS / strip / skip / captions)
 * are persisted so a warm reopen still plays.
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

    /** Drop every audio/episode disk recall for a title after the frontier advances. */
    fun forgetTitle(anilistId: Int) {
        if (anilistId <= 0) return
        val needle = "$anilistId:"
        val keys = prefs.all.keys.filter { it.startsWith(needle) }
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
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

        internal fun encode(sources: List<PlayableSource>): JSONArray {
            val arr = JSONArray()
            for (source in sources) {
                arr.put(encodeOne(source))
            }
            return arr
        }

        internal fun decode(arr: JSONArray?): List<PlayableSource> {
            if (arr == null || arr.length() == 0) return emptyList()
            val out = ArrayList<PlayableSource>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                decodeOne(obj)?.let { out += it }
            }
            return out
        }

        private fun encodeOne(source: PlayableSource): JSONObject {
            val headers = JSONObject()
            source.headers.forEach { (k, v) -> headers.put(k, v) }
            val obj = JSONObject()
                .put("url", source.url)
                .put("label", source.label)
                .put("quality", source.quality)
                .put("audio", source.audio)
                .put("audioLanguage", source.audioLanguage)
                .put("serverName", source.serverName)
                .put("leadKey", source.leadKey)
                .put("qualityRating", source.qualityRating)
                .put("stripBytes", source.stripBytes)
                .put("hls", source.hls)
                .put("headers", headers)
            source.gotakuObf?.let { obf ->
                obj.put(
                    "gotakuObf",
                    JSONObject()
                        .put("keySeed", obf.keySeed)
                        .put("playlistToken", obf.playlistToken)
                        .put("segmentBytes", obf.segmentBytes),
                )
            }
            source.flix?.let { flix ->
                obj.put(
                    "flix",
                    JSONObject()
                        .put("playlistKey", flix.playlistKey)
                        .put("segmentMask", flix.segmentMask)
                        .put("englishAudio", flix.englishAudio),
                )
            }
            if (!source.skip.isEmpty) {
                obj.put("skip", encodeSkip(source.skip))
            }
            if (source.subtitles.isNotEmpty()) {
                val caps = JSONArray()
                source.subtitles.forEach { track ->
                    caps.put(
                        JSONObject()
                            .put("url", track.url)
                            .put("label", track.label)
                            .put("language", track.language)
                            .put("mimeType", track.mimeType),
                    )
                }
                obj.put("subtitles", caps)
            }
            source.thumbnails?.let { thumb ->
                val th = JSONObject().put("url", thumb.url)
                val thHeaders = JSONObject()
                thumb.headers.forEach { (k, v) -> thHeaders.put(k, v) }
                th.put("headers", thHeaders)
                obj.put("thumbnails", th)
            }
            return obj
        }

        private fun decodeOne(obj: JSONObject): PlayableSource? {
            val url = obj.optString("url").trim()
            if (url.isEmpty()) return null
            return PlayableSource(
                url = url,
                headers = decodeHeaders(obj.optJSONObject("headers")),
                label = obj.optString("label").ifBlank { "Stream" },
                stripBytes = obj.optInt("stripBytes", SegmentStripDataSource.DEFAULT_STRIP_BYTES),
                subtitles = decodeSubtitles(obj.optJSONArray("subtitles")),
                gotakuObf = decodeGotaku(obj.optJSONObject("gotakuObf")),
                skip = decodeSkip(obj.optJSONObject("skip")),
                quality = obj.optString("quality").ifBlank { null },
                audio = obj.optString("audio").ifBlank { null },
                qualityRating = obj.optString("qualityRating").ifBlank { null },
                flix = decodeFlix(obj.optJSONObject("flix")),
                thumbnails = decodeThumb(obj.optJSONObject("thumbnails")),
                hls = obj.optBoolean("hls", false),
                audioLanguage = obj.optString("audioLanguage").ifBlank { null },
                serverName = obj.optString("serverName").ifBlank { null },
                leadKey = obj.optString("leadKey").ifBlank { null },
            )
        }

        private fun decodeHeaders(obj: JSONObject?): Map<String, String> {
            if (obj == null) return emptyMap()
            val headers = linkedMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k)
                if (k.isNotBlank() && v.isNotBlank()) headers[k] = v
            }
            return headers
        }

        private fun decodeGotaku(obj: JSONObject?): GotakuObf? {
            if (obj == null) return null
            val seed = obj.optString("keySeed").trim()
            val token = obj.optString("playlistToken").trim()
            if (seed.isEmpty() || token.isEmpty()) return null
            return GotakuObf(
                keySeed = seed,
                playlistToken = token,
                segmentBytes = obj.optInt("segmentBytes", 8192),
            )
        }

        private fun decodeFlix(obj: JSONObject?): FlixUnwrap? {
            if (obj == null) return null
            val key = obj.optString("playlistKey").trim()
            val mask = obj.optString("segmentMask").trim()
            if (key.isEmpty() || mask.isEmpty()) return null
            return FlixUnwrap(
                playlistKey = key,
                segmentMask = mask,
                englishAudio = obj.optBoolean("englishAudio", false),
            )
        }

        private fun encodeSkip(skip: SkipTimes): JSONObject {
            val obj = JSONObject()
            skip.intro?.let { obj.put("intro", encodeInterval(it)) }
            skip.outro?.let { obj.put("outro", encodeInterval(it)) }
            skip.recap?.let { obj.put("recap", encodeInterval(it)) }
            skip.preview?.let { obj.put("preview", encodeInterval(it)) }
            return obj
        }

        private fun encodeInterval(iv: SkipInterval): JSONObject =
            JSONObject().put("startSec", iv.startSec).put("endSec", iv.endSec)

        private fun decodeSkip(obj: JSONObject?): SkipTimes {
            if (obj == null) return SkipTimes()
            return SkipTimes(
                intro = decodeInterval(obj.optJSONObject("intro")),
                outro = decodeInterval(obj.optJSONObject("outro")),
                recap = decodeInterval(obj.optJSONObject("recap")),
                preview = decodeInterval(obj.optJSONObject("preview")),
            )
        }

        private fun decodeInterval(obj: JSONObject?): SkipInterval? {
            if (obj == null) return null
            val start = obj.optDouble("startSec", Double.NaN)
            val end = obj.optDouble("endSec", Double.NaN)
            if (start.isNaN() || end.isNaN()) return null
            return SkipInterval(start, end)
        }

        private fun decodeSubtitles(arr: JSONArray?): List<SubtitleTrack> {
            if (arr == null || arr.length() == 0) return emptyList()
            val out = ArrayList<SubtitleTrack>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url").trim()
                if (url.isEmpty()) continue
                out += SubtitleTrack(
                    url = url,
                    label = obj.optString("label").ifBlank { "Subtitles" },
                    language = obj.optString("language").ifBlank { null },
                    mimeType = obj.optString("mimeType").ifBlank { "text/vtt" },
                )
            }
            return out
        }

        private fun decodeThumb(obj: JSONObject?): ThumbnailTrack? {
            if (obj == null) return null
            val url = obj.optString("url").trim()
            if (url.isEmpty()) return null
            return ThumbnailTrack(url = url, headers = decodeHeaders(obj.optJSONObject("headers")))
        }
    }
}
