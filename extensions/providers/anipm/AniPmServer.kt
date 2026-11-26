// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import site.anikitty.app.data.skip.SkipTimes
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * On-device **ani.pm** resolve — live origin is https://anipm.pk/
 *
 * Flow:
 * 1. Search `/api/anime/search`
 * 2. Bootstrap `/api/anime/playback-bootstrap/{source}/{id}`
 * 3. Session `/api/anime/settlar/session?provider=anipm`
 * 4. Warm the embed HTML (host-guard cookies), then `/api/embed/session`
 * 5. Play the HLS master on `media.settlar.io`
 *
 * The provider label is always **ani.pm** — never remapped to another site.
 */
class AniPmServer(
    private val cache: ResolveCache? = null,
    private val http: OkHttpClient = defaultClient(),
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "ani.pm",
        description = "On-device ani.pm resolve via anipm.pk. The playlist plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=anipm.pk&sz=128",
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
        warmSite()
        val show = findShow(anilistId, titleHint)
        val lang = if (dub) "dub" else "sub"
        val boot = openBootstrap(show, episode, lang)
        val embedUrl = openSessionEmbed(boot, episode, lang)
        listOf(openPlaylist(embedUrl, boot.skip, dub))
    }

    private fun warmSite() {
        runCatching { get(ORIGIN, siteHeaders(html = true)) }
    }

    private fun findShow(anilistId: Int, titleHint: String?): AninzShow {
        cache?.getShow(ID, anilistId)?.siteId?.takeIf { it.isNotBlank() }?.let { cached ->
            val source = cached.substringBefore(':').ifBlank { "settlar" }
            val id = cached.substringAfter(':', "")
            if (id.isNotBlank()) return AninzShow(id, source, titleHint.orEmpty(), anilistId)
        }
        val title = FlixCrypto.cleanTitle(titleHint)
        if (title.isBlank()) error("ani.pm needs a title to find this show.")
        val body = get("$ORIGIN/api/anime/search?q=${enc(title)}", siteHeaders())
        if (aninzBlocked(body.code, body.text)) error(BLOCKED)
        if (body.code !in 200..299) error(BLOCKED)
        val show = aninzPickShow(body.text, anilistId, title) ?: error(MISSING)
        cache?.putShow(ID, anilistId, "${show.source}:${show.id}", "$ORIGIN/anime/${enc(show.id)}")
        return show
    }

    private fun openBootstrap(show: AninzShow, episode: Int, lang: String): AninzBootstrap {
        val numbered = loadBootstrap(show, episode.toString(), lang) ?: error(MISSING)
        val route = numbered.routeId?.takeIf { it.isNotEmpty() && it != episode.toString() }
            ?: return numbered
        return loadBootstrap(show, route, lang) ?: numbered
    }

    private fun loadBootstrap(show: AninzShow, episodeKey: String, lang: String): AninzBootstrap? {
        val source = if (show.source == "anilist") "anilist" else "settlar"
        val body = get(
            "$ORIGIN/api/anime/playback-bootstrap/$source/${enc(show.id)}?ep=${enc(episodeKey)}&lang=$lang",
            siteHeaders(),
        )
        if (aninzBlocked(body.code, body.text)) error(BLOCKED)
        if (body.code == 404) return null
        if (body.code !in 200..299) error(BLOCKED)
        return aninzBootstrap(body.text, lang)
    }

    /** Returns the full embed URL (with token), not just the token. */
    private fun openSessionEmbed(boot: AninzBootstrap, fallbackEpisode: Int, lang: String): String {
        val episode = if (boot.episode > 0) boot.episode else fallbackEpisode
        val query =
            "selection=${enc(boot.selection)}&provider=$OWN_SESSION&ep=$episode&channel=$lang&telemetry=0"
        val body = get("$ORIGIN/api/anime/settlar/session?$query", siteHeaders())
        if (aninzBlocked(body.code, body.text)) error(BLOCKED)
        if (body.code !in 200..299) error(BLOCKED)
        val embedUrl = anipmEmbedUrl(body.text) ?: error(MISSING)
        // Host-guard + cookies: the embed HTML must load before /api/embed/session.
        val page = get(embedUrl, embedPageHeaders(embedUrl))
        if (aninzBlocked(page.code, page.text)) error(BLOCKED)
        if (page.code !in 200..299) error(BLOCKED)
        return embedUrl
    }

    private fun openPlaylist(embedUrl: String, skip: SkipTimes, dub: Boolean): PlayableSource {
        val token = anipmEmbedToken(embedUrl) ?: error(MISSING)
        val session = get(
            "$EMBED_ORIGIN/api/embed/session?t=${enc(token)}",
            embedApiHeaders(embedUrl),
        )
        if (aninzBlocked(session.code, session.text)) error(BLOCKED)
        if (session.code !in 200..299) error(BLOCKED)
        val pending = aninzPending(session.text) ?: error(MISSING)
        val playlist = get(pending.url, mediaHeaders(embedUrl))
        if (aninzBlocked(playlist.code, playlist.text)) error(BLOCKED)
        if (playlist.code !in 200..299) error(BLOCKED)
        return aninzPlayable(pending, playlist.text, skip, dub, USER_AGENT)?.copy(
            serverName = server.name,
        ) ?: error(MISSING)
    }

    private fun get(url: String, headers: Map<String, String>): HttpText {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = runCatching { http.newCall(builder.build()).execute() }
            .getOrElse { error(BLOCKED) }
        response.use {
            val text = it.body?.string().orEmpty()
            return HttpText(it.code, text)
        }
    }

    private fun siteHeaders(html: Boolean = false): Map<String, String> = mapOf(
        "Accept" to if (html) "text/html,application/xhtml+xml,*/*;q=0.8" else "application/json",
        "Referer" to "$ORIGIN/",
        "Origin" to ORIGIN,
        "User-Agent" to USER_AGENT,
        "Accept-Language" to "en-US,en;q=0.9",
    )

    private fun embedPageHeaders(embedUrl: String): Map<String, String> = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Referer" to "$ORIGIN/",
        "User-Agent" to USER_AGENT,
        "Accept-Language" to "en-US,en;q=0.9",
        "Sec-Fetch-Dest" to "iframe",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "cross-site",
    )

    private fun embedApiHeaders(embedUrl: String): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Referer" to embedUrl,
        "Origin" to EMBED_ORIGIN,
        "User-Agent" to USER_AGENT,
        "Accept-Language" to "en-US,en;q=0.9",
    )

    private fun mediaHeaders(embedUrl: String): Map<String, String> = mapOf(
        "Accept" to "application/vnd.apple.mpegurl,*/*",
        "Referer" to embedUrl,
        "Origin" to EMBED_ORIGIN,
        "User-Agent" to USER_AGENT,
    )

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private data class HttpText(val code: Int, val text: String)

    companion object {
        const val ID = "anipm"
        const val MISSING = "ani.pm does not have this episode."
        const val BLOCKED = "ani.pm could not be reached."
        /** Live host — ani.pm redirects here. */
        const val ORIGIN = "https://anipm.pk"
        const val EMBED_ORIGIN = "https://embed.settlar.io"
        /** Settlar session provider id on anipm.pk. */
        const val OWN_SESSION = "anipm"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

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

/** In-memory cookies so anipm.pk + embed.settlar.io share a warm session. */
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

internal fun anipmEmbedUrl(payload: String): String? {
    val url = runCatching { org.json.JSONObject(payload) }.getOrNull()
        ?.optString("embedUrl")
        ?.trim()
        .orEmpty()
    if (!url.startsWith("${AniPmServer.EMBED_ORIGIN}/")) return null
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
