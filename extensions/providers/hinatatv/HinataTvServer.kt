// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import site.anikitty.app.data.skip.SkipTimes
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * On-device [HinataTV](https://hinatatv.site/) resolve.
 *
 * The watch page is AniList-keyed (`/watch/{anilistId}`). Playback embeds
 * AnimePapa, which redirects `/embed/ani/{anilistId}/{ep}/{lang}` →
 * `/embed/mal/{malId}/…`, then issues a short-lived session token for the
 * PNG byte-range HLS playlist — same wire format Media3 already plays for
 * ani.pm. Sub and dub when AnimePapa has both lanes.
 */
class HinataTvServer(
    private val cache: ResolveCache? = null,
    private val http: OkHttpClient = defaultClient(),
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "HinataTV",
        description = "On-device hinatatv.site resolve. AnimePapa playlists play on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=hinatatv.site&sz=128",
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
        if (!episodeAvailable(anilistId, episode)) error(MISSING)
        val malId = resolveMal(anilistId, episode, lang)
        val opened = openPapa(malId, episode, lang)
        cache?.putShow(ID, anilistId, malId.toString(), "$PAPA/embed/mal/$malId")
        namedByServer(listOf(opened), server.name)
    }

    /** Soft availability from HinataTV — miss means hard missing; API failure continues. */
    private fun episodeAvailable(anilistId: Int, episode: Int): Boolean {
        val body = get(
            "$SITE/api/watch/availability?anime=$anilistId",
            jsonHeaders(SITE, "$SITE/watch/$anilistId"),
        )
        if (body.code !in 200..299) return true
        return hinataAvailabilityHasEpisode(body.text, episode)
    }

    private fun resolveMal(anilistId: Int, episode: Int, lang: String): Int {
        cache?.getShow(ID, anilistId)?.siteId?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        // Race already warmed MAL via ani.pm — skip Papa ani→mal RTT.
        cache?.getShow(AniPmServer.ID, anilistId)?.siteId?.toIntOrNull()?.takeIf { it > 0 }?.let {
            return it
        }
        val aniEmbed = hinataPapaAniEmbedUrl(anilistId, episode, lang)
        val headers = htmlHeaders(PAPA, "$SITE/watch/$anilistId")
        val looked = resolvePapaMalEmbed(aniEmbed, headers)
        return looked.malId ?: error(looked.failure)
    }

    private fun openPapa(malId: Int, episode: Int, lang: String): PlayableSource {
        val embedRef = "$PAPA/embed/mal/$malId/$episode/$lang"
        val page = get(embedRef, htmlHeaders(PAPA, "$SITE/"))
        if (hinataChallenged(page.code, page.text)) error(BLOCKED)
        if (page.code == 404) error(MISSING)
        if (page.code !in 200..299) error(BLOCKED)

        val session = postSession(malId, episode, lang, embedRef)
        val mediaUrl = anipmMediaPlaylistUrl(PAPA, malId, episode, lang, session.token)
        val playlist = get(mediaUrl, mediaHeaders(PAPA, embedRef))
        if (hinataChallenged(playlist.code, playlist.text)) error(BLOCKED)
        if (playlist.code == 404) error(MISSING)
        if (playlist.code !in 200..299) error(BLOCKED)
        if (!anipmIsHls(playlist.text)) error(MISSING)

        val skip = loadSkip(malId, episode, lang, embedRef)
        val tracks = loadSubtitles(malId, episode, lang, session.token, embedRef)
        val fields = BuiltInSourceMeta.aninz(anipmBestVariant(playlist.text), lang)
        return PlayableSource(
            url = mediaUrl,
            headers = mediaHeaders(PAPA, embedRef),
            label = fields.quality ?: if (lang == "dub") "DUB" else "SUB",
            // Papa PNG HLS: BYTERANGE + single-byte XOR — not MegaPlay strip.
            stripBytes = 0,
            pngXor = true,
            hls = true,
            quality = fields.quality,
            audio = fields.audio ?: if (lang == "dub") "DUB" else "SUB",
            skip = skip,
            subtitles = tracks,
            audioLanguage = requestedAudioLanguage(lang),
            serverName = server.name,
        )
    }

    private fun postSession(malId: Int, episode: Int, lang: String, referer: String): AnipmSession {
        AnipmSessionCache.get(PAPA, malId, episode, lang)?.let { return it }
        val body = JSONObject()
            .put("malId", malId)
            .put("episode", episode)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url("$PAPA/api/session")
            .post(body)
        sessionHeaders(PAPA, referer).forEach { (n, v) -> builder.header(n, v) }
        val response = runCatching { http.newCall(builder.build()).execute() }
            .getOrElse { error(BLOCKED) }
        return response.use {
            val text = it.body?.string().orEmpty()
            if (hinataChallenged(it.code, text)) error(BLOCKED)
            if (it.code == 404) error(MISSING)
            if (it.code !in 200..299) {
                val code = runCatching { JSONObject(text).optJSONObject("error")?.optString("code") }
                    .getOrNull()
                if (code.equals("media-unavailable", true) ||
                    code.equals("source-absent", true) ||
                    code.equals("episode-not-found", true) ||
                    code.equals("title-not-found", true)
                ) {
                    error(MISSING)
                }
                error(BLOCKED)
            }
            val session = anipmParseSession(text) ?: error(MISSING)
            AnipmSessionCache.put(PAPA, malId, episode, lang, session)
            session
        }
    }

    /**
     * Follow Papa ani→mal redirects hop-by-hop. Intermediate Location values
     * that are not yet `/embed/mal/` keep walking; outages/challenges surface
     * as [BLOCKED] so ServerCatalog soft-blocks the provider.
     */
    private fun resolvePapaMalEmbed(
        startUrl: String,
        headers: Map<String, String>,
    ): HinataMalLookup {
        var url = startUrl
        var reached = false
        for (hop in 0 until MAX_REDIRECT_HOPS) {
            val hopResult = headHop(url, headers)
            if (!hopResult.networkOk) {
                return HinataMalLookup(
                    malId = null,
                    failure = hinataMalLookupFailure(reached = reached, challenged = false),
                )
            }
            reached = true
            if (hinataChallenged(hopResult.code, hopResult.body)) {
                return HinataMalLookup(malId = null, failure = BLOCKED)
            }
            val loc = hopResult.location?.let { hinataAbsolutizePapaUrl(it, PAPA) }
            if (loc != null) {
                hinataMalFromPapaEmbedUrl(loc)?.let {
                    return HinataMalLookup(malId = it, failure = MISSING)
                }
                url = loc
                continue
            }
            if (hopResult.code == 404) {
                return HinataMalLookup(malId = null, failure = MISSING)
            }
            break
        }
        val finalHop = followFinal(url, headers)
        if (!finalHop.networkOk) {
            return HinataMalLookup(
                malId = null,
                failure = hinataMalLookupFailure(reached = reached, challenged = false),
            )
        }
        if (hinataChallenged(finalHop.code, finalHop.body)) {
            return HinataMalLookup(malId = null, failure = BLOCKED)
        }
        if (finalHop.code == 404) {
            return HinataMalLookup(malId = null, failure = MISSING)
        }
        hinataMalFromPapaEmbedUrl(finalHop.url)?.let {
            return HinataMalLookup(malId = it, failure = MISSING)
        }
        return HinataMalLookup(malId = null, failure = MISSING)
    }

    private fun headHop(url: String, headers: Map<String, String>): HeadHop {
        val client = http.newBuilder().followRedirects(false).build()
        val builder = Request.Builder().url(url).head()
        headers.forEach { (n, v) -> builder.header(n, v) }
        val response = runCatching { client.newCall(builder.build()).execute() }
            .getOrElse { return HeadHop(networkOk = false, code = 0, location = null, body = "") }
        return response.use {
            HeadHop(
                networkOk = true,
                code = it.code,
                location = it.header("Location")?.trim()?.takeIf { loc -> loc.isNotEmpty() },
                body = "",
            )
        }
    }

    private data class HeadHop(
        val networkOk: Boolean,
        val code: Int,
        val location: String?,
        val body: String,
    )

    private data class HinataMalLookup(val malId: Int?, val failure: String)

    private fun loadSkip(malId: Int, episode: Int, lang: String, referer: String): SkipTimes {
        val body = get(
            "$PAPA/api/skip-times/$malId/$episode?dur=0&lang=$lang",
            jsonHeaders(PAPA, referer),
        )
        if (body.code !in 200..299 || hinataChallenged(body.code, body.text)) return SkipTimes()
        return anipmSkip(body.text)
    }

    private fun loadSubtitles(
        malId: Int,
        episode: Int,
        lang: String,
        token: String,
        referer: String,
    ): List<SubtitleTrack> {
        val body = get(
            "$PAPA/api/subtitles/$malId/$episode?lang=$lang&t=${enc(token)}",
            jsonHeaders(PAPA, referer),
        )
        if (body.code !in 200..299 || hinataChallenged(body.code, body.text)) return emptyList()
        return anipmSubtitles(body.text, PAPA, token)
    }

    /** Follow redirects; keep status/body so challenges classify as BLOCKED. */
    private fun followFinal(url: String, headers: Map<String, String>): FinalHop {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (n, v) -> builder.header(n, v) }
        val response = runCatching { http.newCall(builder.build()).execute() }
            .getOrElse {
                return FinalHop(networkOk = false, code = 0, url = null, body = "")
            }
        return response.use {
            FinalHop(
                networkOk = true,
                code = it.code,
                url = it.request.url.toString(),
                body = it.body?.string().orEmpty().take(800),
            )
        }
    }

    private data class FinalHop(
        val networkOk: Boolean,
        val code: Int,
        val url: String?,
        val body: String,
    )

    private fun get(url: String, headers: Map<String, String>): HttpText {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = runCatching { http.newCall(builder.build()).execute() }
            .getOrElse { return HttpText(0, "") }
        return response.use { HttpText(it.code, it.body?.string().orEmpty()) }
    }

    private fun jsonHeaders(origin: String, referer: String): Map<String, String> = linkedMapOf(
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
        const val ID = "hinatatv"
        const val SITE = "https://hinatatv.site"
        /** Same AnimePapa Player API host ani.pm races — shared session cache. */
        const val PAPA = AniPmServer.PAPA
        const val MISSING = "HinataTV does not have this episode."
        const val BLOCKED = "HinataTV could not be reached."
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val MAX_REDIRECT_HOPS = 6

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

/** AnimePapa AniList embed that HinataTV iframes. */
internal fun hinataPapaAniEmbedUrl(anilistId: Int, episode: Int, lang: String): String {
    val lane = if (lang.equals("dub", ignoreCase = true)) "dub" else "sub"
    return "${HinataTvServer.PAPA}/embed/ani/$anilistId/$episode/$lane"
}

/** Parse MAL id from `/embed/mal/{mal}/…` (absolute or relative). */
internal fun hinataMalFromPapaEmbedUrl(url: String?): Int? {
    if (url.isNullOrBlank()) return null
    val match = Regex(
        """/embed/mal/([1-9][0-9]*)/(?:[1-9][0-9]*)(?:/(?:sub|dub))?/?""",
        RegexOption.IGNORE_CASE,
    ).find(url) ?: return null
    return match.groupValues[1].toIntOrNull()
}

/** True when HinataTV lists [episode] as available (or payload is empty/unknown). */
internal fun hinataAvailabilityHasEpisode(payload: String, episode: Int): Boolean {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return true
    val rows = root.optJSONArray("episodes") ?: return true
    if (rows.length() == 0) return false
    for (i in 0 until rows.length()) {
        if (rows.optInt(i, -1) == episode) return true
    }
    return false
}

internal fun hinataChallenged(code: Int, body: String): Boolean {
    if (code == 403 || code == 503) return true
    val probe = body.take(800).lowercase()
    return "just a moment" in probe ||
        "cf-mitigated" in probe ||
        "challenge-platform" in probe ||
        "cf-browser-verification" in probe
}

/** Absolute Papa URL from a Location header (relative or absolute). */
internal fun hinataAbsolutizePapaUrl(location: String, origin: String = HinataTvServer.PAPA): String {
    val loc = location.trim()
    return when {
        loc.startsWith("http", ignoreCase = true) -> loc
        loc.startsWith("/") -> "${origin.trimEnd('/')}$loc"
        else -> "${origin.trimEnd('/')}/$loc"
    }
}

/**
 * Soft miss vs hard down for Papa ani→mal lookup.
 * Never-reached / challenge → [HinataTvServer.BLOCKED]; reached without MAL → MISSING.
 */
internal fun hinataMalLookupFailure(reached: Boolean, challenged: Boolean): String = when {
    challenged -> HinataTvServer.BLOCKED
    !reached -> HinataTvServer.BLOCKED
    else -> HinataTvServer.MISSING
}
