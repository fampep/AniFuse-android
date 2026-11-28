// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device [MMani](https://mmani.com/) / AniBD resolve.
 *
 * Episodes are AniList-keyed (`/ajax.php?episodes&epid={anilistId}`) behind an
 * `X-KF-Req` boot token. Each episode link unwraps through
 * [play.anien.top](https://play.anien.top/) `PLAYER_CONFIG.videoUrl` — HLS
 * whose `.jpg` segments are plain MPEG-TS (no XOR / strip). English VTT from
 * the embed rides along when present. Sub-only site.
 */
class MmaniServer(
    private val cache: ResolveCache? = null,
    private val http: OkHttpClient = defaultClient(),
    private val origins: List<String> = ORIGINS,
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "MMani",
        description = "On-device mmani.com resolve. AniBD HLS plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=mmani.com&sz=128",
        siteUrl = SITE,
    )

    private val kfToken = AtomicReference<String?>(null)
    private val activeOrigin = AtomicReference(origins.first())

    override suspend fun resolve(
        anilistId: Int,
        episode: Int,
        audio: String,
        titleHint: String?,
    ): List<PlayableSource> = withContext(Dispatchers.IO) {
        require(anilistId > 0) { "anilistId required" }
        require(episode > 0) { "episode required" }
        if (audio.equals("dub", ignoreCase = true)) error(DUB)

        var boot = bootSession()
        var episodesBody = ajaxGet(boot, "episodes&epid=$anilistId")
        if (episodesBody == null) {
            // Cached token / dead origin — clear and try mirrors once.
            clearBoot(boot.token)
            boot = bootSession()
            episodesBody = ajaxGet(boot, "episodes&epid=$anilistId")
                ?: error(if (boot.reachable) MISSING else BLOCKED)
        }
        val servers = mmaniEpisodeServers(episodesBody)
        if (servers.isEmpty()) error(MISSING)

        val lane = mmaniPickServer(servers, preferDub = false) ?: error(MISSING)
        val ep = mmaniPickEpisode(lane.episodes, episode) ?: error(MISSING)
        cache?.putShow(ID, anilistId, ep.link, "${boot.origin}/up/")

        var eplinkBody = ajaxGet(boot, "eplink&data=${ep.link}")
        if (eplinkBody == null) {
            clearBoot(boot.token)
            boot = bootSession()
            eplinkBody = ajaxGet(boot, "eplink&data=${ep.link}") ?: error(MISSING)
        }
        val embeds = mmaniEplinkSources(eplinkBody)
        if (embeds.isEmpty()) error(MISSING)

        val out = ArrayList<PlayableSource>(embeds.size)
        for (embed in embeds) {
            val opened = openEmbed(embed, boot.origin) ?: continue
            out += opened
        }
        if (out.isEmpty()) error(MISSING)
        namedByServer(out, server.name)
    }

    private fun openEmbed(embed: MmaniEmbed, siteOrigin: String): PlayableSource? {
        val page = get(embed.url, htmlHeaders(siteOrigin)) ?: return null
        val config = mmaniPlayerConfig(page) ?: return null
        val playlist = mmaniAbsoluteUrl(embed.url, config.videoUrl) ?: return null
        if (!playlist.contains(".m3u8", ignoreCase = true) && !playlist.contains("/hls/")) {
            return null
        }
        val mediaHeaders = mediaHeaders(playlist, embed.url)
        val probe = get(playlist, mediaHeaders) ?: return null
        if (!mmaniLooksLikeHls(probe)) return null
        val caps = config.tracks.mapNotNull { track ->
            val url = mmaniAbsoluteUrl(embed.url, track.file) ?: return@mapNotNull null
            SubtitleTrack(
                url = url,
                label = track.label.ifBlank { "English" },
                language = "en",
                mimeType = "text/vtt",
            )
        }
        val fields = BuiltInSourceMeta.animegg(embed.server.ifBlank { "HD" }, "sub")
        return PlayableSource(
            url = playlist,
            headers = mediaHeaders,
            label = fields.quality ?: embed.server.ifBlank { "SUB" },
            stripBytes = 0,
            pngXor = false,
            subtitles = caps,
            hls = true,
            quality = fields.quality,
            audio = "SUB",
            qualityRating = fields.qualityRating,
            audioLanguage = requestedAudioLanguage("sub"),
        )
    }

    private data class Boot(val origin: String, val token: String, val reachable: Boolean)

    private fun bootSession(): Boot {
        kfToken.get()?.let { token ->
            return Boot(activeOrigin.get(), token, reachable = true)
        }
        var reachable = false
        for (origin in origins) {
            val html = get(origin.trimEnd('/') + "/", htmlHeaders(origin))
            if (html != null) reachable = true
            val token = html?.let(::mmaniKfToken) ?: continue
            kfToken.set(token)
            activeOrigin.set(origin.trimEnd('/'))
            return Boot(origin.trimEnd('/'), token, reachable = true)
        }
        return Boot(origins.first().trimEnd('/'), "", reachable = reachable)
    }

    private fun clearBoot(token: String?) {
        if (token.isNullOrBlank()) {
            kfToken.set(null)
            return
        }
        kfToken.compareAndSet(token, null)
    }

    private fun ajaxGet(boot: Boot, query: String): String? {
        if (boot.token.isBlank()) return null
        val url = "${boot.origin}/ajax.php?$query"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", boot.origin)
            .header("Referer", "${boot.origin}/")
            .header("User-Agent", USER_AGENT)
            .header("X-KF-Req", boot.token)
            .header("Cache-Control", "no-store")
            .build()
        val body = execute(request)
        if (body == null) {
            // HTTP/network failure — drop cached token so mirrors can boot.
            clearBoot(boot.token)
            return null
        }
        // Stale KF token → Forbidden JSON; drop cache so the next resolve reboots.
        if (mmaniAjaxForbidden(body)) {
            clearBoot(boot.token)
            return null
        }
        return body
    }

    private fun get(url: String, headers: Map<String, String>): String? {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        return execute(builder.build())
    }

    private fun execute(request: Request): String? {
        val call = http.newCall(request)
        call.timeout().timeout(14, TimeUnit.SECONDS)
        val response = runCatching { call.execute() }.getOrNull() ?: return null
        response.use {
            val text = it.body?.string().orEmpty()
            if (providerChallenged(it.code, text)) return null
            if (it.code == 404) return null
            if (it.code !in 200..299) return null
            return text.takeIf { body -> body.isNotBlank() }
        }
    }

    private fun htmlHeaders(origin: String): Map<String, String> = linkedMapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "User-Agent" to USER_AGENT,
        "Referer" to origin.trimEnd('/') + "/",
        "Origin" to origin.trimEnd('/'),
    )

    private fun mediaHeaders(playlistUrl: String, embedUrl: String): Map<String, String> {
        val parsed = playlistUrl.toHttpUrlOrNull()
        val origin = if (parsed != null) "${parsed.scheme}://${parsed.host}" else PLAY
        return linkedMapOf(
            "Accept" to "*/*",
            "User-Agent" to USER_AGENT,
            "Referer" to embedUrl,
            "Origin" to origin,
        )
    }

    companion object {
        const val ID = "mmani"
        const val SITE = "https://mmani.com"
        const val PLAY = "https://play.anien.top"
        const val MISSING = "MMani does not have this episode."
        const val BLOCKED = "MMani could not be reached."
        const val DUB = "MMani only has subbed episodes."
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        /** Mirror first — mmani.com shares the same KF stack when reachable. */
        val ORIGINS = listOf(
            "https://mmani.bluesagenaturals.com",
            "https://mmani.com",
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(14, TimeUnit.SECONDS)
            .callTimeout(22, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}

internal data class MmaniEpisode(val name: String, val slug: String, val link: String)

internal data class MmaniServerLane(val id: Int, val name: String, val episodes: List<MmaniEpisode>)

internal data class MmaniEmbed(val server: String, val url: String)

internal data class MmaniCaption(val label: String, val file: String)

internal data class MmaniPlayerConfig(val videoUrl: String, val tracks: List<MmaniCaption>)

/** Boot token from the page's fetch wrapper: `})(window.fetch, "…")`. */
internal fun mmaniKfToken(html: String): String? {
    val match = KF_TOKEN_RE.find(html) ?: return null
    return match.groupValues[1].takeIf { it.isNotBlank() }
}

private val KF_TOKEN_RE =
    Regex("""\)\(window\.fetch,\s*"([a-f0-9]{16,})"\)""", RegexOption.IGNORE_CASE)

internal fun mmaniEpisodeServers(payload: String): List<MmaniServerLane> {
    val trimmed = payload.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("{")) return emptyList()
    val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
    val out = ArrayList<MmaniServerLane>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val id = obj.optInt("id", 0)
        val name = obj.optString("server_name").ifBlank { obj.optString("name") }
        val epsArr = obj.optJSONArray("server_data") ?: JSONArray()
        val eps = ArrayList<MmaniEpisode>(epsArr.length())
        for (j in 0 until epsArr.length()) {
            val ep = epsArr.optJSONObject(j) ?: continue
            val link = ep.optString("link").trim()
            if (link.isEmpty()) continue
            eps += MmaniEpisode(
                name = ep.optString("name").trim(),
                slug = ep.optString("slug").trim(),
                link = link,
            )
        }
        if (eps.isEmpty()) continue
        out += MmaniServerLane(id = id, name = name, episodes = eps)
    }
    return out
}

/** Prefer an explicit sub lane; fall back to the first server with episodes. */
internal fun mmaniPickServer(servers: List<MmaniServerLane>, preferDub: Boolean): MmaniServerLane? {
    if (servers.isEmpty()) return null
    val needle = if (preferDub) "dub" else "sub"
    servers.firstOrNull { it.name.contains(needle, ignoreCase = true) }?.let { return it }
    if (!preferDub) return servers.firstOrNull()
    return null
}

internal fun mmaniPickEpisode(episodes: List<MmaniEpisode>, episode: Int): MmaniEpisode? {
    if (episode <= 0 || episodes.isEmpty()) return null
    episodes.firstOrNull { mmaniEpisodeNumber(it.slug) == episode }?.let { return it }
    episodes.firstOrNull { mmaniEpisodeNumber(it.name) == episode }?.let { return it }
    return null
}

internal fun mmaniEpisodeNumber(raw: String): Int? {
    val digits = Regex("""(\d+)""").find(raw.trim())?.groupValues?.getOrNull(1) ?: return null
    return digits.toIntOrNull()?.takeIf { it > 0 }
}

internal fun mmaniEplinkSources(payload: String): List<MmaniEmbed> {
    val trimmed = payload.trim()
    if (trimmed.isEmpty()) return emptyList()
    val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
    val out = ArrayList<MmaniEmbed>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val link = obj.optString("link").trim()
        if (!link.startsWith("http")) continue
        out += MmaniEmbed(
            server = obj.optString("server").trim(),
            url = link,
        )
    }
    return out
}

internal fun mmaniPlayerConfig(html: String): MmaniPlayerConfig? {
    val match = PLAYER_CONFIG_RE.find(html) ?: return null
    val json = match.groupValues[1]
    val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val video = obj.optString("videoUrl").trim()
    if (video.isEmpty()) return null
    val tracksArr = obj.optJSONArray("tracks") ?: JSONArray()
    val tracks = ArrayList<MmaniCaption>(tracksArr.length())
    for (i in 0 until tracksArr.length()) {
        val t = tracksArr.optJSONObject(i) ?: continue
        val file = t.optString("file").trim()
        if (file.isEmpty()) continue
        tracks += MmaniCaption(
            label = t.optString("label").ifBlank { "English" },
            file = file,
        )
    }
    return MmaniPlayerConfig(videoUrl = video, tracks = tracks)
}

private val PLAYER_CONFIG_RE =
    Regex("""window\.PLAYER_CONFIG\s*=\s*(\{.*?\});\s*</script>""", RegexOption.DOT_MATCHES_ALL)

internal fun mmaniAbsoluteUrl(base: String, maybeRelative: String): String? {
    val value = maybeRelative.trim()
    if (value.isEmpty()) return null
    if (value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
    ) {
        return value
    }
    val root = base.toHttpUrlOrNull() ?: return null
    return root.resolve(value)?.toString()
}

internal fun mmaniLooksLikeHls(body: String): Boolean {
    val trimmed = body.trimStart()
    return trimmed.startsWith("#EXTM3U") || trimmed.contains("#EXTINF")
}

internal fun mmaniAjaxForbidden(body: String): Boolean {
    val trimmed = body.trim()
    if (!trimmed.startsWith("{")) return false
    val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return false
    val msg = obj.optString("msg").ifBlank { obj.optString("message") }
    if (msg.contains("Forbidden", ignoreCase = true)) return true
    // optBoolean defaults to false when absent — only treat explicit status:false.
    return obj.has("status") && !obj.optBoolean("status", true)
}
