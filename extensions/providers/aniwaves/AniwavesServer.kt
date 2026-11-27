// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
 * On-device [aniwaves.tv](https://aniwaves.tv/) resolve.
 *
 * Watch URLs look like
 * `https://aniwaves.tv/watch/kusuriya-no-hitorigoto-3rd-season-82762/ep-1`.
 * AniList stays the catalog. Search is `/filter?keyword=`. The episode opens
 * `/ajax/server/list?servers={id}&eps={n}`, then `/ajax/sources?id=…`. Embeds
 * resolve through `embed-N/getSources` or MegaPlay `stream/getSourcesNew`
 * (AES when the payload is encrypted). Playback stays on this phone.
 */
class AniwavesServer(
    private val cache: ResolveCache? = null,
    private val crypto: MegaPlayCrypto = MegaPlayCrypto(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .cookieJar(MemoryCookieJar())
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
        // Warm the watch page so Cloudflare cookies land before the ajax calls.
        warm(watch)
        val listed = read(
            "$ORIGIN/ajax/server/list?servers=${show.id}&eps=$episode",
            siteHeaders(watch),
        ) ?: error(BLOCKED)
        if (aniwavesBlocked(listed.code, listed.text)) error(BLOCKED)
        if (listed.code !in 200..299) error(if (listed.code == 404) MISSING else BLOCKED)
        val listHtml = runCatching { JSONObject(listed.text).optString("result") }.getOrDefault("")
        val servers = aniwavesServers(listHtml, dub)
        if (servers.isEmpty()) error(MISSING)
        // Open every listed mirror in parallel (cap 3). The player gets the
        // full set so Source failover can hop Vidplay → MegaPlay → … without
        // another resolve round-trip.
        val gate = Semaphore(3)
        var lastError = MISSING
        val opened = coroutineScope {
            servers.map { link ->
                async {
                    gate.withPermit {
                        runCatching { openLink(link, watch, dub) }.getOrElse { err ->
                            lastError = err.message?.takeIf { it.isNotBlank() } ?: lastError
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val unique = opened.distinctBy { it.url }
        if (unique.isEmpty()) error(lastError)
        namedByServer(unique, server.name)
    }

    private fun openLink(link: AniwavesLink, watch: String, dub: Boolean): PlayableSource {
        val encoded = URLEncoder.encode(link.id, Charsets.UTF_8.name())
        val sourced = read(
            "$ORIGIN/ajax/sources?id=$encoded&asi=0&autoPlay=0",
            siteHeaders(watch),
        ) ?: error(BLOCKED)
        if (aniwavesBlocked(sourced.code, sourced.text)) error(BLOCKED)
        if (sourced.code !in 200..299) error(MISSING)
        val embed = aniwavesEmbedUrl(sourced.text) ?: error(MISSING)
        val direct = aniwavesMediaUrl(sourced.text)
        val sourcesPayload: String
        var strip = 0
        if (direct != null) {
            sourcesPayload = sourced.text
        } else {
            val page = read(embed, embedHeaders(embed)) ?: error(BLOCKED)
            if (aniwavesBlocked(page.code, page.text)) error(BLOCKED)
            if (page.code !in 200..299) error(MISSING)
            val dataId = aniwavesDataId(page.text) ?: error(MISSING)
            val origin = aniwavesOrigin(embed) ?: ORIGIN
            val candidates = aniwavesSourcesCandidates(embed, dataId)
            var body: HttpText? = null
            for (url in candidates) {
                val reply = read(url, embedHeaders(embed)) ?: continue
                if (aniwavesBlocked(reply.code, reply.text)) {
                    error(BLOCKED)
                }
                if (reply.code in 200..299) {
                    body = reply
                    break
                }
            }
            val payload = body ?: error(MISSING)
            sourcesPayload = payload.text
            strip = runCatching { crypto.stripBytes(origin) }.getOrDefault(0)
        }
        val media = aniwavesMediaUrl(sourcesPayload, crypto, aniwavesOrigin(embed))
            ?: error(MISSING)
        if (!aniwavesPlayableHost(media)) error(MISSING)
        val playlist = read(media, playbackHeaders(embed)) ?: error(BLOCKED)
        if (aniwavesBlocked(playlist.code, playlist.text)) error(BLOCKED)
        if (playlist.code !in 200..299) error(MISSING)
        return aniwavesPlayable(
            url = media,
            playlist = playlist.text,
            dub = dub,
            tracks = aniwavesTracks(sourcesPayload, crypto, aniwavesOrigin(embed)),
            skip = aniwavesSkip(sourcesPayload).let { fromSources ->
                if (fromSources.isEmpty) aniwavesSkip(sourced.text) else fromSources
            },
            referer = aniwavesOrigin(embed)?.let { "$it/" } ?: "$ORIGIN/",
            origin = aniwavesOrigin(embed) ?: ORIGIN,
            userAgent = USER_AGENT,
            stripBytes = strip,
            serverName = link.name,
        ) ?: error(MISSING)
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

    private fun warm(watch: String) {
        runCatching { read(watch, siteHeaders("$ORIGIN/")) }
    }

    private fun read(url: String, headers: Map<String, String>): HttpText? {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = runCatching { http.newCall(builder.build()).execute() }.getOrNull() ?: return null
        response.use { return HttpText(it.code, it.body?.string().orEmpty()) }
    }

    private fun siteHeaders(referer: String): Map<String, String> = mapOf(
        "Accept" to "application/json, text/html, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Referer" to referer,
        "User-Agent" to USER_AGENT,
        "X-Requested-With" to "XMLHttpRequest",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
    )

    private fun embedHeaders(embed: String): Map<String, String> = mapOf(
        "Accept" to "text/html,application/json,*/*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Referer" to "$ORIGIN/",
        "Origin" to (aniwavesOrigin(embed) ?: ORIGIN),
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
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

internal data class AniwavesShow(val id: String, val slug: String, val title: String)

internal fun aniwavesBlocked(code: Int, body: String): Boolean {
    if (code == 403 || code == 429 || code == 503) return true
    val head = body.take(2_000).lowercase()
    return "just a moment" in head ||
        "cf-browser-verification" in head ||
        "challenge-platform" in head ||
        "cf-mitigated" in head ||
        "performing security verification" in head
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
    if (hits.isNotEmpty()) return hits
    val loose = Regex(
        """href="(/watch/[a-z0-9-]+(?:/ep-\d+)?)"[\s\S]{0,1200}?class="name\s+d-title"[^>]*>([^<]+)<""",
        RegexOption.IGNORE_CASE,
    )
    for (match in loose.findAll(html)) {
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

internal data class AniwavesLink(
    val id: String,
    val name: String,
    val audio: String,
)

/**
 * Prefer the requested audio block. Every `<li data-link-id>` (Vidplay, MegaPlay,
 * …) is kept so resolve can open them in parallel. Opposite-audio links follow
 * only when the preferred block is empty.
 */
internal fun aniwavesServers(html: String, dub: Boolean): List<AniwavesLink> {
    val preferred = if (dub) "dub" else "sub"
    val fallback = if (dub) "sub" else "dub"
    val ordered = ArrayList<AniwavesLink>()
    fun absorb(type: String) {
        val start = Regex("""data-type="$type"""", RegexOption.IGNORE_CASE).find(html) ?: return
        val rest = html.substring(start.range.first)
        val next = Regex("""data-type="(?!$type)""", RegexOption.IGNORE_CASE).find(rest, 1)
        val block = if (next != null) rest.substring(0, next.range.first) else rest
        val seen = HashSet<String>()
        for (match in Regex("""<li\b([^>]*)>([\s\S]*?)</li>""", RegexOption.IGNORE_CASE).findAll(block)) {
            val attrs = match.groupValues[1]
            val id = aniwavesAttr(attrs, "data-link-id") ?: continue
            if (!seen.add(id)) continue
            val name = aniwavesStripHtml(match.groupValues[2]).ifBlank {
                aniwavesAttr(attrs, "title") ?: "Server ${ordered.size + 1}"
            }
            ordered += AniwavesLink(id = id, name = name, audio = type)
        }
        if (seen.isNotEmpty()) return
        for (match in Regex("""data-link-id="([^"]+)"""").findAll(block)) {
            val id = match.groupValues[1].trim()
            if (id.isEmpty() || !seen.add(id)) continue
            ordered += AniwavesLink(id = id, name = "Server ${ordered.size + 1}", audio = type)
        }
    }
    absorb(preferred)
    if (ordered.isEmpty()) absorb(fallback)
    if (ordered.isEmpty()) {
        for (match in Regex("""data-link-id="([^"]+)"""").findAll(html)) {
            val id = match.groupValues[1].trim()
            if (id.isEmpty() || ordered.any { it.id == id }) continue
            ordered += AniwavesLink(id = id, name = "Server ${ordered.size + 1}", audio = preferred)
        }
    }
    return ordered
}

internal fun aniwavesLinkIds(html: String, dub: Boolean): List<String> =
    aniwavesServers(html, dub).map { it.id }

internal fun aniwavesPickLink(html: String, dub: Boolean): String? =
    aniwavesServers(html, dub).firstOrNull()?.id

private fun aniwavesAttr(attrs: String, name: String): String? {
    val match = Regex("""\b${Regex.escape(name)}=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        .find(attrs)
        ?: return null
    return match.groupValues[1].trim().ifEmpty { null }
}

private fun aniwavesStripHtml(raw: String): String =
    raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

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

internal fun aniwavesOrigin(url: String?): String? {
    if (url == null || !url.startsWith("https://")) return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    val port = if (uri.port > 0 && uri.port != 443) ":${uri.port}" else ""
    return "https://$host$port"
}

internal fun aniwavesSourcesUrl(embedUrl: String, dataId: String): String? =
    aniwavesSourcesCandidates(embedUrl, dataId).firstOrNull()

internal fun aniwavesSourcesCandidates(embedUrl: String, dataId: String): List<String> {
    val origin = aniwavesOrigin(embedUrl) ?: return emptyList()
    val path = runCatching { URI(embedUrl).path }.getOrNull().orEmpty()
    val prefix = Regex("""/(embed-\d+)/""").find(path)?.groupValues?.get(1) ?: "embed-1"
    val id = URLEncoder.encode(dataId, Charsets.UTF_8.name())
    val host = runCatching { URI(embedUrl).host }.getOrNull()?.lowercase().orEmpty()
    val out = ArrayList<String>(3)
    if ("megaplay" in host || "kotoplay" in host) {
        out += "$origin/stream/getSourcesNew?id=$id"
    }
    out += "$origin/$prefix/getSources?id=$id"
    out += "$origin/stream/getSourcesNew?id=$id"
    return out.distinct()
}

internal fun aniwavesMediaUrl(
    payload: String,
    crypto: MegaPlayCrypto? = null,
    playerOrigin: String? = null,
): String? {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    aniwavesFile(root.opt("sources"))?.let { return it }
    aniwavesFile(root.opt("file"))?.let { return it }
    crypto?.let { keys ->
        aniwavesEncryptedFile(root, keys, playerOrigin)?.let { return it }
    }
    val result = root.optJSONObject("result") ?: return null
    aniwavesFile(result.opt("sources"))?.let { return it }
    aniwavesFile(result.opt("url"))?.let { return it }
    aniwavesFile(result.opt("file"))?.let { return it }
    crypto?.let { keys ->
        aniwavesEncryptedFile(result, keys, playerOrigin)?.let { return it }
    }
    return null
}

internal fun aniwavesTracks(
    payload: String,
    crypto: MegaPlayCrypto? = null,
    playerOrigin: String? = null,
): List<SubtitleTrack> {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return emptyList()
    fun from(rows: JSONArray?): List<SubtitleTrack> {
        if (rows == null) return emptyList()
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
                mimeType = if (url.substringBefore('?').endsWith(".srt", true)) {
                    "application/x-subrip"
                } else {
                    "text/vtt"
                },
            )
        }
        return tracks
    }
    from(root.optJSONArray("tracks")).takeIf { it.isNotEmpty() }?.let { return it }
    from(root.optJSONObject("result")?.optJSONArray("tracks")).takeIf { it.isNotEmpty() }?.let { return it }
    if (crypto != null) {
        val enc = root.optString("sources").ifBlank { root.optString("enc") }
        if (enc.isNotBlank() && !enc.startsWith("http") && !enc.trimStart().startsWith("{")) {
            val origin = playerOrigin ?: return emptyList()
            val decrypted = runCatching { crypto.decryptEnc(enc, origin) }.getOrNull() ?: return emptyList()
            return from(decrypted.optJSONArray("tracks"))
        }
    }
    return emptyList()
}

internal fun aniwavesSkip(payload: String): SkipTimes {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return SkipTimes()
    val skip = root.optJSONObject("skip_data")
        ?: root.optJSONObject("skip")
        ?: root.optJSONObject("result")?.optJSONObject("skip_data")
        ?: root
    return SkipTimes(
        intro = aniwavesInterval(skip, "intro"),
        outro = aniwavesInterval(skip, "outro"),
    )
}

internal fun aniwavesPlayableHost(url: String): Boolean {
    if (!url.startsWith("https://")) return false
    val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
    return host != "aniwaves.tv" &&
        host != "www.aniwaves.tv" &&
        !host.endsWith(".aniwaves.tv")
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
    stripBytes: Int = 0,
    serverName: String? = null,
): PlayableSource? {
    if (!playlist.trimStart().startsWith("#EXTM3U")) return null
    if (!aniwavesPlayableHost(url)) return null
    val mode = if (dub) "dub" else "sub"
    val fields = BuiltInSourceMeta.aniwaves(aniwavesBestVariant(playlist), mode)
    val mirror = serverName?.trim()?.takeIf { it.isNotEmpty() }
    return PlayableSource(
        url = url,
        headers = mapOf(
            "Referer" to referer,
            "Origin" to origin,
            "User-Agent" to userAgent,
        ),
        label = listOfNotNull(mirror, fields.quality ?: if (dub) "DUB" else "SUB")
            .joinToString(" · "),
        stripBytes = stripBytes,
        subtitles = tracks,
        quality = fields.quality,
        audio = fields.audio,
        hls = true,
        skip = skip,
        audioLanguage = requestedAudioLanguage(mode),
        serverName = mirror,
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
            if (url.startsWith("https://") && aniwavesPlayableHost(url) && ".m3u8" in url.lowercase()) {
                return url
            }
        }
        is JSONArray -> {
            for (i in 0 until value.length()) {
                val row = value.optJSONObject(i) ?: continue
                val file = row.optString("file").ifBlank { row.optString("url") }.trim()
                val hls = ".m3u8" in file.lowercase() || row.optString("type").contains("hls", true)
                if (file.startsWith("https://") && aniwavesPlayableHost(file) && hls) return file
            }
        }
        is JSONObject -> {
            val file = value.optString("file").ifBlank { value.optString("url") }.trim()
            if (file.startsWith("https://") && aniwavesPlayableHost(file) && ".m3u8" in file.lowercase()) {
                return file
            }
        }
    }
    return null
}

private fun aniwavesEncryptedFile(
    root: JSONObject,
    crypto: MegaPlayCrypto,
    playerOrigin: String?,
): String? {
    val enc = root.optString("sources").ifBlank { root.optString("enc") }.trim()
    if (enc.isBlank() || enc.startsWith("http") || enc.trimStart().startsWith("{") || enc.trimStart().startsWith("[")) {
        return null
    }
    val origin = playerOrigin ?: return null
    val decrypted = runCatching { crypto.decryptEnc(enc, origin) }.getOrNull() ?: return null
    aniwavesFile(decrypted.opt("sources"))?.let { return it }
    aniwavesFile(decrypted.opt("file"))?.let { return it }
    aniwavesFile(decrypted.optJSONObject("sources"))?.let { return it }
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
