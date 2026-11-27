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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import site.anikitty.app.data.skip.malIdFromTitleHint
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device [2Dhive](https://2dhive.com/) resolve.
 *
 * Episodes are keyed by MyAnimeList id:
 * `https://2dhive.com/episode?anime={mal}&ep_num={n}`.
 * AniList stays the catalog. Playback opens BabaStream, Wavy, HiAnime, and
 * HAdfree on this phone. MegaPlay is never used.
 */
class TwoDHiveServer(
    private val cache: ResolveCache? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .cookieJar(MemoryCookieJar())
        .build(),
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "2Dhive",
        description = "On-device 2dhive.com resolve. BabaStream, Wavy, and HiAnime play on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=2dhive.com&sz=128",
        siteUrl = SITE,
    )

    @Volatile
    private var cachedCapToken: String? = null

    @Volatile
    private var cachedCapTokenExpiry: Long = 0L

    override suspend fun resolve(
        anilistId: Int,
        episode: Int,
        audio: String,
        titleHint: String?,
    ): List<PlayableSource> = withContext(Dispatchers.IO) {
        require(anilistId > 0) { "anilistId required" }
        require(episode > 0) { "episode required" }
        val type = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
        val malId = resolveMal(anilistId, titleHint)
        val episodeUrl = "$SITE/episode?anime=$malId&ep_num=$episode"
        // Warm Cloudflare cookies before the parallel race.
        warm(episodeUrl)
        val lastError = AtomicReference(MISSING)
        val gate = Semaphore(4)
        val opened = coroutineScope {
            val jobs = listOf(
                async {
                    gate.withPermit {
                        runCatching { openBabaStream(malId, episode, type, episodeUrl) }
                            .onFailure { rememberFailure(lastError, it) }
                            .getOrNull()
                            .orEmpty()
                    }
                },
                async {
                    gate.withPermit {
                        runCatching { openWavy(malId, episode, type, episodeUrl) }
                            .onFailure { rememberFailure(lastError, it) }
                            .getOrNull()
                            .orEmpty()
                    }
                },
                async {
                    gate.withPermit {
                        runCatching { openEpisodeMirrors(malId, episode, type, episodeUrl) }
                            .onFailure { rememberFailure(lastError, it) }
                            .getOrNull()
                            .orEmpty()
                    }
                },
                async {
                    if (type != "sub") {
                        emptyList()
                    } else {
                        gate.withPermit {
                            runCatching { openHiAnime(malId, episode, episodeUrl) }
                                .onFailure { rememberFailure(lastError, it) }
                                .getOrNull()
                                ?.let { listOf(it) }
                                .orEmpty()
                        }
                    }
                },
            )
            jobs.awaitAll().flatten()
        }
        val unique = twodhiveDedup(opened)
        if (unique.isEmpty()) error(lastError.get())
        namedByServer(unique, server.name)
    }

    private fun resolveMal(anilistId: Int, titleHint: String?): Int {
        cache?.getShow(ID, anilistId)?.siteId?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        malIdFromTitleHint(titleHint).takeIf { it > 0 }?.let { mal ->
            cache?.putShow(ID, anilistId, mal.toString(), "$SITE/anime?anime=$mal")
            return mal
        }
        val titles = twodhiveTitles(titleHint)
        if (titles.isEmpty()) error("2Dhive needs a title or MAL id to find this show.")
        var reachable = false
        var best: TwoDHiveHit? = null
        for (title in titles) {
            val encoded = URLEncoder.encode(title, Charsets.UTF_8.name())
            val reply = read("$SITE/api/search?q=$encoded", siteHeaders("$SITE/"))
            if (reply == null) continue
            if (providerChallenged(reply.code, reply.text)) error(BLOCKED)
            if (reply.code !in 200..299) {
                if (reply.code != 404) reachable = true
                continue
            }
            reachable = true
            val hit = twodhivePick(twodhiveSearchHits(reply.text), title) ?: continue
            if (best == null || hit.score > best.score) best = hit
            if (hit.score >= 100) break
        }
        val show = best ?: error(if (reachable) MISSING else BLOCKED)
        cache?.putShow(ID, anilistId, show.malId.toString(), "$SITE/anime?anime=${show.malId}")
        return show.malId
    }

    private fun openBabaStream(malId: Int, episode: Int, type: String, episodeUrl: String): List<PlayableSource> {
        val embed = "$BABA/embed/$malId/$episode/$type"
        val page = read(embed, babaHeaders(episodeUrl)) ?: error(BLOCKED)
        if (providerChallenged(page.code, page.text)) error(BLOCKED)
        if (page.code !in 200..299) error(MISSING)
        val cfg = twodhiveParseCfg(page.text) ?: error(MISSING)
        val out = ArrayList<PlayableSource>(2)
        twodhivePrimaryHls(cfg)?.let { url ->
            out += playable(
                url = url,
                label = "BabaStream",
                type = type,
                referer = embed,
                origin = BABA,
                hls = true,
            )
        }
        resolveBabaApi(cfg, embed)?.let { hit ->
            if (twodhiveIsMegaPlay(hit.url)) return@let
            out += playable(
                url = hit.url,
                label = if (hit.hls) "BabaStream HLS" else "BabaStream Direct",
                type = type,
                referer = embed,
                origin = BABA,
                hls = hit.hls,
                subtitles = hit.tracks,
            )
        }
        if (out.isEmpty()) error(MISSING)
        return out
    }

    private fun resolveBabaApi(cfg: TwoDHiveCfg, embed: String): TwoDHiveStream? {
        val pk = cfg.pk?.takeIf { it.isNotBlank() } ?: return null
        val sid = cfg.sid?.takeIf { it.isNotBlank() } ?: return null
        fun post(path: String, payload: String): String? {
            val enc = runCatching { TwoDHiveCrypto.encryptAesGcm(pk, payload) }.getOrNull() ?: return null
            val body = JSONObject().put("s", sid).put("d", enc).toString()
                .toRequestBody("application/json".toMediaType())
            val builder = Request.Builder()
                .url("$BABA$path")
                .post(body)
            babaHeaders(embed).forEach { (n, v) -> builder.header(n, v) }
            builder.header("Origin", BABA)
            builder.header("Content-Type", "application/json")
            val response = runCatching { http.newCall(builder.build()).execute() }.getOrNull() ?: return null
            return response.use {
                if (it.code !in 200..299) return@use null
                val bodyText = it.body?.string().orEmpty()
                if (providerChallenged(it.code, bodyText)) return@use null
                val d = runCatching { JSONObject(bodyText).optString("d") }.getOrNull()
                d?.takeIf { enc -> enc.isNotBlank() }?.let { enc ->
                    runCatching { TwoDHiveCrypto.decryptAesGcm(pk, enc) }.getOrNull()
                }
            }
        }
        var raw = post("/api/resolve", """{"ts":${System.currentTimeMillis()}}""")
        var payload = twodhiveParseResolve(raw)
        if (payload?.needsVerify == true && !cfg.cap.isNullOrBlank()) {
            val token = capToken(cfg.cap)
            if (!token.isNullOrBlank()) {
                val verify = post(
                    "/api/cap-verify",
                    """{"ts":${System.currentTimeMillis()},"token":"$token","mode":"invisible"}""",
                )
                val ok = runCatching { JSONObject(verify.orEmpty()).optString("t") }.getOrNull() == "ok" ||
                    twodhiveParseResolve(verify)?.ok == true
                if (ok) {
                    raw = post("/api/resolve", """{"ts":${System.currentTimeMillis()}}""")
                    payload = twodhiveParseResolve(raw)
                }
            }
        }
        val stream = payload ?: return null
        val url = stream.url?.takeIf { it.startsWith("http") } ?: return null
        if (twodhiveIsMegaPlay(url)) return null
        val absolute = twodhiveAbsUrl(url, BABA)
        val tracks = stream.tracks.mapNotNull { track ->
            val u = track.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SubtitleTrack(
                url = twodhiveAbsUrl(u, BABA),
                label = track.label?.takeIf { it.isNotBlank() } ?: "English",
                language = "en",
            )
        }
        return TwoDHiveStream(
            url = absolute,
            hls = stream.kind.equals("hls", ignoreCase = true) || absolute.contains(".m3u8", ignoreCase = true),
            tracks = tracks,
        )
    }

    private fun capToken(capEndpoint: String): String? {
        val now = System.currentTimeMillis()
        cachedCapToken?.takeIf { now < cachedCapTokenExpiry && it.isNotBlank() }?.let { return it }
        return runCatching {
            val endpoint = if (capEndpoint.endsWith("/")) capEndpoint else "$capEndpoint/"
            val empty = "{}".toRequestBody("application/json".toMediaType())
            val chalReq = Request.Builder()
                .url("${endpoint}challenge")
                .post(empty)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            val chalBody = http.newCall(chalReq).execute().use { resp ->
                if (resp.code !in 200..299) return@use null
                resp.body?.string()
            } ?: return null
            val chal = JSONObject(chalBody)
            val token = chal.optString("token").takeIf { it.isNotBlank() } ?: return null
            val challenge = chal.optJSONObject("challenge")
            val count = challenge?.optInt("c", 80) ?: 80
            val sLen = challenge?.optInt("s", 32) ?: 32
            val dLen = challenge?.optInt("d", 4) ?: 4
            val solutions = TwoDHiveCrypto.solveCapChallenges(token, count, sLen, dLen)
            val redeemJson = JSONObject()
                .put("token", token)
                .put("solutions", JSONArray(solutions))
                .toString()
                .toRequestBody("application/json".toMediaType())
            val redeemReq = Request.Builder()
                .url("${endpoint}redeem")
                .post(redeemJson)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            val redeemBody = http.newCall(redeemReq).execute().use { resp ->
                if (resp.code !in 200..299) return@use null
                resp.body?.string()
            } ?: return null
            val redeem = JSONObject(redeemBody)
            val out = redeem.optString("token").takeIf { it.isNotBlank() } ?: return null
            cachedCapToken = out
            cachedCapTokenExpiry = redeem.optLong("expires", now + 3_600_000L)
            out
        }.getOrNull()
    }

    private fun openWavy(malId: Int, episode: Int, type: String, episodeUrl: String): List<PlayableSource> {
        val embed = "$WAVY/$malId/$episode/$type"
        val page = read(embed, wavyHeaders(episodeUrl)) ?: error(BLOCKED)
        if (providerChallenged(page.code, page.text)) error(BLOCKED)
        if (page.code !in 200..299) error(MISSING)
        return listOf(
            playable(
                url = "$WAVY/stream.m3u8",
                label = "Wavy",
                type = type,
                referer = embed,
                origin = WAVY,
                hls = true,
                quality = "720p",
            ),
        )
    }

    private fun openHiAnime(malId: Int, episode: Int, episodeUrl: String): PlayableSource? {
        val reply = read(
            "$SITE/api/hianime?mal_id=$malId&ep_num=$episode",
            siteHeaders(episodeUrl),
        ) ?: return null
        if (providerChallenged(reply.code, reply.text)) error(BLOCKED)
        if (reply.code !in 200..299) return null
        val json = runCatching { JSONObject(reply.text) }.getOrNull() ?: return null
        val m3u8 = json.optString("m3u8").takeIf { it.startsWith("http") } ?: return null
        if (twodhiveIsMegaPlay(m3u8)) return null
        val sub = json.optString("subtitle").takeIf { it.startsWith("http") }
        return playable(
            url = m3u8,
            label = "HiAnime",
            type = "sub",
            referer = episodeUrl,
            origin = SITE,
            hls = true,
            subtitles = listOfNotNull(
                sub?.let { SubtitleTrack(url = it, label = "English", language = "en") },
            ),
        )
    }

    private fun openEpisodeMirrors(
        malId: Int,
        episode: Int,
        type: String,
        episodeUrl: String,
    ): List<PlayableSource> {
        val page = read(episodeUrl, siteHeaders("$SITE/")) ?: error(BLOCKED)
        if (providerChallenged(page.code, page.text)) error(BLOCKED)
        if (page.code !in 200..299) error(if (page.code == 404) MISSING else BLOCKED)
        val wantDub = type == "dub"
        val out = ArrayList<PlayableSource>()
        val seen = LinkedHashSet<String>()
        fun add(source: PlayableSource?) {
            if (source == null) return
            if (twodhiveIsMegaPlay(source.url)) return
            if (!seen.add(source.url)) return
            out += source
        }
        for (iframe in twodhiveIframes(page.text)) {
            if (twodhiveIsMegaPlay(iframe)) continue
            val isDub = iframe.contains("/dub", ignoreCase = true) ||
                iframe.endsWith("/dub", ignoreCase = true)
            if (isDub != wantDub && (iframe.contains("/sub", true) || iframe.contains("/dub", true))) {
                continue
            }
            when {
                iframe.contains("babastream", ignoreCase = true) -> {
                    runCatching { openBabaStreamUrl(iframe, type) }.getOrNull()?.forEach { add(it) }
                }
                else -> {
                    // Unknown embeds stay out — we only ship playable URLs, not MegaPlay.
                }
            }
        }
        for (server in twodhiveServers(page.text)) {
            if (server.dub != wantDub) continue
            if (twodhiveIsMegaPlay(server.slug)) continue
            when {
                server.name.equals("HAdfree", ignoreCase = true) ||
                    server.slug.contains("hadfree", ignoreCase = true) -> {
                    add(openHadfree(server.slug, type, episodeUrl))
                }
                server.slug.contains("babastream", ignoreCase = true) -> {
                    runCatching { openBabaStreamUrl(server.slug, type) }.getOrNull()?.forEach { add(it) }
                }
                server.slug.startsWith("http") &&
                    (server.slug.contains(".m3u8", true) || server.slug.contains(".mp4", true)) -> {
                    add(
                        playable(
                            url = server.slug,
                            label = server.name.ifBlank { "2Dhive" },
                            type = type,
                            referer = episodeUrl,
                            origin = SITE,
                            hls = server.slug.contains(".m3u8", true),
                        ),
                    )
                }
            }
        }
        twodhivePrefetchUrl(page.text, type)?.let { url ->
            add(
                playable(
                    url = url,
                    label = "2Dhive",
                    type = type,
                    referer = episodeUrl,
                    origin = SITE,
                    hls = url.contains(".m3u8", true),
                ),
            )
        }
        return out
    }

    private fun openBabaStreamUrl(embed: String, type: String): List<PlayableSource> {
        val page = read(embed, babaHeaders("$SITE/")) ?: return emptyList()
        if (providerChallenged(page.code, page.text)) error(BLOCKED)
        if (page.code !in 200..299) return emptyList()
        val cfg = twodhiveParseCfg(page.text) ?: return emptyList()
        val out = ArrayList<PlayableSource>()
        twodhivePrimaryHls(cfg)?.let { url ->
            out += playable(
                url = url,
                label = "BabaStream",
                type = type,
                referer = embed,
                origin = BABA,
                hls = true,
            )
        }
        resolveBabaApi(cfg, embed)?.let { hit ->
            if (!twodhiveIsMegaPlay(hit.url)) {
                out += playable(
                    url = hit.url,
                    label = if (hit.hls) "BabaStream HLS" else "BabaStream Direct",
                    type = type,
                    referer = embed,
                    origin = BABA,
                    hls = hit.hls,
                    subtitles = hit.tracks,
                )
            }
        }
        return out
    }

    private fun openHadfree(slug: String, type: String, episodeUrl: String): PlayableSource? {
        val encoded = URLEncoder.encode(slug, Charsets.UTF_8.name())
        val reply = read("$SITE/api/hadfree?slug=$encoded", siteHeaders(episodeUrl)) ?: return null
        if (providerChallenged(reply.code, reply.text)) error(BLOCKED)
        if (reply.code !in 200..299) return null
        val url = runCatching { JSONObject(reply.text).optString("streamUrl") }.getOrNull()
            ?.takeIf { it.startsWith("http") }
            ?: return null
        if (twodhiveIsMegaPlay(url)) return null
        return playable(
            url = url,
            label = "HAdfree",
            type = type,
            referer = episodeUrl,
            origin = SITE,
            hls = url.contains(".m3u8", true),
        )
    }

    private fun playable(
        url: String,
        label: String,
        type: String,
        referer: String,
        origin: String,
        hls: Boolean,
        quality: String? = null,
        subtitles: List<SubtitleTrack> = emptyList(),
    ): PlayableSource {
        val meta = BuiltInSourceMeta.twodhive(
            streamInf = quality.orEmpty(),
            audioMode = type,
        )
        return PlayableSource(
            url = url,
            headers = playbackHeaders(referer, origin),
            label = label,
            hls = hls || url.contains(".m3u8", ignoreCase = true),
            audio = meta.audio ?: if (type == "dub") "DUB" else "SUB",
            quality = meta.quality ?: quality,
            audioLanguage = requestedAudioLanguage(type),
            subtitles = subtitles,
            serverName = label,
        )
    }

    private fun rememberFailure(slot: AtomicReference<String>, err: Throwable) {
        val msg = err.message?.takeIf { it.isNotBlank() } ?: MISSING
        if (msg == BLOCKED || isHardProviderFailure(msg)) {
            slot.set(BLOCKED)
        } else {
            slot.compareAndSet(MISSING, msg)
        }
    }

    private fun warm(url: String) {
        runCatching { read(url, siteHeaders("$SITE/")) }
    }

    private fun read(url: String, headers: Map<String, String>): HiveHttp? {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val response = runCatching { http.newCall(builder.build()).execute() }.getOrNull() ?: return null
        return response.use { HiveHttp(it.code, it.body?.string().orEmpty()) }
    }

    private data class HiveHttp(val code: Int, val text: String)

    private fun siteHeaders(referer: String): Map<String, String> = linkedMapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Referer" to referer,
        "Origin" to SITE,
    )

    private fun babaHeaders(referer: String): Map<String, String> = linkedMapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "*/*",
        "Referer" to referer,
        "Origin" to BABA,
    )

    private fun wavyHeaders(referer: String): Map<String, String> = linkedMapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "*/*",
        "Referer" to referer,
        "Origin" to WAVY,
    )

    private fun playbackHeaders(referer: String, origin: String): Map<String, String> = linkedMapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "*/*",
        "Referer" to referer,
        "Origin" to origin,
    )

    companion object {
        const val ID = "twodhive"
        const val SITE = "https://2dhive.com"
        const val BABA = "https://babastream.top"
        const val WAVY = "https://wavy.babastream.top"
        const val MISSING = "2Dhive does not have this episode."
        const val BLOCKED = "2Dhive could not be reached."
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

internal data class TwoDHiveHit(val malId: Int, val title: String, val score: Int)

internal data class TwoDHiveCfg(
    val primary: String?,
    val mal: String?,
    val ep: String?,
    val sub: String?,
    val sid: String?,
    val pk: String?,
    val cap: String?,
)

internal data class TwoDHiveServerRow(
    val name: String,
    val slug: String,
    val dub: Boolean,
)

internal data class TwoDHiveResolve(
    val kind: String?,
    val url: String?,
    val needsVerify: Boolean,
    val ok: Boolean,
    val tracks: List<TwoDHiveTrack>,
)

internal data class TwoDHiveTrack(val label: String?, val url: String?)

internal data class TwoDHiveStream(
    val url: String,
    val hls: Boolean,
    val tracks: List<SubtitleTrack>,
)

internal fun twodhiveTitles(titleHint: String?): List<String> =
    titleHint.orEmpty()
        .split("||")
        .map { part ->
            part.replace(Regex("""\bmal:\d+\b""", RegexOption.IGNORE_CASE), "").trim()
        }
        .filter { it.length >= 2 }
        .distinct()

internal fun twodhiveSearchHits(payload: String): List<TwoDHiveHit> {
    val root = runCatching { JSONObject(payload) }.getOrNull() ?: return emptyList()
    val results = root.optJSONArray("results") ?: return emptyList()
    val out = ArrayList<TwoDHiveHit>(results.length())
    for (i in 0 until results.length()) {
        val row = results.optJSONObject(i) ?: continue
        val mal = row.optInt("id", 0).takeIf { it > 0 } ?: continue
        val title = row.optString("englishTitle").takeIf { it.isNotBlank() }
            ?: row.optString("title").takeIf { it.isNotBlank() }
            ?: continue
        out += TwoDHiveHit(malId = mal, title = title, score = 0)
    }
    return out
}

internal fun twodhivePick(hits: List<TwoDHiveHit>, query: String): TwoDHiveHit? {
    if (hits.isEmpty()) return null
    val needle = FlixCrypto.cleanTitle(query).lowercase()
    if (needle.isBlank()) return hits.firstOrNull()?.copy(score = 1)
    return hits
        .map { hit ->
            val title = hit.title.lowercase()
            val score = when {
                title == needle -> 100
                title.startsWith(needle) || needle.startsWith(title) -> 90
                title.contains(needle) || needle.contains(title) -> 70
                else -> titleSimilarity(title, needle)
            }
            hit.copy(score = score)
        }
        .filter { it.score > 0 }
        .maxByOrNull { it.score }
}

private fun titleSimilarity(a: String, b: String): Int {
    val left = a.split(Regex("""\W+""")).filter { it.length > 2 }.toSet()
    val right = b.split(Regex("""\W+""")).filter { it.length > 2 }.toSet()
    if (left.isEmpty() || right.isEmpty()) return 0
    val shared = left.intersect(right).size
    if (shared == 0) return 0
    return ((shared * 60) / maxOf(left.size, right.size)).coerceAtLeast(1)
}

internal fun twodhiveParseCfg(html: String): TwoDHiveCfg? {
    val match = Regex("""\bvar\s+CFG\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL)
        .find(html)
        ?: return null
    val json = runCatching { JSONObject(match.groupValues[1]) }.getOrNull() ?: return null
    return TwoDHiveCfg(
        primary = json.optString("primary").takeIf { it.isNotBlank() },
        mal = json.optString("mal").takeIf { it.isNotBlank() },
        ep = json.optString("ep").takeIf { it.isNotBlank() },
        sub = json.optString("sub").takeIf { it.isNotBlank() },
        sid = json.optString("sid").takeIf { it.isNotBlank() },
        pk = json.optString("pk").takeIf { it.isNotBlank() },
        cap = json.optString("cap").takeIf { it.isNotBlank() },
    )
}

internal fun twodhivePrimaryHls(cfg: TwoDHiveCfg): String? {
    val primary = cfg.primary?.trimEnd('/')?.takeIf { it.startsWith("http") } ?: return null
    val mal = cfg.mal ?: return null
    val ep = cfg.ep ?: return null
    val sub = cfg.sub ?: return null
    return "$primary/v2/${enc(mal)}/${enc(ep)}/${enc(sub)}/.m3u8?v=3"
}

private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

internal fun twodhiveParseResolve(raw: String?): TwoDHiveResolve? {
    if (raw.isNullOrBlank()) return null
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val kind = json.optString("t").takeIf { it.isNotBlank() }
    val tracks = ArrayList<TwoDHiveTrack>()
    val arr = json.optJSONArray("tracks")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            tracks += TwoDHiveTrack(
                label = row.optString("label").takeIf { it.isNotBlank() },
                url = row.optString("u").takeIf { it.isNotBlank() },
            )
        }
    }
    return TwoDHiveResolve(
        kind = kind,
        url = json.optString("u").takeIf { it.isNotBlank() },
        needsVerify = kind.equals("error", true) && json.optString("m").equals("verify", true),
        ok = kind.equals("ok", true),
        tracks = tracks,
    )
}

internal fun twodhiveIframes(html: String): List<String> =
    Regex("""(?i)<iframe[^>]+\bsrc=["']([^"']+)["']""")
        .findAll(html)
        .map { twodhiveHtmlUnescape(it.groupValues[1]).trim() }
        .filter { it.startsWith("http") }
        .distinct()
        .toList()

internal fun twodhiveServers(html: String): List<TwoDHiveServerRow> {
    val out = ArrayList<TwoDHiveServerRow>()
    val props = twodhiveAstroProps(html) ?: return out
    fun addRow(row: JSONObject?) {
        if (row == null) return
        val slug = row.optString("slug").ifBlank { row.optString("url") }
        if (slug.isBlank()) return
        out += TwoDHiveServerRow(
            name = row.optString("server_name").ifBlank { row.optString("name") },
            slug = twodhiveHtmlUnescape(slug),
            dub = row.optBoolean("dub") || slug.contains("/dub", ignoreCase = true),
        )
    }
    when (val servers = props.opt("servers")) {
        is JSONArray -> {
            for (i in 0 until servers.length()) {
                addRow(servers.optJSONObject(i))
            }
        }
        is JSONObject -> {
            for (key in servers.keys()) {
                addRow(servers.optJSONObject(key))
            }
        }
    }
    return out
}

internal fun twodhivePrefetchUrl(html: String, type: String): String? {
    val props = twodhiveAstroProps(html) ?: return null
    val prefetched = props.opt("prefetchedHls") as? JSONObject ?: return null
    val lane = prefetched.optJSONObject(type) ?: prefetched.optJSONObject(type.lowercase())
    val direct = lane?.optString("url")?.takeIf { it.startsWith("http") }
        ?: prefetched.optString(type).takeIf { it.startsWith("http") }
    if (direct != null && !twodhiveIsMegaPlay(direct)) return direct
    // Inline playlist content has no host URL — skip; Media3 needs an absolute address.
    return null
}

internal fun twodhiveAstroProps(html: String): JSONObject? {
    val island = Regex(
        """(?is)<astro-island[^>]+(?:EpisodePlayer|prefetchedHls)[^>]*\bprops="([^"]+)"|""" +
            """(?is)\bprops="([^"]*prefetchedHls[^"]*)""",
    ).find(html)
    val raw = island?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
        ?: run {
            val idx = html.indexOf("prefetchedHls")
            if (idx < 0) return null
            val propsIdx = html.lastIndexOf("props=\"", idx)
            if (propsIdx < 0) return null
            val start = propsIdx + 7
            val end = html.indexOf('"', start)
            if (end < 0) return null
            html.substring(start, end)
        }
    val decoded = twodhiveHtmlUnescape(raw)
    val json = runCatching { JSONObject(decoded) }.getOrNull() ?: return null
    return twodhiveDecodeProps(json)
}

internal fun twodhiveDecodeProps(raw: JSONObject): JSONObject {
    val out = JSONObject()
    for (key in raw.keys()) {
        out.put(key, twodhiveDecodeAstro(raw.opt(key)))
    }
    return out
}

internal fun twodhiveDecodeAstro(value: Any?): Any? {
    if (value !is JSONArray || value.length() < 2) return value
    val type = value.optInt(0, -1)
    val data = value.opt(1)
    return when (type) {
        0 -> {
            if (data !is JSONObject) data
            else {
                val out = JSONObject()
                for (key in data.keys()) out.put(key, twodhiveDecodeAstro(data.opt(key)))
                out
            }
        }
        1 -> {
            if (data is JSONArray) {
                val out = JSONArray()
                for (i in 0 until data.length()) out.put(twodhiveDecodeAstro(data.opt(i)))
                out
            } else data
        }
        else -> data
    }
}

internal fun twodhiveIsMegaPlay(url: String): Boolean =
    url.contains("megaplay", ignoreCase = true)

internal fun twodhiveAbsUrl(url: String, base: String): String {
    if (url.startsWith("http://", true) || url.startsWith("https://", true)) return url
    if (url.startsWith("//")) return "https:$url"
    val root = base.trimEnd('/')
    return if (url.startsWith("/")) "$root$url" else "$root/$url"
}

internal fun twodhiveHtmlUnescape(raw: String): String =
    raw.replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("\\/", "/")

internal fun twodhiveDedup(sources: List<PlayableSource>): List<PlayableSource> {
    val seen = LinkedHashSet<String>()
    return sources.filter { source ->
        !twodhiveIsMegaPlay(source.url) && seen.add(source.url)
    }
}
