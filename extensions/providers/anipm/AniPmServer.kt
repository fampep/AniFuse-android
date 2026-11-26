// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import site.anikitty.app.data.skip.SkipTimes
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * On-device ani.pm resolve — live origin is https://anipm.pk/
 * (ani.pm redirects there; same settlar bootstrap + embed pipeline as ani.nz).
 *
 * Search is `GET /api/anime/search`. Playback is
 * `GET /api/anime/playback-bootstrap/{source}/{id}` then
 * `/api/anime/settlar/session?provider=anipm`, then the HLS master on
 * `media.settlar.io`. The provider label stays **ani.pm**.
 */
class AniPmServer(
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
        val show = findShow(anilistId, titleHint)
        val lang = if (dub) "dub" else "sub"
        val boot = openBootstrap(show, episode, lang)
        val token = openSession(boot, episode, lang)
        listOf(openPlaylist(token, boot.skip, dub))
    }

    private fun findShow(anilistId: Int, titleHint: String?): AninzShow {
        cache?.getShow(ID, anilistId)?.siteId?.takeIf { it.isNotBlank() }?.let { cached ->
            val source = cached.substringBefore(':').ifBlank { "settlar" }
            val id = cached.substringAfter(':', "")
            if (id.isNotBlank()) return AninzShow(id, source, titleHint.orEmpty(), anilistId)
        }
        val title = FlixCrypto.cleanTitle(titleHint)
        if (title.isBlank()) error("ani.pm needs a title to find this show.")
        val body = get(
            "$ORIGIN/api/anime/search?q=${enc(title)}",
            siteHeaders(),
        )
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

    private fun openSession(boot: AninzBootstrap, fallbackEpisode: Int, lang: String): String {
        val episode = if (boot.episode > 0) boot.episode else fallbackEpisode
        val query = "selection=${enc(boot.selection)}&provider=$OWN_SESSION&ep=$episode&channel=$lang&telemetry=0"
        val body = get("$ORIGIN/api/anime/settlar/session?$query", siteHeaders())
        if (aninzBlocked(body.code, body.text)) error(BLOCKED)
        if (body.code !in 200..299) error(BLOCKED)
        return aninzEmbedToken(body.text) ?: error(MISSING)
    }

    private fun openPlaylist(token: String, skip: SkipTimes, dub: Boolean): PlayableSource {
        val session = get(
            "${AniNzServer.EMBED_ORIGIN}/api/embed/session?t=${enc(token)}",
            embedHeaders(),
        )
        if (aninzBlocked(session.code, session.text)) error(BLOCKED)
        if (session.code !in 200..299) error(BLOCKED)
        val pending = aninzPending(session.text) ?: error(MISSING)
        val playlist = get(pending.url, mediaHeaders())
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

    private fun siteHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Referer" to "$ORIGIN/",
        "Origin" to ORIGIN,
        "User-Agent" to USER_AGENT,
    )

    private fun embedHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Referer" to "${AniNzServer.EMBED_ORIGIN}/",
        "Origin" to AniNzServer.EMBED_ORIGIN,
        "User-Agent" to USER_AGENT,
    )

    private fun mediaHeaders(): Map<String, String> = mapOf(
        "Referer" to "${AniNzServer.EMBED_ORIGIN}/",
        "Origin" to AniNzServer.EMBED_ORIGIN,
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
        /** Settlar session provider id on anipm.pk (not a display rename). */
        const val OWN_SESSION = "anipm"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
