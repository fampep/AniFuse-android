// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * On-device animegg.org resolve.
 *
 * AniList stays the catalog. Search is the public search page. A card matches
 * when its title, or an alternate title on that same card, is the show.
 * The series page lists episodes. The episode page links a public `/embed/`
 * player, and that player names `/play/{id}/video.mp4`. The phone follows
 * that file. Sub or dub follows the tab on the episode page. A dubbed tab is
 * not played for a sub request.
 */
class AnimeggServer(
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
        name = "AnimeGG",
        description = "On-device animegg.org resolve. The episode file plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=animegg.org&sz=128",
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
        val episodeUrl = findEpisode(show, episode, dub)
        val page = open(episodeUrl, episodeUrl)
        val mirror = animeggChooseMirror(animeggMirrors(page), dub) ?: error(MISSING)
        val embedUrl = "$ORIGIN${mirror.embedPath}"
        val embed = open(embedUrl, episodeUrl)
        val files = animeggFiles(embed)
        if (files.isEmpty()) error(MISSING)
        files.map { file ->
            animeggPlayable(
                url = file.url,
                label = file.label,
                dub = dub,
                referer = embedUrl,
                userAgent = USER_AGENT,
            ) ?: error(MISSING)
        }
    }

    private fun findShow(anilistId: Int, titleHint: String?): AnimeggShow {
        cache?.getShow(ID, anilistId)?.let { saved ->
            val slug = animeggSlug(saved.siteId) ?: return@let
            return AnimeggShow(slug, FlixCrypto.cleanTitle(titleHint), emptyList())
        }
        val queries = animeggQueries(titleHint)
        if (queries.isEmpty()) error("AnimeGG needs a title to find this show.")
        for (query in queries) {
            val page = open(animeggSearchUrl(query), "$ORIGIN/")
            val show = animeggPickShow(animeggSearchHits(page), query) ?: continue
            cache?.putShow(ID, anilistId, show.slug, "$ORIGIN/series/${show.slug}")
            return show
        }
        error(MISSING)
    }

    private fun findEpisode(show: AnimeggShow, episode: Int, dub: Boolean): String {
        val seriesUrl = "$ORIGIN/series/${show.slug}"
        cache?.getEpisodes(ID, seriesUrl)?.let { cached ->
            if (cached.isNotEmpty()) {
                val hit = cached.firstOrNull { it.number == episode } ?: error(MISSING)
                val language = if (dub) hit.hasDub else hit.hasSub
                if (!language) error(MISSING)
                if (hit.ids.startsWith("$ORIGIN/")) return hit.ids
            }
        }
        val page = open(seriesUrl, "$ORIGIN/")
        val rows = animeggEpisodes(page)
        if (rows.isNotEmpty()) {
            cache?.putEpisodes(
                ID,
                seriesUrl,
                rows.map { row ->
                    ResolveCache.CachedEpisode(
                        number = row.number,
                        ids = row.url,
                        hasSub = row.sub || row.raw,
                        hasDub = row.dub,
                    )
                },
            )
            val hit = rows.firstOrNull { it.number == episode } ?: error(MISSING)
            val language = if (dub) hit.dub else hit.sub || hit.raw
            if (!language) error(MISSING)
            return hit.url
        }
        return animeggEpisodeUrl("/${show.slug}-episode-$episode") ?: error(MISSING)
    }

    private fun open(url: String, referer: String): String {
        val body = read(url, siteHeaders(referer)) ?: error(BLOCKED)
        if (animeggBlocked(body.code, body.text)) error(BLOCKED)
        if (body.code == 404) error(MISSING)
        if (body.code !in 200..299) error(BLOCKED)
        if (body.text.isBlank()) error(MISSING)
        return body.text
    }

    private fun read(url: String, headers: Map<String, String>): HttpText? {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = runCatching { http.newCall(builder.build()).execute() }.getOrNull() ?: return null
        response.use { return HttpText(it.code, it.body?.string().orEmpty()) }
    }

    private fun siteHeaders(referer: String): Map<String, String> = mapOf(
        "Accept" to "text/html,application/xhtml+xml",
        "Referer" to referer,
        "User-Agent" to USER_AGENT,
    )

    private data class HttpText(val code: Int, val text: String)

    companion object {
        const val ID = "animegg"
        const val MISSING = "AnimeGG does not have this episode."
        const val BLOCKED = "AnimeGG could not be reached."
        const val ORIGIN = "https://www.animegg.org"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}

internal data class AnimeggShow(
    val slug: String,
    val title: String,
    val aliases: List<String>,
)

internal data class AnimeggEpisode(
    val number: Int,
    val url: String,
    val sub: Boolean,
    val dub: Boolean,
    val raw: Boolean,
)

internal data class AnimeggMirror(
    val version: String,
    val mirror: String,
    val embedPath: String,
)

internal data class AnimeggFile(
    val url: String,
    val label: String,
)

internal fun animeggQueries(titleHint: String?): List<String> {
    val rawParts = titleHint.orEmpty().split("||").map { it.trim() }.filter { it.isNotEmpty() }
    val parts = if (rawParts.isEmpty()) {
        listOf(FlixCrypto.cleanTitle(titleHint))
    } else {
        rawParts.map { part ->
            part.replace(Regex("""\bmal:\d+\b""", RegexOption.IGNORE_CASE), "").trim()
        }
    }
    val out = LinkedHashSet<String>()
    for (part in parts) {
        if (part.length < 2) continue
        out += part
        val head = part.substringBefore(':').trim()
        if (head.length >= 2 && !head.equals(part, ignoreCase = true)) out += head
    }
    return out.take(6).toList()
}

internal fun animeggSearchUrl(query: String): String {
    val q = URLEncoder.encode(query, Charsets.UTF_8.name()).replace("+", "%20")
    return "${AnimeggServer.ORIGIN}/search/?q=$q"
}

internal fun animeggEpisodeUrl(path: String): String? {
    val clean = path.substringBefore('?').substringBefore('#')
    val match = EPISODE_PATH.matchEntire(clean) ?: return null
    val number = match.groupValues[2].toIntOrNull() ?: return null
    if (number <= 0) return null
    val slug = animeggSlug(match.groupValues[1]) ?: return null
    return "${AnimeggServer.ORIGIN}/$slug-episode-$number"
}

internal fun animeggSlug(value: String): String? {
    val slug = value.trim().trim('/')
    if (!SLUG.matches(slug)) return null
    return slug
}

internal fun animeggBlocked(code: Int, body: String): Boolean {
    if (code == 403 || code == 429 || code == 503) return true
    val head = body.take(5_000).lowercase()
    return "just a moment" in head ||
        "cf-browser-verification" in head ||
        "challenge-platform" in head ||
        "attention required" in head ||
        "you have been blocked" in head
}

internal fun animeggSearchHits(html: String): List<AnimeggShow> {
    val page = animeggHtml(html)
    val shows = ArrayList<AnimeggShow>()
    val seen = HashSet<String>()
    for (match in SEARCH_CARD.findAll(page)) {
        val href = attr(match.groupValues[1], "href") ?: continue
        val slug = animeggSeriesSlug(href) ?: continue
        if (!seen.add(slug)) continue
        val body = match.groupValues[2]
        val title = H2.find(body)?.groupValues?.get(1)?.trim().orEmpty()
        if (title.isEmpty()) continue
        val aliases = ALT.find(body)?.let { animeggAliases(it.groupValues[1]) }.orEmpty()
        shows += AnimeggShow(slug, title, aliases)
    }
    return shows
}

internal fun animeggPickShow(hits: List<AnimeggShow>, titleHint: String?): AnimeggShow? {
    val want = animeggNormalize(titleHint)
    if (want.isBlank()) return null
    val primary = hits.filter { animeggNormalize(it.title) == want }
    if (primary.isNotEmpty()) return primary.minByOrNull { animeggNormalize(it.title).length }
    val alias = hits.filter { show -> show.aliases.any { animeggNormalize(it) == want } }
    return alias.minByOrNull { animeggNormalize(it.title).length }
}

internal fun animeggEpisodes(html: String): List<AnimeggEpisode> {
    val page = animeggHtml(html)
    val start = page.indexOf("<ul class=\"newmanga\">")
    if (start < 0) return emptyList()
    val end = page.indexOf("</ul>", start).let { if (it < 0) page.length else it }
    val list = page.substring(start, end)
    val rows = ArrayList<AnimeggEpisode>()
    val seen = HashSet<Int>()
    for (match in EPISODE_ANCHOR.findAll(list)) {
        if ("anm_det_pop" !in match.groupValues[3]) continue
        val number = match.groupValues[2].toIntOrNull() ?: continue
        if (number <= 0 || !seen.add(number)) continue
        val tailEnd = list.indexOf("</li>", match.range.last).let { if (it < 0) list.length else it }
        val block = list.substring(match.range.first, tailEnd)
        val url = animeggEpisodeUrl(match.groupValues[1]) ?: continue
        rows += AnimeggEpisode(
            number = number,
            url = url,
            sub = "#subbed" in block || "btn-subbed" in block,
            dub = "#dubbed" in block || "btn-dubbed" in block,
            raw = "#raw" in block || "btn-raw" in block,
        )
    }
    return rows
}

internal fun animeggMirrors(html: String): List<AnimeggMirror> {
    val page = animeggHtml(html)
    val mirrors = ArrayList<AnimeggMirror>()
    for (match in MIRROR.findAll(page)) {
        val path = animeggEmbedPath(match.groupValues[3]) ?: continue
        mirrors += AnimeggMirror(
            version = match.groupValues[1].lowercase(),
            mirror = match.groupValues[2],
            embedPath = path,
        )
    }
    return mirrors
}

internal fun animeggChooseMirror(mirrors: List<AnimeggMirror>, dub: Boolean): AnimeggMirror? {
    val versions = if (dub) listOf("dubbed") else listOf("subbed", "raw")
    for (version in versions) {
        val rows = mirrors.filter { it.version == version }
        rows.firstOrNull { it.mirror.equals("Animegg", ignoreCase = true) }?.let { return it }
        rows.firstOrNull()?.let { return it }
    }
    return null
}

internal fun animeggFiles(html: String): List<AnimeggFile> {
    val files = ArrayList<AnimeggFile>()
    for (match in FILE.findAll(html)) {
        val url = animeggFileUrl(animeggHtml(match.groupValues[1])) ?: continue
        val label = animeggHtml(match.groupValues[2]).trim()
        if (label.isEmpty() || files.any { it.url == url }) continue
        files += AnimeggFile(url, label)
    }
    return files.sortedByDescending { animeggHeight(it.label) }
}

internal fun animeggFileUrl(raw: String): String? {
    val text = raw.trim()
    val absolute = when {
        text.startsWith("/play/") -> "${AnimeggServer.ORIGIN}$text"
        text.startsWith("https://www.animegg.org/play/") -> text
        text.startsWith("https://animegg.org/play/") ->
            "${AnimeggServer.ORIGIN}/play/" + text.substringAfter("https://animegg.org/play/")
        else -> return null
    }
    val path = absolute.substringAfter(AnimeggServer.ORIGIN).substringBefore('?').substringBefore('#')
    if (!PLAY_PATH.matches(path)) return null
    val query = absolute.substringAfter('?', "")
    if ('#' in query || (query.isNotEmpty() && !QUERY.matches(query))) return null
    return if (query.isEmpty()) "${AnimeggServer.ORIGIN}$path" else "${AnimeggServer.ORIGIN}$path?$query"
}

internal fun animeggEmbedPath(src: String): String? {
    val text = src.trim()
    val path = when {
        text.startsWith("/embed/") -> text
        text.startsWith("//www.animegg.org/embed/") -> text.removePrefix("//www.animegg.org")
        text.startsWith("//animegg.org/embed/") -> text.removePrefix("//animegg.org")
        text.startsWith("${AnimeggServer.ORIGIN}/embed/") -> text.removePrefix(AnimeggServer.ORIGIN)
        text.startsWith("https://animegg.org/embed/") -> text.removePrefix("https://animegg.org")
        else -> return null
    }.substringBefore('?').substringBefore('#')
    if (!EMBED_PATH.matches(path)) return null
    return path
}

internal fun animeggPlayable(
    url: String,
    label: String,
    dub: Boolean,
    referer: String,
    userAgent: String,
): PlayableSource? {
    val file = animeggFileUrl(url) ?: return null
    if (!referer.startsWith("https://")) return null
    val mode = if (dub) "dub" else "sub"
    val fields = BuiltInSourceMeta.animegg(label, mode)
    return PlayableSource(
        url = file,
        headers = mapOf(
            "Referer" to referer,
            "Origin" to AnimeggServer.ORIGIN,
            "User-Agent" to userAgent,
        ),
        label = fields.quality ?: label.ifBlank { if (dub) "DUB" else "SUB" },
        stripBytes = 0,
        quality = fields.quality,
        audio = fields.audio,
        qualityRating = fields.qualityRating,
        hls = false,
        audioLanguage = requestedAudioLanguage(mode),
    )
}

internal fun animeggNormalize(raw: String?): String =
    raw.orEmpty()
        .lowercase()
        .replace('’', '\'')
        .replace("'", "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()

private fun animeggSeriesSlug(href: String): String? {
    val path = href.substringBefore('?').substringBefore('#')
    val at = path.indexOf("/series/")
    if (at < 0) return null
    return animeggSlug(path.substring(at + "/series/".length))
}

private fun animeggAliases(raw: String): List<String> =
    raw.split(',', '、', ';')
        .map { it.trim() }
        .filter { it.length >= 2 }

private fun animeggHeight(label: String): Int =
    Regex("""(\d{3,4})\s*p""", RegexOption.IGNORE_CASE).find(label)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
        ?: 0

private fun animeggHtml(raw: String): String =
    raw.replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

private fun attr(tag: String, name: String): String? {
    val match = Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(tag)
    return match?.groupValues?.get(1)?.trim()?.ifBlank { null }
}

private val SLUG = Regex("""[a-z0-9]+(?:-+[a-z0-9]+)*""")

private val EPISODE_PATH = Regex("""/([a-z0-9-]+)-episode-(\d+)""")

private val PLAY_PATH = Regex("""/play/\d+/video\.mp4""")

private val EMBED_PATH = Regex("""/embed/\d+""")

private val QUERY = Regex("""[A-Za-z0-9._~%=&-]+""")

private val SEARCH_CARD = Regex(
    """<a\b([^>]*\bclass="mse(?:\s[^"]*)?"[^>]*)>([\s\S]*?)</a>""",
    RegexOption.IGNORE_CASE,
)

private val H2 = Regex("""<h2>([^<]*)</h2>""", RegexOption.IGNORE_CASE)

private val ALT = Regex("""Alt Titles\s*:\s*([^<]+)""", RegexOption.IGNORE_CASE)

private val EPISODE_ANCHOR = Regex(
    """<a\s+href="(/[a-z0-9-]+-episode-(\d+))"([^>]*)>""",
    RegexOption.IGNORE_CASE,
)

private val MIRROR = Regex(
    """<div\s+id="(subbed|dubbed|raw)-([^"]+)"[^>]*>\s*<iframe[^>]*\ssrc="([^"]+)"""",
    RegexOption.IGNORE_CASE,
)

private val FILE = Regex(
    """file\s*:\s*["']([^"']+)["']\s*,\s*label\s*:\s*["']([^"']+)["']""",
    RegexOption.IGNORE_CASE,
)
