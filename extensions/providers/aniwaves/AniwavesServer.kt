// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import site.anikitty.app.data.skip.SkipInterval
import site.anikitty.app.data.skip.SkipTimes
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * On-device aniwaves.tv resolve.
 *
 * AniList stays the catalog. Search is the site filter. A card matches when
 * its title is the show, or is the shortest title that still contains that
 * name. The episode is `ajax/server/list` for that show id, then the embed
 * `getSources` playlist. Sub or dub follows the server block. Playback stays
 * on this phone.
 */
class AniwavesServer(
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
        name = "aniwaves",
        description = "On-device aniwaves.tv resolve. The playlist plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=aniwaves.tv&sz=128",
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
        val show = findShow(anilistId, titleHint)
        val watch = "$ORIGIN/watch/${show.slug}/ep-$episode"
        val listed = read(
            "$ORIGIN/ajax/server/list?servers=${show.id}&eps=$episode",
            siteHeaders(watch),
        ) ?: error(BLOCKED)
        if (aniwavesBlocked(listed.code, listed.text)) error(BLOCKED)
        val listHtml = runCatching { JSONObject(listed.text).optString("result") }.getOrDefault("")
        val linkId = aniwavesPickLink(listHtml, dub) ?: error(MISSING)
        val encoded = URLEncoder.encode(linkId, Charsets.UTF_8.name())
        val sourced = read(
            "$ORIGIN/ajax/sources?id=$encoded&asi=0&autoPlay=0",
            siteHeaders(watch),
        ) ?: error(BLOCKED)
        if (aniwavesBlocked(sourced.code, sourced.text)) error(BLOCKED)
        val embed = aniwavesEmbedUrl(sourced.text) ?: error(MISSING)
        val direct = aniwavesMediaUrl(sourced.text)
        val sourcesPayload = if (direct != null) {
            sourced.text
        } else {
            val page = read(embed, embedHeaders(embed)) ?: error(BLOCKED)
            if (aniwavesBlocked(page.code, page.text)) error(BLOCKED)
            val dataId = aniwavesDataId(page.text) ?: error(MISSING)
            val sourcesUrl = aniwavesSourcesUrl(embed, dataId) ?: error(MISSING)
            val body = read(sourcesUrl, embedHeaders(embed)) ?: error(BLOCKED)
            if (aniwavesBlocked(body.code, body.text)) error(BLOCKED)
            body.text
        }
        val media = aniwavesMediaUrl(sourcesPayload) ?: error(MISSING)
        if (!aniwavesPlayableHost(media)) error(MISSING)
        val playlist = read(media, playbackHeaders(embed)) ?: error(BLOCKED)
        if (aniwavesBlocked(playlist.code, playlist.text)) error(BLOCKED)
        listOf(
            aniwavesPlayable(
                url = media,
                playlist = playlist.text,
                dub = dub,
                tracks = aniwavesTracks(sourcesPayload),
                skip = aniwavesSkip(sourcesPayload).let { fromSources ->
                    if (fromSources.isEmpty) aniwavesSkip(sourced.text) else fromSources
                },
                referer = aniwavesOrigin(embed)?.let { "$it/" } ?: "$ORIGIN/",
                origin = aniwavesOrigin(embed) ?: ORIGIN,
                userAgent = USER_AGENT,
            ) ?: error(MISSING),
        )
    }

    private fun findShow(anilistId: Int, titleHint: String?): AniwavesShow {
        cache?.getShow(ID, anilistId)?.let { saved ->
            val cached = aniwavesCachedShow(saved.siteId, saved.watchUrl) ?: return@let
            return cached.copy(title = titleHint.orEmpty())
        }
        val title = FlixCrypto.cleanTitle(titleHint)
        if (title.isBlank()) error("aniwaves needs a title to find this show.")
        val query = URLEncoder.encode(title, Charsets.UTF_8.name())
        val page = read("$ORIGIN/filter?keyword=$query", siteHeaders("$ORIGIN/")) ?: error(BLOCKED)
        if (aniwavesBlocked(page.code, page.text)) error(BLOCKED)
        val show = aniwavesPickShow(aniwavesSearchHits(page.text), title) ?: error(MISSING)
        cache?.putShow(ID, anilistId, show.slug, "$ORIGIN/watch/${show.slug}")
        return show
    }

    private fun read(url: String, headers: Map<String, String>): HttpText? {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = runCatching { http.newCall(builder.build()).execute() }.getOrNull() ?: return null
        response.use { return HttpText(it.code, it.body?.string().orEmpty()) }
    }

    private fun siteHeaders(referer: String): Map<String, String> = mapOf(
        "Accept" to "application/json, text/html, */*",
        "Referer" to referer,
        "User-Agent" to USER_AGENT,
        "X-Requested-With" to "XMLHttpRequest",
    )

    private fun embedHeaders(embed: String): Map<String, String> = mapOf(
        "Accept" to "text/html,application/json,*/*",
        "Referer" to embed,
        "User-Agent" to USER_AGENT,
    )

    private fun playbackHeaders(embed: String): Map<String, String> {
        val origin = aniwavesOrigin(embed) ?: ORIGIN
        return mapOf(
            "Accept" to "application/vnd.apple.mpegurl,application/x-mpegURL,*/*",
            "Referer" to "$origin/",
            "Origin" to origin,
            "User-Agent" to USER_AGENT,
        )
    }

    private data class HttpText(val code: Int, val text: String)

    companion object {
        const val ID = "aniwaves"
        const val MISSING = "aniwaves does not have this episode."
        const val BLOCKED = "aniwaves could not be reached."
        const val ORIGIN = "https://aniwaves.tv"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}

internal data class AniwavesShow(val id: String, val slug: String, val title: String)

internal fun aniwavesBlocked(code: Int, body: String): Boolean {
    if (code == 403 || code == 429 || code == 503) return true
    val head = body.take(2_000).lowercase()
    return "just a moment" in head ||
        "cf-browser-verification" in head ||
        "challenge-platform" in head ||
        "cf-mitigated" in head
}

internal fun aniwavesSearchHits(html: String): List<AniwavesShow> {
    val card = Regex(
        """<a\s+href="(/watch/[^"#?]+)"[\s\S]{0,2500}?<a\s+class="name\s+d-title"[^>]*>([^<]+)</a>""",
        RegexOption.IGNORE_CASE,
    )
    val hits = ArrayList<AniwavesShow>()
    for (match in card.findAll(html)) {
        val href = match.groupValues[1].trim()
        val id = aniwavesShowId(href) ?: continue
        val slug = href.substringAfter("/watch/").substringBefore('/').trim()
        val title = match.groupValues[2].trim()
        if (title.isEmpty() || hits.any { it.id == id }) continue
        hits += AniwavesShow(id, slug, title)
    }
    return hits
}

internal fun aniwavesShowId(href: String): String? {
    val path = href.substringBefore('?').trimEnd('/')
    val slug = Regex("""/watch/([^/]+)""").find(path)?.groupValues?.get(1)
        ?: path.substringAfterLast('/')
    val id = Regex("""(\d+)$""").find(slug)?.groupValues?.get(1) ?: return null
    if (!slug.contains('-') || slug.length <= id.length) return null
    return id
}

internal fun aniwavesPickShow(hits: List<AniwavesShow>, titleHint: String?): AniwavesShow? {
    val want = aniwavesNormalize(titleHint)
    if (want.isBlank()) return null
    hits.firstOrNull { aniwavesNormalize(it.title) == want }?.let { return it }
    val words = want.split(' ').filter { it.length >= 3 || it.any(Char::isDigit) }
    if (words.isEmpty()) return null
    return hits.filter { show ->
        val name = aniwavesNormalize(show.title).split(' ').filter { it.isNotBlank() }
        words.all { it in name }
    }.minByOrNull { show ->
        aniwavesNormalize(show.title).split(' ').count { it.isNotBlank() }
    }
}

internal fun aniwavesCachedShow(siteId: String, watchUrl: String): AniwavesShow? {
    val slug = watchUrl.substringAfter("/watch/", "").substringBefore('/').substringBefore('?')
    val id = aniwavesShowId("/watch/$slug")
        ?: aniwavesShowId("/watch/$siteId")
        ?: return null
    if (slug.isBlank()) return null
    return AniwavesShow(id, slug, "")
}

internal fun aniwavesPickLink(html: String, dub: Boolean): String? {
    val type = if (dub) "dub" else "sub"
    val block = Regex(
        """<div[^>]*data-type="$type"[^>]*>([\s\S]*?)</div>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.get(1) ?: return null
    return Regex("""data-link-id="([^"]+)"""").find(block)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
}

internal fun aniwavesEmbedUrl(payload: String): String? {
    val url = runCatching { JSONObject(payload) }.getOrNull()
        ?.optJSONObject("result")
        ?.optString("url")
        ?.trim()
        .orEmpty()
    if (!url.startsWith("https://")) return null
    return url
}

internal fun aniwavesDataId(html: String): String? =
    Regex("""data-id="([^"]+)"""").find(html)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

internal fun aniwavesOrigin(url: String): String? {
    if (!url.startsWith("https://")) return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    val port = if (uri.port > 0 && uri.port != 443) ":${uri.port}" else ""
    return "https://$host$port"
}

internal fun aniwavesSourcesUrl(embedUrl: String, dataId: String): String? {
    val origin = aniwavesOrigin(embedUrl) ?: return null
    val path = runCatching { URI(embedUrl).path }.getOrNull().orEmpty()
    val prefix = Regex("""/(embed-\d+)/""").find(path)?.groupValues?.get(1) ?: "embed-1"
    val id = URLEncoder.encode(dataId, Charsets.UTF_8.name())
    return "$origin/$prefix/getSources?id=$id"
}

internal fun aniwavesMediaUrl(payload: String): String? {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    aniwavesFile(root.opt("sources"))?.let { return it }
    val result = root.optJSONObject("result") ?: return null
    return aniwavesFile(result.opt("sources")) ?: aniwavesFile(result.opt("url"))
}

internal fun aniwavesTracks(payload: String): List<SubtitleTrack> {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return emptyList()
    val rows = root.optJSONArray("tracks")
        ?: root.optJSONObject("result")?.optJSONArray("tracks")
        ?: return emptyList()
    val tracks = ArrayList<SubtitleTrack>()
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        val url = row.optString("file").ifBlank { row.optString("url") }.trim()
        if (!url.startsWith("https://")) continue
        val kind = row.optString("kind")
        val caption = kind.equals("captions", true) ||
            kind.equals("subtitles", true) ||
            url.substringBefore('?').endsWith(".vtt", true) ||
            url.substringBefore('?').endsWith(".srt", true)
        if (!caption) continue
        val label = row.optString("label").trim().ifBlank { row.optString("lang").trim() }
        if (label.isEmpty()) continue
        tracks += SubtitleTrack(
            url = url,
            label = label,
            language = row.optString("lang").trim().ifBlank { null },
            mimeType = if (url.substringBefore('?').endsWith(".srt", true)) "application/x-subrip" else "text/vtt",
        )
    }
    return tracks
}

internal fun aniwavesSkip(payload: String): SkipTimes {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return SkipTimes()
    val skip = root.optJSONObject("skip_data")
        ?: root.optJSONObject("skip")
        ?: root.optJSONObject("result")?.optJSONObject("skip_data")
        ?: return SkipTimes()
    return SkipTimes(
        intro = aniwavesInterval(skip, "intro"),
        outro = aniwavesInterval(skip, "outro"),
    )
}

internal fun aniwavesPlayableHost(url: String): Boolean {
    if (!url.startsWith("https://")) return false
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
    return host != "aniwaves.tv" && host != "www.aniwaves.tv"
}

internal fun aniwavesPlayable(
    url: String,
    playlist: String,
    dub: Boolean,
    tracks: List<SubtitleTrack>,
    skip: SkipTimes,
    referer: String,
    origin: String,
    userAgent: String,
): PlayableSource? {
    if (!playlist.trimStart().startsWith("#EXTM3U")) return null
    if (!aniwavesPlayableHost(url)) return null
    val mode = if (dub) "dub" else "sub"
    val fields = BuiltInSourceMeta.aniwaves(aniwavesBestVariant(playlist), mode)
    return PlayableSource(
        url = url,
        headers = mapOf(
            "Referer" to referer,
            "Origin" to origin,
            "User-Agent" to userAgent,
        ),
        label = fields.quality ?: if (dub) "DUB" else "SUB",
        stripBytes = 0,
        subtitles = tracks,
        quality = fields.quality,
        audio = fields.audio,
        hls = true,
        skip = skip,
        audioLanguage = requestedAudioLanguage(mode),
    )
}

internal fun aniwavesBestVariant(playlist: String): String {
    var best = ""
    var bestHeight = -1
    for (line in playlist.lineSequence()) {
        val trimmed = line.trim()
        if (!trimmed.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue
        val height = Regex("""RESOLUTION=\d+x(\d+)""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (height >= bestHeight) {
            bestHeight = height
            best = trimmed
        }
    }
    return best
}

private fun aniwavesNormalize(raw: String?): String {
    val cleaned = FlixCrypto.cleanTitle(raw)
    if (cleaned.isBlank()) return ""
    return cleaned.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}

private fun aniwavesFile(value: Any?): String? {
    when (value) {
        is String -> {
            val url = value.trim()
            if (url.startsWith("https://") && aniwavesPlayableHost(url) && ".m3u8" in url.lowercase()) return url
        }
        is JSONArray -> {
            for (i in 0 until value.length()) {
                val row = value.optJSONObject(i) ?: continue
                val file = row.optString("file").ifBlank { row.optString("url") }.trim()
                val hls = ".m3u8" in file.lowercase() || row.optString("type").contains("hls", true)
                if (file.startsWith("https://") && aniwavesPlayableHost(file) && hls) return file
            }
        }
    }
    return null
}

private fun aniwavesInterval(root: JSONObject, name: String): SkipInterval? {
    val array = root.optJSONArray(name)
    if (array != null && array.length() >= 2) {
        val start = array.optDouble(0, -1.0)
        val end = array.optDouble(1, -1.0)
        if (start >= 0 && end > start) return SkipInterval(start, end)
    }
    val obj = root.optJSONObject(name) ?: return null
    val start = obj.optDouble("start", -1.0)
    val end = obj.optDouble("end", -1.0)
    if (start >= 0 && end > start) return SkipInterval(start, end)
    return null
}
