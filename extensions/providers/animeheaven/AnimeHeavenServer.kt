// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * On-device [AnimeHeaven](https://animeheaven.me/) resolve.
 *
 * Search is by title. The series page names the episode key. Playback opens
 * [gate.php](https://animeheaven.me/gate.php) with that key cookie and plays
 * the direct mp4 on this phone. The catalog stays AniList.
 */
class AnimeHeavenServer(
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
        name = "AnimeHeaven",
        description = "On-device animeheaven.me resolve. The gate file plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=animeheaven.me&sz=128",
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
        if (audio.equals("dub", ignoreCase = true)) error(DUB)
        val show = findShow(anilistId, titleHint)
        val jar = linkedMapOf<String, String>()
        val animeUrl = "$SITE/anime.php?${show.id}"
        val anime = heavenFetch(http, jar, animeUrl, "$SITE/")
        if (anime.code == 404) error(MISSING)
        val animeBody = anime.body ?: error(BLOCKED)
        val key = animeHeavenEpisodeKey(animeBody, episode) ?: error(MISSING)
        jar["key"] = key
        val gate = heavenFetch(http, jar, "$SITE/gate.php", animeUrl)
        if (gate.code == 404) error(MISSING)
        val gateBody = gate.body ?: error(BLOCKED)
        val files = animeHeavenMp4s(gateBody, episodeKey = key)
        if (files.isEmpty()) {
            if (animeHeavenSiteOnly(gateBody)) error(SITE_ONLY)
            error(MISSING)
        }
        namedByServer(
            files.mapIndexed { index, url ->
                PlayableSource(
                    url = url,
                    headers = playbackHeaders(),
                    label = if (index == 0) "AnimeHeaven" else "AnimeHeaven ${index + 1}",
                    hls = false,
                    audio = "SUB",
                    audioLanguage = requestedAudioLanguage("sub"),
                )
            },
            server.name,
        )
    }

    private fun findShow(anilistId: Int, titleHint: String?): AnimeHeavenHit {
        cache?.getShow(ID, anilistId)?.siteId?.takeIf { it.isNotBlank() }?.let { id ->
            return AnimeHeavenHit(id = id, title = FlixCrypto.cleanTitle(titleHint), score = 100)
        }
        val titles = animeHeavenTitles(titleHint)
        if (titles.isEmpty()) error("AnimeHeaven needs a title to find this show.")
        val jar = linkedMapOf<String, String>()
        var best: AnimeHeavenHit? = null
        for (title in titles) {
            val page = heavenGet(http, jar, animeHeavenSearchUrl(title), "$SITE/") ?: continue
            val hit = animeHeavenPick(animeHeavenSearch(page), title) ?: continue
            if (best == null || hit.score > best.score) best = hit
        }
        val show = best ?: error(MISSING)
        cache?.putShow(ID, anilistId, show.id, "$SITE/anime.php?${show.id}")
        return show
    }

    companion object {
        const val ID = "animeheaven"
        const val SITE = "https://animeheaven.me"
        const val MISSING = "AnimeHeaven does not have this episode."
        const val BLOCKED = "AnimeHeaven could not be reached."
        const val DUB = "AnimeHeaven only has subbed episodes."
        /** Soft — their gate only returned the site promo clip, not the episode. */
        const val SITE_ONLY = "AnimeHeaven only plays this on their site."
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        fun playbackHeaders(): Map<String, String> = linkedMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$SITE/gate.php",
            "Origin" to SITE,
            "Accept" to "*/*",
        )
    }
}

internal data class AnimeHeavenHit(val id: String, val title: String, val score: Int)

internal fun animeHeavenTitles(titleHint: String?): List<String> =
    titleHint.orEmpty()
        .split("||")
        .map { it.trim() }
        .filter { it.length >= 2 && !it.startsWith("mal:", ignoreCase = true) }
        .distinct()

internal fun animeHeavenSearchUrl(title: String): String {
    val query = java.net.URLEncoder.encode(title.trim(), Charsets.UTF_8.name())
    return "${AnimeHeavenServer.SITE}/fastsearch.php?xhr=1&s=$query"
}

internal fun animeHeavenSearch(html: String): List<AnimeHeavenHit> {
    val found = ArrayList<AnimeHeavenHit>()
    for (anchor in htmlAnchors(html)) {
        val href = anchorAttr(anchor, "href")
        if (!href.contains("anime.php?", ignoreCase = true)) continue
        val id = href.substringAfter('?', "").trim()
        if (id.isEmpty()) continue
        val fast = Regex("(?is)<[^>]*class\\s*=\\s*[\"'][^\"']*fastname[^\"']*[\"'][^>]*>(.*?)</")
            .find(anchor)?.groupValues?.getOrNull(1)
        val image = Regex("(?i)<img\\b[^>]*>").find(anchor)?.value.orEmpty()
        val title = heavenPlain(fast.orEmpty()).ifBlank { anchorAttr(image, "alt") }
        if (title.isNotBlank()) found += AnimeHeavenHit(id, title, 0)
    }
    return found
}

internal fun animeHeavenPick(hits: List<AnimeHeavenHit>, query: String): AnimeHeavenHit? =
    hits.map { it.copy(score = animeHeavenScore(query, it.title)) }
        .filter { it.score >= 60 }
        .maxByOrNull { it.score }

internal fun animeHeavenScore(query: String, candidate: String): Int {
    val left = heavenFold(query)
    val right = heavenFold(candidate)
    if (left.isEmpty() || right.isEmpty()) return 0
    if (left == right) return 100
    val ratio = minOf(left.length, right.length).toDouble() / maxOf(left.length, right.length)
    if (right.startsWith(left) || left.startsWith(right)) return if (ratio >= 0.6) 80 else 30
    if (right.contains(left) || left.contains(right)) return if (ratio >= 0.6) 60 else 25
    return 0
}

internal fun animeHeavenEpisodeKey(html: String, episode: Int): String? {
    for (anchor in htmlAnchors(html)) {
        val opening = anchor.substringBefore('>') + ">"
        val attrs = listOf("onmouseover", "onclick", "href").joinToString(" ") { anchorAttr(opening, it) }
        val key = Regex("""gate[ha]\s*\(\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(attrs)?.groupValues?.getOrNull(1)
            ?: continue
        val number = Regex("(?is)watch2[^\"']*[\"'][^>]*>([0-9]+)")
            .find(anchor)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: continue
        if (number == episode) return key
    }
    return null
}

/**
 * Direct mp4 mirrors from gate.php.
 *
 * The first `<source>` is often `ax.animeheaven.me` — a tiny promo clip that
 * tells the viewer to watch on the site / Elite. Real episode mirrors carry
 * the episode key in the query and live on hosts like `po` / `cz` / `ct`.
 */
internal fun animeHeavenMp4s(html: String, episodeKey: String? = null): List<String> {
    if (animeHeavenSiteOnly(html)) return emptyList()
    val raw = Regex("(?i)<source\\b[^>]*>").findAll(html).map { anchorAttr(it.value, "src") }
        .filter { it.startsWith("http://") || it.startsWith("https://") }
        .filter { runCatching { java.net.URI(it).path.orEmpty().endsWith(".mp4", ignoreCase = true) }.getOrDefault(false) }
        .filter { !it.contains("&error", ignoreCase = true) }
        .distinct()
        .toList()
    val key = episodeKey?.trim().orEmpty()
    val episode = raw.filter { url ->
        !animeHeavenPromoHost(url) && (key.isEmpty() || url.contains(key, ignoreCase = true))
    }
    if (episode.isNotEmpty()) return episode
    // No key-matched row — still skip the promo CDN so Media3 never plays gif.mp4.
    return raw.filterNot(::animeHeavenPromoHost)
}

/** Gate page that only advertises watching on animeheaven.me / Elite. */
internal fun animeHeavenSiteOnly(html: String): Boolean {
    val plain = heavenPlain(html)
    if (Regex("(?i)watch\\s+episode\\s+on\\s+animeheaven\\.me").containsMatchIn(plain)) return true
    if (Regex("(?i)\\belite\\b").containsMatchIn(plain) &&
        Regex("(?i)watch\\s+(it\\s+)?on\\s+(our\\s+)?site").containsMatchIn(plain)
    ) {
        return true
    }
    return false
}

/** `ax.` serves the short “watch on site” promo, not the episode. */
internal fun animeHeavenPromoHost(url: String): Boolean {
    val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    return host == "ax.animeheaven.me" || host.startsWith("ax.")
}

internal data class HeavenReply(val code: Int, val body: String?)

internal fun heavenGet(
    http: OkHttpClient,
    jar: MutableMap<String, String>,
    url: String,
    referer: String,
): String? = heavenFetch(http, jar, url, referer).body

/** Gate/search fetch with status so 404 stays missing instead of sinking Heaven. */
internal fun heavenFetch(
    http: OkHttpClient,
    jar: MutableMap<String, String>,
    url: String,
    referer: String,
): HeavenReply {
    val request = Request.Builder().url(url).get()
        .header("Accept", "text/html,*/*")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Referer", referer)
        .header("User-Agent", AnimeHeavenServer.USER_AGENT)
    if (jar.isNotEmpty()) {
        request.header("Cookie", jar.entries.joinToString("; ") { (name, value) -> "$name=$value" })
    }
    val call = http.newCall(request.build())
    call.timeout().timeout(8, TimeUnit.SECONDS)
    val response = try {
        call.execute()
    } catch (_: Exception) {
        return HeavenReply(0, null)
    }
    response.use {
        it.headers("Set-Cookie").forEach { raw ->
            val pair = raw.substringBefore(';').trim()
            val name = pair.substringBefore('=', "").trim()
            val value = pair.substringAfter('=', "")
            if (name.isNotEmpty() && !name.equals("path", true) && !name.equals("domain", true)) {
                jar[name] = value
            }
        }
        val body = it.body?.string()?.takeIf { text -> text.isNotBlank() }
        if (it.code !in 200..299) return HeavenReply(it.code, null)
        return HeavenReply(it.code, body)
    }
}

private fun htmlAnchors(html: String): List<String> =
    Regex("(?is)<a\\b[^>]*>.*?</a>").findAll(html).map { it.value }.toList()

private fun anchorAttr(tag: String, name: String): String {
    val match = Regex("(?i)(?:^|\\s)${Regex.escape(name)}\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").find(tag)
        ?: return ""
    return heavenDecode(match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty())
}

private fun heavenPlain(value: String): String =
    heavenDecode(
        value.replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(),
    )

private fun heavenDecode(value: String): String {
    val named = value.replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
    return Regex("&#(x[0-9a-f]+|\\d+);", RegexOption.IGNORE_CASE).replace(named) { match ->
        val raw = match.groupValues[1]
        val code = if (raw.startsWith("x", true)) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
        code?.toChar()?.toString() ?: match.value
    }
}

private fun heavenFold(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]"), "")
