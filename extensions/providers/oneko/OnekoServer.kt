// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * On-device [0neko](https://beta.0neko.cc/) resolve.
 *
 * The watch page is keyed by AniList id. The phone asks the same public
 * backends that site uses, then plays the HLS file here.
 */
class OnekoServer(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build(),
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "0neko",
        description = "On-device 0neko resolve. The playlist plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=0neko.cc&sz=128",
        siteUrl = SITE,
    )

    override suspend fun resolve(
        anilistId: Int,
        episode: Int,
        audio: String,
        titleHint: String?,
    ): List<PlayableSource> = withContext(Dispatchers.IO) {
        require(anilistId > 0) { "anilistId required" }
        require(episode > 0) { "episode required" }
        val mode = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
        val backends = onekoBackends(read("$SITE/backend.json") ?: error(BLOCKED))
        if (backends.isEmpty()) error(BLOCKED)
        for (provider in PROVIDERS) {
            val listBody = get(backends, "/episodes/$provider/$anilistId?map=false") ?: continue
            val ep = onekoPickEpisode(onekoEpisodes(listBody, provider, mode), episode) ?: continue
            val watchBody = get(backends, "/${ep.path}") ?: continue
            val files = onekoStreams(watchBody).map { onekoPlayable(it, mode) }
            if (files.isNotEmpty()) return@withContext namedByServer(files, server.name)
        }
        error(MISSING)
    }

    private fun get(backends: List<String>, path: String): String? {
        val suffix = if (path.startsWith("/")) path else "/$path"
        for (root in backends) {
            val body = read(root.trimEnd('/') + suffix) ?: continue
            if (body.isNotBlank()) return body
        }
        return null
    }

    private fun read(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("Origin", SITE)
            .header("Referer", "$SITE/")
            .header("User-Agent", USER_AGENT)
            .build()
        val call = http.newCall(request)
        call.timeout().timeout(12, TimeUnit.SECONDS)
        val response = runCatching { call.execute() }.getOrNull() ?: return null
        response.use {
            if (it.code !in 200..299) return null
            return it.body?.string()?.takeIf { body -> body.isNotBlank() }
        }
    }

    companion object {
        const val ID = "oneko"
        const val SITE = "https://beta.0neko.cc"
        const val MISSING = "0neko does not have this episode."
        const val BLOCKED = "0neko could not be reached."
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        /** Same order 0neko asks first. Stop at the first provider that answers. */
        val PROVIDERS = listOf("kaa", "anikoto", "anizone")
    }
}

internal data class OnekoEpisode(val path: String, val number: Int)

internal data class OnekoStream(
    val url: String,
    val server: String,
    val hls: Boolean,
    val headers: Map<String, String>,
)

internal fun onekoBackends(body: String): List<String> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    val backends = root.optJSONObject("backends") ?: return emptyList()
    val out = LinkedHashSet<String>()
    fun take(key: String) {
        val rows = backends.optJSONArray(key) ?: return
        for (i in 0 until rows.length()) {
            val url = rows.optString(i).trim().trimEnd('/')
            if (url.startsWith("http") && !url.contains("example.com")) out += url
        }
    }
    take("main")
    take("backup")
    return out.toList()
}

internal fun onekoEpisodes(body: String, provider: String, audio: String): List<OnekoEpisode> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    val block = root.optJSONObject(provider) ?: return emptyList()
    if (block.has("error") && !block.has("episodes")) return emptyList()
    val rows = block.optJSONObject("episodes")?.optJSONArray(audio) ?: return emptyList()
    val out = ArrayList<OnekoEpisode>(rows.length())
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        val number = row.optInt("number", -1)
        if (number < 1) continue
        val path = row.optString("id").trim().trimStart('/')
        if (path.isEmpty()) continue
        out += OnekoEpisode(path = path, number = number)
    }
    return out
}

internal fun onekoPickEpisode(episodes: List<OnekoEpisode>, episode: Int): OnekoEpisode? =
    episodes.firstOrNull { it.number == episode }

internal fun onekoStreams(body: String): List<OnekoStream> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    if (root.has("error") && !root.has("streams")) return emptyList()
    val rows = root.optJSONArray("streams") ?: return emptyList()
    val top = onekoHeaderMap(root.optJSONObject("headers"))
    val out = ArrayList<OnekoStream>(rows.length())
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        if (row.optString("type").equals("embed", true)) continue
        if (row.has("isActive") && !row.optBoolean("isActive", true)) continue
        val url = row.optString("url").trim()
        if (!url.startsWith("http")) continue
        val headers = LinkedHashMap<String, String>()
        headers.putAll(top)
        headers.putAll(onekoHeaderMap(row.optJSONObject("headers")))
        row.optString("referer").trim().takeIf { it.startsWith("http") }?.let { headers["Referer"] = it }
        out += OnekoStream(
            url = url,
            server = row.optString("server").ifBlank { "0neko" }.trim(),
            hls = row.optString("type").equals("hls", true) || url.contains(".m3u8", true),
            headers = headers,
        )
    }
    return out
}

internal fun onekoPlayable(stream: OnekoStream, audio: String): PlayableSource =
    PlayableSource(
        url = stream.url,
        headers = stream.headers,
        label = stream.server,
        hls = stream.hls,
        audioLanguage = requestedAudioLanguage(audio),
    )

private fun onekoHeaderMap(obj: JSONObject?): Map<String, String> {
    if (obj == null) return emptyMap()
    val out = LinkedHashMap<String, String>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = obj.optString(key).trim()
        if (key.isNotBlank() && value.isNotEmpty()) out[key] = value
    }
    return out
}
