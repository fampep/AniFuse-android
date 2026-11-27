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
    private val cache: ResolveCache? = null,
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

    @Volatile private var cachedBackends: List<String> = emptyList()

    override suspend fun resolve(
        anilistId: Int,
        episode: Int,
        audio: String,
        titleHint: String?,
    ): List<PlayableSource> = withContext(Dispatchers.IO) {
        require(anilistId > 0) { "anilistId required" }
        require(episode > 0) { "episode required" }
        val mode = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
        val backends = backends()
        if (backends.isEmpty()) error(BLOCKED)
        var reachable = false
        val preferred = cache?.getShow(ID, anilistId)?.siteId?.takeIf { it in PROVIDERS }
        val providers = if (preferred != null) {
            listOf(preferred) + PROVIDERS.filterNot { it == preferred }
        } else {
            PROVIDERS
        }
        for (provider in providers) {
            val list = get(backends, "/episodes/$provider/$anilistId?map=false")
            if (list.reachable) reachable = true
            val listBody = list.body ?: continue
            val ep = onekoPickEpisode(onekoEpisodes(listBody, provider, mode), episode) ?: continue
            val watch = get(backends, "/${ep.path}")
            if (watch.reachable) reachable = true
            val watchBody = watch.body ?: continue
            val files = onekoStreams(watchBody).map { onekoPlayable(it, mode) }
            if (files.isNotEmpty()) {
                cache?.putShow(ID, anilistId, provider, "$SITE/series/$anilistId")
                return@withContext namedByServer(files, server.name)
            }
        }
        error(if (reachable) MISSING else BLOCKED)
    }

    private fun backends(): List<String> {
        cachedBackends.takeIf { it.isNotEmpty() }?.let { return it }
        val cached = cache?.getShow(ID, BACKEND_CACHE_ID)?.siteId
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.startsWith("http") }
            .orEmpty()
        if (cached.isNotEmpty()) {
            cachedBackends = cached
            return cached
        }
        val body = read("$SITE/backend.json")
        if (!body.reachable || body.body.isNullOrBlank()) error(BLOCKED)
        val listed = onekoBackends(body.body!!)
        if (listed.isEmpty()) error(BLOCKED)
        cachedBackends = listed
        cache?.putShow(ID, BACKEND_CACHE_ID, listed.joinToString("\n"), "$SITE/backend.json")
        return listed
    }

    private fun get(backends: List<String>, path: String): OnekoReply {
        val suffix = if (path.startsWith("/")) path else "/$path"
        var reachable = false
        for (root in backends) {
            val reply = read(root.trimEnd('/') + suffix)
            if (reply.reachable) reachable = true
            val body = reply.body
            if (!body.isNullOrBlank()) return OnekoReply(body, reachable = true)
        }
        return OnekoReply(null, reachable = reachable)
    }

    private fun read(url: String): OnekoReply {
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
        val response = runCatching { call.execute() }.getOrNull()
            ?: return OnekoReply(null, reachable = false)
        response.use {
            val text = it.body?.string().orEmpty()
            if (providerChallenged(it.code, text)) return OnekoReply(null, reachable = false)
            if (it.code == 404) return OnekoReply(null, reachable = true)
            if (it.code !in 200..299) return OnekoReply(null, reachable = it.code < 500)
            return OnekoReply(text.takeIf { body -> body.isNotBlank() }, reachable = true)
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

        /** Sentinel AniList id used only to persist backend.json mirrors. */
        internal const val BACKEND_CACHE_ID = -1
    }
}

internal data class OnekoReply(val body: String?, val reachable: Boolean)

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
