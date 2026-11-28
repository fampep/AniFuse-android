// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import site.anikitty.app.data.skip.SkipInterval
import site.anikitty.app.data.skip.SkipTimes
import site.anikitty.app.data.skip.malIdFromTitleHint
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * On-device **ani.pm** resolve via the first-party Player API.
 *
 * The old `embed.settlar.io` session path is dead (edge 403). Playback now:
 * 1. Resolve MAL (`mal:` hint, `/api/v1/lookup`, or `/api/v1/titles`)
 * 2. `POST /api/session` `{ malId, episode }` → short-lived token
 * 3. Play `/playlists/{mal}_ep{n}/{sub|dub}.direct.pls?t=…` on the same origin
 *
 * Live hosts are `anipm.pk` and `ani.pm`. The playlist is HLS with PNG
 * byte-range segments; Media3 reads the BYTERANGE tags on this phone.
 */
class AniPmServer(
    private val cache: ResolveCache? = null,
    private val http: OkHttpClient = defaultClient(),
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "ani.pm",
        description = "On-device ani.pm resolve. The first-party playlist plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=ani.pm&sz=128",
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
        val lang = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
        val malId = resolveMal(anilistId, titleHint)
        var lastError = MISSING
        for (origin in ORIGINS) {
            val opened = runCatching {
                openOnOrigin(origin, malId, episode, lang)
            }.getOrElse { err ->
                val msg = err.message?.takeIf { it.isNotBlank() } ?: MISSING
                if (msg == BLOCKED || isHardProviderFailure(msg)) lastError = BLOCKED
                else if (lastError != BLOCKED) lastError = msg
                null
            }
            if (opened != null) {
                cache?.putShow(ID, anilistId, malId.toString(), "$origin/embed/mal/$malId")
                return@withContext namedByServer(listOf(opened), server.name)
            }
        }
        error(lastError)
    }

    private fun resolveMal(anilistId: Int, titleHint: String?): Int {
        cache?.getShow(ID, anilistId)?.siteId?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        malIdFromTitleHint(titleHint).takeIf { it > 0 }?.let { return it }

        var reachable = false
        for (origin in ORIGINS) {
            warm(origin)
            val lookup = get(
                "$origin/api/v1/lookup?anilist=$anilistId",
                jsonHeaders(origin),
            )
            when {
                anipmChallenged(lookup.code, lookup.text) -> {
                    lastBlocked = true
                    continue
                }
                lookup.code in 200..299 -> {
                    reachable = true
                    anipmMalFromLookup(lookup.text, anilistId)?.let { return it }
                }
                lookup.code != 404 -> reachable = true
            }
        }

        val titles = anipmTitles(titleHint)
        if (titles.isEmpty()) {
            error(if (reachable || lastBlocked) MISSING else "ani.pm needs a title or MAL id to find this show.")
        }
        for (origin in ORIGINS) {
            for (title in titles) {
                val body = get(
                    "$origin/api/v1/titles?q=${enc(title)}&limit=10",
                    jsonHeaders(origin),
                )
                if (anipmChallenged(body.code, body.text)) {
                    lastBlocked = true
                    continue
                }
                if (body.code !in 200..299) {
                    if (body.code != 404) reachable = true
                    continue
                }
                reachable = true
                anipmPickTitle(body.text, anilistId, title)?.let { return it }
            }
        }
        error(if (lastBlocked && !reachable) BLOCKED else MISSING)
    }

    @Volatile
    private var lastBlocked: Boolean = false

    private fun openOnOrigin(origin: String, malId: Int, episode: Int, lang: String): PlayableSource {
        warm(origin)
        val embedRef = "$origin/embed/mal/$malId/$episode/$lang"
        // Host-guard: the player document sets same-origin cookies the session expects.
        val page = get(embedRef, htmlHeaders(origin, "$origin/"))
        if (anipmChallenged(page.code, page.text)) error(BLOCKED)
        if (page.code == 404) error(MISSING)
        if (page.code !in 200..299) error(BLOCKED)

        val session = postSession(origin, malId, episode, lang, embedRef)
        val token = session.token
        val mediaUrl = anipmMediaPlaylistUrl(origin, malId, episode, lang, token)
        val playlist = get(mediaUrl, mediaHeaders(origin, embedRef))
        if (anipmChallenged(playlist.code, playlist.text)) error(BLOCKED)
        if (playlist.code == 404) error(MISSING)
        if (playlist.code !in 200..299) error(BLOCKED)
        if (!anipmIsHls(playlist.text)) error(MISSING)

        val skip = loadSkip(origin, malId, episode, lang, embedRef)
        val tracks = loadSubtitles(origin, malId, episode, lang, token, embedRef)
        val fields = BuiltInSourceMeta.aninz(anipmBestVariant(playlist.text), lang)
        return PlayableSource(
            url = mediaUrl,
            headers = mediaHeaders(origin, embedRef),
            label = fields.quality ?: if (lang == "dub") "DUB" else "SUB",
            hls = true,
            quality = fields.quality,
            audio = fields.audio ?: if (lang == "dub") "DUB" else "SUB",
            skip = skip,
            subtitles = tracks,
            audioLanguage = requestedAudioLanguage(lang),
            serverName = server.name,
        )
    }

    private fun postSession(
        origin: String,
        malId: Int,
        episode: Int,
        lang: String,
        referer: String,
    ): AnipmSession {
        AnipmSessionCache.get(origin, malId, episode, lang)?.let { return it }
        val body = JSONObject()
            .put("malId", malId)
            .put("episode", episode)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url("$origin/api/session")
            .post(body)
        sessionHeaders(origin, referer).forEach { (n, v) -> builder.header(n, v) }
        val response = runCatching { http.newCall(builder.build()).execute() }
            .getOrElse { error(BLOCKED) }
        return response.use {
            val text = it.body?.string().orEmpty()
            if (anipmChallenged(it.code, text)) error(BLOCKED)
            if (it.code == 404) error(MISSING)
            if (it.code !in 200..299) {
                val code = runCatching { JSONObject(text).optJSONObject("error")?.optString("code") }
                    .getOrNull()
                if (code.equals("media-unavailable", true) || code.equals("source-absent", true)) {
                    error(MISSING)
                }
                error(BLOCKED)
            }
            val session = anipmParseSession(text) ?: error(MISSING)
            AnipmSessionCache.put(origin, malId, episode, lang, session)
            session
        }
    }

    private fun loadSkip(
        origin: String,
        malId: Int,
        episode: Int,
        lang: String,
        referer: String,
    ): SkipTimes {
        val body = get(
            "$origin/api/skip-times/$malId/$episode?dur=0&lang=$lang",
            jsonHeaders(origin, referer),
        )
        if (body.code !in 200..299 || anipmChallenged(body.code, body.text)) return SkipTimes()
        return anipmSkip(body.text)
    }

    private fun loadSubtitles(
        origin: String,
        malId: Int,
        episode: Int,
        lang: String,
        token: String,
        referer: String,
    ): List<SubtitleTrack> {
        val body = get(
            "$origin/api/subtitles/$malId/$episode?lang=$lang&t=${enc(token)}",
            jsonHeaders(origin, referer),
        )
        if (body.code !in 200..299 || anipmChallenged(body.code, body.text)) return emptyList()
        return anipmSubtitles(body.text, origin, token)
    }

    private fun warm(origin: String) {
        runCatching { get(origin, htmlHeaders(origin, "$origin/")) }
    }

    private fun get(url: String, headers: Map<String, String>): HttpText {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = runCatching { http.newCall(builder.build()).execute() }
            .getOrElse { return HttpText(0, "") }
        return response.use { HttpText(it.code, it.body?.string().orEmpty()) }
    }

    private fun jsonHeaders(origin: String, referer: String = "$origin/"): Map<String, String> =
        linkedMapOf(
            "Accept" to "application/json",
            "Referer" to referer,
            "Origin" to origin,
            "User-Agent" to USER_AGENT,
            "Accept-Language" to "en-US,en;q=0.9",
        )

    private fun htmlHeaders(origin: String, referer: String): Map<String, String> = linkedMapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Referer" to referer,
        "Origin" to origin,
        "User-Agent" to USER_AGENT,
        "Accept-Language" to "en-US,en;q=0.9",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "same-origin",
    )

    private fun sessionHeaders(origin: String, referer: String): Map<String, String> = linkedMapOf(
        "Accept" to "application/json",
        "Content-Type" to "application/json",
        "Referer" to referer,
        "Origin" to origin,
        "User-Agent" to USER_AGENT,
        "Accept-Language" to "en-US,en;q=0.9",
    )

    private fun mediaHeaders(origin: String, referer: String): Map<String, String> = linkedMapOf(
        "Accept" to "application/vnd.apple.mpegurl,*/*;q=0.8",
        "Referer" to referer,
        "Origin" to origin,
        "User-Agent" to USER_AGENT,
        "Accept-Language" to "en-US,en;q=0.9",
    )

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private data class HttpText(val code: Int, val text: String)

    companion object {
        const val ID = "anipm"
        const val MISSING = "ani.pm does not have this episode."
        const val BLOCKED = "ani.pm could not be reached."
        /** Canonical brand / store URL. */
        const val SITE = "https://ani.pm"
        /** Live playback origins — anipm.pk is the CF apex ani.pm often lands on. */
        val ORIGINS = listOf("https://anipm.pk", "https://ani.pm")
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(16, TimeUnit.SECONDS)
                .callTimeout(28, TimeUnit.SECONDS)
                .followRedirects(true)
                .retryOnConnectionFailure(true)
                .cookieJar(MemoryCookieJar())
                .build()
    }
}

internal data class AnipmSession(
    val token: String,
    val expiresAt: Long,
)

/**
 * Process-wide session tokens for ani.pm / anipm.pk / AnimePapa origins.
 * Race retries and HinataTV↔ani.pm handoffs skip a cold `/api/session` RTT
 * when the token is still inside its expiry skew.
 */
internal object AnipmSessionCache {
    private val store = ConcurrentHashMap<String, AnipmSession>()

    fun get(
        origin: String,
        malId: Int,
        episode: Int,
        lang: String,
        nowMs: Long = System.currentTimeMillis(),
    ): AnipmSession? {
        val key = key(origin, malId, episode, lang)
        val hit = store[key] ?: return null
        if (hit.token.isBlank()) {
            store.remove(key, hit)
            return null
        }
        if (hit.expiresAt > 0L && hit.expiresAt <= nowMs + SKEW_MS) {
            store.remove(key, hit)
            return null
        }
        return hit
    }

    fun put(origin: String, malId: Int, episode: Int, lang: String, session: AnipmSession) {
        if (session.token.isBlank()) return
        store[key(origin, malId, episode, lang)] = session
    }

    fun clear() {
        store.clear()
    }

    private fun key(origin: String, malId: Int, episode: Int, lang: String): String {
        val host = origin.trimEnd('/').lowercase()
        val lane = if (lang.equals("dub", ignoreCase = true)) "dub" else "sub"
        return "$host|$malId|$episode|$lane"
    }

    /** Drop tokens this early so Media3 never hits a mid-playback 401. */
    internal const val SKEW_MS = 30_000L
}

/** In-memory cookies so ani.pm / anipm.pk share a warm same-origin session. */
internal class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host.lowercase()
        val bucket = store.getOrPut(host) { ConcurrentHashMap() }
        cookies.forEach { cookie -> bucket[cookie.name] = cookie }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host.lowercase()
        val now = System.currentTimeMillis()
        val out = ArrayList<Cookie>()
        store.forEach { (cookieHost, bucket) ->
            if (!hostEndsWith(host, cookieHost)) return@forEach
            val iter = bucket.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val cookie = entry.value
                if (cookie.expiresAt < now) {
                    iter.remove()
                    continue
                }
                if (cookie.matches(url)) out += cookie
            }
        }
        return out
    }

    private fun hostEndsWith(host: String, cookieHost: String): Boolean =
        host == cookieHost || host.endsWith(".$cookieHost")
}

internal fun anipmChallenged(code: Int, body: String): Boolean {
    if (code == 403 || code == 503) return true
    val probe = body.take(800).lowercase()
    return "just a moment" in probe ||
        "cf-mitigated" in probe ||
        "challenge-platform" in probe ||
        "cf-browser-verification" in probe
}

internal fun anipmTitles(titleHint: String?): List<String> =
    titleHint.orEmpty()
        .split("||")
        .map { part ->
            part.replace(Regex("""\bmal:\d+\b""", RegexOption.IGNORE_CASE), "").trim()
        }
        .filter { it.length >= 2 }
        .distinct()

internal fun anipmMalFromLookup(payload: String, anilistId: Int): Int? {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    val data = root.optJSONArray("data") ?: return null
    for (i in 0 until data.length()) {
        val row = data.optJSONObject(i) ?: continue
        val title = row.optJSONObject("title") ?: row
        val found = row.optBoolean("found", true)
        if (!found) continue
        val listedAnilist = title.optInt("anilistId", 0)
        if (anilistId > 0 && listedAnilist > 0 && listedAnilist != anilistId) continue
        val mal = title.optInt("malId", 0).takeIf { it > 0 }
            ?: row.optInt("malId", 0).takeIf { it > 0 }
        if (mal != null) return mal
    }
    return null
}

internal fun anipmPickTitle(payload: String, anilistId: Int, query: String): Int? {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    val data = root.optJSONArray("data") ?: return null
    val hits = ArrayList<Pair<Int, String>>(data.length())
    for (i in 0 until data.length()) {
        val row = data.optJSONObject(i) ?: continue
        val mal = row.optInt("malId", 0).takeIf { it > 0 } ?: continue
        val title = row.optString("title").trim()
        if (title.isEmpty()) continue
        val listed = row.optInt("anilistId", 0)
        if (anilistId > 0 && listed == anilistId) return mal
        hits += mal to title
    }
    val want = FlixCrypto.cleanTitle(query).lowercase()
    if (want.isBlank()) return hits.firstOrNull()?.first
    hits.firstOrNull { it.second.lowercase() == want }?.let { return it.first }
    return hits.firstOrNull { (mal, title) ->
        val t = title.lowercase()
        t.contains(want) || want.contains(t)
    }?.first
}

internal fun anipmParseSession(payload: String): AnipmSession? {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    val token = root.optString("token").trim()
    if (token.isEmpty()) return null
    return AnipmSession(
        token = token,
        expiresAt = root.optLong("expiresAt", 0L),
    )
}

internal fun anipmMediaPlaylistUrl(
    origin: String,
    malId: Int,
    episode: Int,
    lang: String,
    token: String,
): String {
    val lane = if (lang.equals("dub", ignoreCase = true)) "dub" else "sub"
    // Language media playlist — master.m3u8 children omit the session token.
    return "$origin/playlists/${malId}_ep$episode/$lane.direct.pls?t=${encToken(token)}"
}

private fun encToken(token: String): String =
    URLEncoder.encode(token, Charsets.UTF_8.name()).replace("+", "%20")

internal fun anipmIsHls(body: String): Boolean {
    val head = body.trimStart()
    if (head.startsWith("#EXTM3U")) return true
    val lower = head.take(80).lowercase()
    return "player required" !in lower && head.isNotBlank() && !head.startsWith("{")
}

internal fun anipmBestVariant(playlist: String): String {
    var best = ""
    var bestHeight = -1
    for (line in playlist.lineSequence()) {
        if (!line.contains("RESOLUTION", ignoreCase = true)) continue
        val height = Regex("""RESOLUTION=\d+x(\d+)""", RegexOption.IGNORE_CASE)
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: continue
        if (height > bestHeight) {
            bestHeight = height
            best = line
        }
    }
    // Media playlists have no RESOLUTION — surface a height from BYTERANGE masters only.
    if (best.isNotEmpty()) return best
    // Heuristic: high bitrate media playlists are typically 1080p on this CDN.
    if (playlist.contains("#EXTINF", ignoreCase = true)) return "RESOLUTION=1920x1080"
    return ""
}

internal fun anipmSkip(payload: String): SkipTimes {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return SkipTimes()
    return SkipTimes(
        intro = anipmInterval(root.optJSONObject("op")),
        outro = anipmInterval(root.optJSONObject("ed")),
    )
}

private fun anipmInterval(node: JSONObject?): SkipInterval? {
    if (node == null) return null
    val start = node.optDouble("start", Double.NaN)
    val end = node.optDouble("end", Double.NaN)
    if (!start.isFinite() || !end.isFinite() || start < 0.0 || end <= start) return null
    return SkipInterval(start, end)
}

internal fun anipmSubtitles(payload: String, origin: String, token: String): List<SubtitleTrack> {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return emptyList()
    val rows = root.optJSONArray("tracks") ?: return emptyList()
    val out = ArrayList<SubtitleTrack>(rows.length())
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        val raw = row.optString("url").trim()
        if (raw.isEmpty()) continue
        val absolute = when {
            raw.startsWith("https://", true) || raw.startsWith("http://", true) -> raw
            raw.startsWith("/") -> "$origin$raw"
            else -> "$origin/$raw"
        }
        val withToken = if (absolute.contains("t=")) {
            absolute
        } else {
            absolute + (if (absolute.contains('?')) "&" else "?") + "t=${encToken(token)}"
        }
        val type = row.optString("type").lowercase()
        out += SubtitleTrack(
            url = withToken,
            label = row.optString("label").trim().ifBlank {
                row.optString("lang").trim().ifBlank { "Caption" }
            },
            language = row.optString("lang").trim().ifBlank { null },
            mimeType = when (type) {
                "ass", "ssa" -> "text/x-ssa"
                else -> "text/vtt"
            },
        )
    }
    return out
}

/** Legacy settlar embed URL helper — kept for older unit tests / AniNz parity callers. */
internal fun anipmEmbedUrl(payload: String): String? {
    val url = runCatching { JSONObject(payload) }.getOrNull()
        ?.optString("embedUrl")
        ?.trim()
        .orEmpty()
    if (!url.startsWith("https://embed.settlar.io/")) return null
    return url
}

internal fun anipmEmbedToken(embedUrl: String): String? {
    val query = embedUrl.substringAfter('?', "")
    for (part in query.split('&')) {
        if (part.substringBefore('=') != "t") continue
        val raw = part.substringAfter('=', "")
        val token = runCatching {
            java.net.URLDecoder.decode(raw, Charsets.UTF_8.name())
        }.getOrDefault(raw)
        if (token.isNotBlank()) return token
    }
    return null
}
