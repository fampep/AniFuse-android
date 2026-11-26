// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import site.anikitty.app.data.skip.SkipTimes
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * On-device anigato.lol resolve.
 *
 * AniList stays the catalog. The watch id is the AniList id, the same number
 * in `/watch/{id}/{episode}`. The episode list is `/api/episodes/{id}`.
 * Sub or dub is `/api/watch/{id}/sub|dub/{episode}`. A dub request never
 * plays a sub row.
 *
 * Rows whose file is `/api/stream/` are real HLS masters. The self-hosted
 * `master.m3u8` links are not playlists, so they are left alone. The phone
 * requests the master with the site Referer, Origin, and User-Agent.
 */
class AnigatoServer(
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
        name = "AniGato",
        description = "On-device anigato.lol resolve. The episode playlist plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=anigato.lol&sz=128",
        siteUrl = ORIGIN,
    )

    override suspend fun resolve(
        anilistId: Int,
        episode: Int,
        audio: String,
        titleHint: String?,
    ): List<PlayableSource> = withContext(Dispatchers.IO) {
        require(anilistId > 0) { "anilistId required" }
        require(episode > 0) { "episode required" }
        val dub = audio.equals("dub", ignoreCase = true)
        val numbers = episodeNumbers(anilistId)
        if (episode !in numbers) error(MISSING)
        val page = "$ORIGIN/watch/$anilistId/$episode"
        val watchUrl = anigatoWatchUrl(anilistId, episode, dub)
        var payload = open(watchUrl, page)
        var streams = anigatoStreams(payload, dub) ?: error(BLOCKED)
        if (streams.isEmpty() && anigatoStillLoading(payload)) {
            payload = open(watchUrl, page)
            streams = anigatoStreams(payload, dub) ?: error(BLOCKED)
        }
        if (streams.isEmpty()) error(MISSING)
        val ranked = ArrayList<Pair<PlayableSource, Int>>()
        for (stream in streams) {
            val playlist = playlistOf(stream.url, page) ?: continue
            val source = anigatoPlayable(
                url = stream.url,
                playlist = playlist,
                dub = dub,
                tracks = stream.subtitles,
                userAgent = USER_AGENT,
            ) ?: continue
            ranked += source to anigatoHeight(playlist)
        }
        if (ranked.isEmpty()) error(UNPLAYABLE)
        ranked.sortedByDescending { it.second }.map { it.first }
    }

    private fun episodeNumbers(anilistId: Int): List<Int> {
        val siteId = anilistId.toString()
        cache?.getEpisodes(ID, siteId)?.takeIf { it.isNotEmpty() }?.let { cached ->
            return cached.map { it.number }
        }
        val body = open(anigatoEpisodesUrl(anilistId), "$ORIGIN/")
        val art = anigatoEpisodeArt(body)
        val numbers = if (art.isNotEmpty()) {
            art.keys.sorted()
        } else {
            anigatoEpisodeNumbers(body) ?: error(BLOCKED)
        }
        if (numbers.isNotEmpty()) {
            cache?.putEpisodes(
                ID,
                siteId,
                numbers.map { number ->
                    val still = art[number]
                    ResolveCache.CachedEpisode(
                        number = number,
                        ids = number.toString(),
                        hasSub = true,
                        hasDub = true,
                        imageUrl = still?.imageUrl,
                        title = still?.title,
                    )
                },
            )
            cache?.putShow(ID, anilistId, siteId, "$ORIGIN/watch/$anilistId")
        }
        return numbers
    }

    private fun open(url: String, referer: String): String {
        if (url.isEmpty()) error(MISSING)
        val body = read(url, apiHeaders(referer)) ?: error(BLOCKED)
        if (anigatoChallenge(body.code, body.text)) error(BLOCKED)
        if (body.code == 404) error(MISSING)
        if (body.code !in 200..299) error(BLOCKED)
        return body.text
    }

    private fun playlistOf(url: String, referer: String): String? {
        val body = read(url, mediaHeaders(referer)) ?: return null
        if (anigatoChallenge(body.code, body.text)) return null
        if (body.code !in 200..299) return null
        if (!anigatoPlaylistBody(body.text)) return null
        return body.text
    }

    private fun read(url: String, headers: Map<String, String>): HttpText? {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = runCatching { http.newCall(builder.build()).execute() }.getOrNull() ?: return null
        response.use { return HttpText(it.code, it.body?.string().orEmpty()) }
    }

    private fun apiHeaders(referer: String): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Referer" to referer,
        "Origin" to ORIGIN,
        "User-Agent" to USER_AGENT,
    )

    private fun mediaHeaders(referer: String): Map<String, String> = mapOf(
        "Accept" to "application/vnd.apple.mpegurl,*/*",
        "Referer" to referer,
        "Origin" to ORIGIN,
        "User-Agent" to USER_AGENT,
    )

    private data class HttpText(val code: Int, val text: String)

    companion object {
        const val ID = "anigato"
        const val MISSING = "AniGato does not have this episode."
        const val BLOCKED = "AniGato could not be reached."
        const val UNPLAYABLE = "AniGato does not serve a playlist this player can open."
        const val ORIGIN = "https://anigato.lol"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}

internal data class AnigatoStream(
    val url: String,
    val subtitles: List<SubtitleTrack>,
)

internal fun anigatoEpisodesUrl(anilistId: Int): String {
    if (anilistId <= 0) return ""
    return "${AnigatoServer.ORIGIN}/api/episodes/$anilistId"
}

internal fun anigatoWatchUrl(anilistId: Int, episode: Int, dub: Boolean): String {
    if (anilistId <= 0 || episode <= 0) return ""
    val audio = if (dub) "dub" else "sub"
    return "${AnigatoServer.ORIGIN}/api/watch/$anilistId/$audio/$episode"
}

internal fun anigatoEpisodeNumbers(json: String): List<Int>? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (!root.has("episodes")) return null
    if (!root.optBoolean("success", false)) return emptyList()
    val rows = root.optJSONArray("episodes") ?: return emptyList()
    val numbers = ArrayList<Int>()
    for (index in 0 until rows.length()) {
        val number = rows.optJSONObject(index)?.optInt("number", 0) ?: continue
        if (number > 0) numbers += number
    }
    return numbers
}

internal fun anigatoStreams(json: String, dub: Boolean): List<AnigatoStream>? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (!root.optBoolean("success", false)) return emptyList()
    if (dub && root.optBoolean("dubUnavailable", false)) return emptyList()
    val rows = root.optJSONArray("streams") ?: return emptyList()
    val wanted = if (dub) "dub" else "sub"
    val found = ArrayList<AnigatoStream>()
    for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        val type = row.optString("type").trim().lowercase()
        if (type.isNotEmpty() && type != wanted) continue
        val url = anigatoPlaylistUrl(row.optString("url")) ?: continue
        found += AnigatoStream(url, anigatoSubtitles(row.optJSONArray("subtitles")))
    }
    return found
}

internal fun anigatoStillLoading(json: String): Boolean {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return false
    if (!root.optBoolean("success", false)) return false
    return root.optBoolean("loadingMore") ||
        root.optBoolean("primaryPending") ||
        root.optBoolean("mainPending")
}

internal fun anigatoPlaylistUrl(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty() || text.contains("..") || text.contains("\\")) return null
    val absolute = when {
        text.startsWith("/api/stream/") -> "${AnigatoServer.ORIGIN}$text"
        text.startsWith("${AnigatoServer.ORIGIN}/api/stream/") -> text
        else -> return null
    }
    val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.userInfo != null) return null
    if (!uri.host.equals("anigato.lol", ignoreCase = true)) return null
    val path = uri.rawPath ?: return null
    if (!path.startsWith("/api/stream/")) return null
    return absolute
}

internal fun anigatoPlaylistBody(text: String): Boolean =
    text.trimStart().startsWith("#EXTM3U")

internal fun anigatoHeight(playlist: String): Int =
    Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
        .find(playlist)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0

internal fun anigatoChallenge(code: Int, body: String): Boolean {
    if (code == 403 || code == 429 || code == 503) return true
    val lower = body.lowercase()
    return "just a moment" in lower ||
        "challenge-platform" in lower ||
        "cf-browser-verification" in lower
}

internal fun anigatoPlayable(
    url: String,
    playlist: String,
    dub: Boolean,
    tracks: List<SubtitleTrack>,
    userAgent: String,
): PlayableSource? {
    val file = anigatoPlaylistUrl(url) ?: return null
    if (!anigatoPlaylistBody(playlist)) return null
    val mode = if (dub) "dub" else "sub"
    val fields = BuiltInSourceMeta.anigato(playlist, mode)
    return PlayableSource(
        url = file,
        headers = mapOf(
            "Referer" to "${AnigatoServer.ORIGIN}/",
            "Origin" to AnigatoServer.ORIGIN,
            "User-Agent" to userAgent,
        ),
        label = fields.quality ?: if (dub) "DUB" else "SUB",
        subtitles = tracks,
        skip = SkipTimes(),
        quality = fields.quality,
        audio = fields.audio,
        hls = true,
        audioLanguage = requestedAudioLanguage(mode),
    )
}

private fun anigatoSubtitles(rows: JSONArray?): List<SubtitleTrack> {
    if (rows == null) return emptyList()
    val tracks = ArrayList<SubtitleTrack>()
    val seen = HashSet<String>()
    for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        val url = anigatoCaptionUrl(row.optString("url").ifBlank { row.optString("file") }) ?: continue
        if (!seen.add(url)) continue
        val label = row.optString("label").trim().ifBlank { row.optString("lang").trim() }
        if (label.isEmpty()) continue
        tracks += SubtitleTrack(
            url = url,
            label = label,
            language = anigatoCaptionLanguage(row.optString("lang").ifBlank { row.optString("label") }),
            mimeType = "text/vtt",
        )
    }
    return tracks
}

private fun anigatoCaptionUrl(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty() || text.contains("..") || text.contains("\\")) return null
    val absolute = when {
        text.startsWith("/api/sub.vtt") -> "${AnigatoServer.ORIGIN}$text"
        text.startsWith("${AnigatoServer.ORIGIN}/api/sub.vtt") -> text
        else -> return null
    }
    val uri = runCatching { URI(absolute) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.userInfo != null) return null
    if (!uri.host.equals("anigato.lol", ignoreCase = true)) return null
    if (uri.rawPath != "/api/sub.vtt") return null
    return absolute
}

private fun anigatoCaptionLanguage(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    CAPTION_NAMES[text.lowercase()]?.let { return it }
    val code = text.lowercase()
    if (code.length !in 2..3) return null
    if (!code.all { it.isLetter() }) return null
    return code
}

private val CAPTION_NAMES = mapOf(
    "english" to "en",
    "japanese" to "ja",
    "spanish" to "es",
    "french" to "fr",
    "german" to "de",
    "portuguese" to "pt",
    "italian" to "it",
    "russian" to "ru",
    "arabic" to "ar",
    "korean" to "ko",
    "chinese" to "zh",
)
