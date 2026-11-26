// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * On-device anihosts.xyz resolve.
 *
 * The AniList id is the watch id. `/p/home/embed/anilist/{id}/{episode}`
 * answers with a small state JSON that names the host embed (`src`) the
 * site picked for that episode. The host embed (uqload for the current
 * lineup) carries a packed jwplayer setup; the packed blob is unwrapped
 * on device and the first `sources[].file` is the HLS master, which the
 * phone opens with the embed page as Referer. The master lists the
 * original and the dub audio, so one URL serves a sub or a dub request.
 */
class AniHostsServer(
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
        name = "AniHosts",
        description = "On-device anihosts.xyz resolve. The episode master playlist plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=anihosts.xyz&sz=128",
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
        val siteId = anilistId.toString()
        cache?.getEpisodes(ID, siteId)?.takeIf { it.isNotEmpty() }?.let { cached ->
            if (cached.none { it.number == episode }) error(MISSING)
        }
        val embedUrl = anihostsEmbedUrl(anilistId, episode)
        val page = open(embedUrl, "$ORIGIN/")
        val state = anihostsState(page) ?: error(BLOCKED)
        val numbers = anihostsEpisodeNumbers(state)
        if (numbers.isNotEmpty()) {
            cache?.putEpisodes(
                ID,
                siteId,
                numbers.map { number ->
                    ResolveCache.CachedEpisode(
                        number = number,
                        ids = number.toString(),
                        hasSub = true,
                        hasDub = true,
                    )
                },
            )
            cache?.putShow(ID, anilistId, siteId, embedUrl)
        }
        val status = state.optString("status")
        if (status != "ready") {
            if (status == "no_episode" || status == "unknown_title") error(MISSING)
            error(BLOCKED)
        }
        val src = state.optString("src").trim()
        if (!src.startsWith("https://")) error(BLOCKED)
        val captions = ArrayList<SubtitleTrack>()
        var file = anihostsMediaUrl(src)
        if (file == null) {
            val host = open(src, embedUrl)
            val config = anihostsUnpackConfig(host) ?: error(UNPLAYABLE)
            file = anihostsFileFromConfig(config)?.let { anihostsMediaUrl(it) } ?: error(UNPLAYABLE)
            captions += anihostsCaptions(config)
        }
        val source = anihostsPlayable(
            url = file,
            referer = src,
            captions = captions,
            dub = dub,
            userAgent = USER_AGENT,
        ) ?: error(UNPLAYABLE)
        listOf(source)
    }

    private fun open(url: String, referer: String): String {
        val body = read(url, siteHeaders(referer)) ?: error(BLOCKED)
        if (anihostsChallenge(body.code, body.text)) error(BLOCKED)
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
        const val ID = "anihosts"
        const val MISSING = "AniHosts does not have this episode."
        const val BLOCKED = "AniHosts could not be reached."
        const val UNPLAYABLE = "AniHosts does not serve a stream this player can open."
        const val ORIGIN = "https://anihosts.xyz"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    }
}

internal fun anihostsEmbedUrl(anilistId: Int, episode: Int): String {
    if (anilistId <= 0 || episode <= 0) return ""
    return "${AniHostsServer.ORIGIN}/p/home/embed/anilist/$anilistId/$episode"
}

/** The `state-data` script is a single JSON object. */
internal fun anihostsState(html: String): JSONObject? {
    val match = STATE.find(html) ?: return null
    return runCatching { JSONObject(match.groupValues[1]) }.getOrNull()
}

internal fun anihostsEpisodeNumbers(state: JSONObject): List<Int> {
    val rows = state.optJSONArray("episodes") ?: return emptyList()
    val numbers = ArrayList<Int>()
    for (index in 0 until rows.length()) {
        val number = rows.optInt(index, 0)
        if (number > 0 && !numbers.contains(number)) numbers += number
    }
    return numbers
}

/**
 * Unwraps the packed jwplayer setup the host embeds. Same replacements as
 * the page's own eval: every dictionary word from the highest index down to
 * 1, skipping empties, then string-literal unescaping.
 */
internal fun anihostsUnpackConfig(html: String): String? {
    val match = PACKED.find(html) ?: return null
    val payload = match.groupValues[1].trim()
        .removePrefix("'").removePrefix("\"")
        .removeSuffix("'").removeSuffix("\"")
    val count = match.groupValues[2].toIntOrNull() ?: return null
    val words = match.groupValues[3].split("|")
    var out = payload
    var index = count - 1
    while (index > 0) {
        val word = words.getOrNull(index).orEmpty()
        if (word.isNotEmpty()) {
            out = Regex("\\b${index.toString(36)}\\b").replace(out, Regex.escapeReplacement(word))
        }
        index--
    }
    return out
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}

/** First `sources[].file`, scoped to the sources array so tracks are ignored. */
internal fun anihostsFileFromConfig(config: String): String? {
    val start = config.indexOf("sources:[")
    if (start < 0) return null
    val end = config.indexOf(']', start)
    if (end < 0) return null
    return FILE.find(config.substring(start, end))?.groupValues?.get(1)
}

/** Caption tracks the host listed on the player; thumbnails and uploads stay out. */
internal fun anihostsCaptions(config: String): List<SubtitleTrack> {
    val start = config.indexOf("tracks:[")
    if (start < 0) return emptyList()
    val end = config.indexOf(']', start)
    if (end < 0) return emptyList()
    val tracks = ArrayList<SubtitleTrack>()
    val seen = HashSet<String>()
    for (match in TRACK.findAll(config.substring(start, end))) {
        val block = match.value
        val kind = KIND.find(block)?.groupValues?.get(1).orEmpty()
        if (!kind.equals("captions", ignoreCase = true)) continue
        val file = FILE.find(block)?.groupValues?.get(1)?.trim().orEmpty()
        if (!file.endsWith(".vtt", ignoreCase = true)) continue
        val url = anihostsCaptionUrl(file) ?: continue
        if (!seen.add(url)) continue
        val label = LABEL.find(block)?.groupValues?.get(1).orEmpty()
        if (label.equals("Upload captions", ignoreCase = true)) continue
        tracks += SubtitleTrack(
            url = url,
            label = label.ifBlank { "English" },
            language = anihostsCaptionLanguage(label),
            mimeType = "text/vtt",
        )
    }
    return tracks
}

/** An HTTPS HLS master or direct file the host itself linked. */
internal fun anihostsMediaUrl(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty() || text.contains("..") || text.contains("\\")) return null
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.userInfo != null) return null
    val path = uri.rawPath ?: return null
    if (!path.endsWith(".m3u8", ignoreCase = true) && !path.endsWith(".mp4", ignoreCase = true)) return null
    return text
}

internal fun anihostsPlayable(
    url: String,
    referer: String,
    captions: List<SubtitleTrack>,
    dub: Boolean,
    userAgent: String,
): PlayableSource? {
    val file = anihostsMediaUrl(url) ?: return null
    if (!referer.startsWith("https://")) return null
    val origin = runCatching {
        val uri = URI(referer)
        "${uri.scheme}://${uri.host}"
    }.getOrNull() ?: return null
    val mode = if (dub) "dub" else "sub"
    val hls = file.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)
    return PlayableSource(
        url = file,
        headers = mapOf(
            "Referer" to referer,
            "Origin" to origin,
            "User-Agent" to userAgent,
        ),
        label = if (dub) "DUB" else "SUB",
        subtitles = captions,
        hls = hls,
        audioLanguage = requestedAudioLanguage(mode),
    )
}

internal fun anihostsChallenge(code: Int, body: String): Boolean {
    if (code == 403 || code == 429 || code == 503) return true
    val head = body.take(5_000).lowercase()
    return "just a moment" in head ||
        "cf-browser-verification" in head ||
        "challenge-platform" in head ||
        "attention required" in head
}

private fun anihostsCaptionUrl(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty() || text.contains("..") || text.contains("\\")) return null
    if (!text.endsWith(".vtt", ignoreCase = true)) return null
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.userInfo != null) return null
    return text
}

private fun anihostsCaptionLanguage(raw: String): String? {
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

private val STATE = Regex(
    """<script[^>]*id="state-data"[^>]*>\s*(\{.*?\})\s*</script>""",
    RegexOption.DOT_MATCHES_ALL,
)

private val PACKED = Regex(
    """eval\(function\(p,a,c,k,e,d\)\{[^{}]*?return p\}\((.+?),36,(\d+),'(.+?)'\.split\('\|'\)""",
    RegexOption.DOT_MATCHES_ALL,
)

private val FILE = Regex("""file\s*:\s*["']([^"']+)["']""")

private val TRACK = Regex("""\{[^{}]*?file\s*:\s*"[^"]+"[^{}]*?\}""")

private val KIND = Regex("""kind\s*:\s*["']([^"']+)["']""")

private val LABEL = Regex("""label\s*:\s*["']([^"']*)["']""")
