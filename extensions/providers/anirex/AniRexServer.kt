// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * On-device [AniRex](https://anirex.to/) resolve.
 *
 * The watch page is keyed by AniList id (`/watch/...-{id}/{ep}`). The phone
 * asks `/api/watch/{anilistId}/{ep}` for FlixCloud embeds and decrypts the
 * playlist here, the same way reanime does.
 *
 * Timeouts fit the 7.5s resolve race: one dead CDN must fail fast so the next
 * source can win. The first good embed returns immediately.
 */
class AniRexServer(
    private val cache: ResolveCache? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(false)
        .build(),
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "AniRex",
        description = "On-device anirex.to resolve. FlixCloud plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=anirex.to&sz=128",
        siteUrl = SITE,
    )

    @Volatile private var segmentMask: ByteArray = FlixCrypto.fallbackSegmentMask

    override suspend fun resolve(
        anilistId: Int,
        episode: Int,
        audio: String,
        titleHint: String?,
    ): List<PlayableSource> = withContext(Dispatchers.IO) {
        require(anilistId > 0) { "anilistId required" }
        require(episode > 0) { "episode required" }
        val mode = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
        playCacheHit(anilistId, episode, mode)?.let { return@withContext namedByServer(it, server.name) }
        val body = watchBody(anilistId, episode)
        coroutineContext.ensureActive()
        val embeds = anirexPlayersForAudio(anirexPlayers(body), mode)
        if (embeds.isEmpty()) error(MISSING)
        var lastError: String = MISSING
        for (player in embeds) {
            coroutineContext.ensureActive()
            val opened = try {
                openEmbed(player.url, mode)
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                lastError = err.message?.takeIf { it.isNotBlank() } ?: lastError
                null
            } ?: continue
            // First good Flix pipeline wins the race. A second embed used to
            // burn the whole 7.5s budget before anything reached Media3.
            playCachePut(anilistId, episode, mode, listOf(opened))
            cache?.putShow(ID, anilistId, anilistId.toString(), "$SITE/watch/-$anilistId/$episode")
            return@withContext namedByServer(listOf(opened), server.name)
        }
        error(lastError)
    }

    private suspend fun openEmbed(pageUrl: String, mode: String): PlayableSource {
        val page = get(pageUrl, referer = "$FLIX_ORIGIN/", accept = "text/html")
        if (page.challenged) error(BLOCKED)
        if (page.code == 404) error(MISSING)
        if (page.timedOut) error(TIMEOUT)
        if (page.code !in 200..299) error(anirexHttpFailure(page.code, page.body))
        coroutineContext.ensureActive()
        val raw = FlixCrypto.extractEmbedData(page.body) ?: error(anirexHttpFailure(page.code, page.body))
        val data = JSONObject(FlixCrypto.jsObjectToJson(raw))
        val seed = data.optString("obfuscation_seed")
        val wasm = Base64.getDecoder().decode(data.optString("w_payload"))
        if (seed.length < 8 || wasm.isEmpty()) error("AniRex embed was missing its playback data.")
        val names = FlixCrypto.fieldNames(seed)
        val token = data.optString(names.tokenField)
        val frag2 = decode(data.optString(names.keyFrag2Field))
        val node = data.getJSONObject("obfuscated_crypto_data")
            .getJSONObject(names.container)
            .getJSONArray(names.array)
            .getJSONObject(0)
            .getJSONObject(names.objectName)
        val frag1 = decode(node.getString(names.keyField))
        val iv = decode(node.getString(names.ivField))
        if (token.isBlank()) error("AniRex embed was missing its playback token.")
        val tokenBody = get("$FLIX_ORIGIN/api/m3u8/$token", referer = pageUrl, accept = "application/json")
        if (tokenBody.challenged) error(BLOCKED)
        if (tokenBody.timedOut) error(TIMEOUT)
        if (tokenBody.code !in 200..299) error(anirexHttpFailure(tokenBody.code, tokenBody.body))
        coroutineContext.ensureActive()
        val tokenJson = JSONObject(tokenBody.body)
        val vidKey = FlixCrypto.sha256Hex(token + "vid").take(10)
        val keyKey = FlixCrypto.sha256Hex(token + "key").take(10)
        val url = FlixCrypto.decryptStreamUrl(
            wasm = wasm,
            frag1 = frag1,
            frag2 = frag2,
            keyFrag = decode(tokenJson.getString(keyKey)),
            seed = seed,
            iv = iv,
            ciphertext = decode(tokenJson.getString(vidKey)),
        )
        if (!url.startsWith("http")) error("AniRex did not return a stream.")
        val key = FlixCrypto.playlistKey(wasm)
        // Never fetch hls.js on the critical path — that extra GET routinely
        // blew the race. Prefer HTML mask, then last good, then the baked fallback.
        val mask = FlixCrypto.parseSegmentMask(page.body)?.also { segmentMask = it } ?: segmentMask
        val playlist = runCatching { fetchPlaylist(url, key) }.getOrElse {
            error(it.message?.takeIf { msg -> msg.isNotBlank() } ?: PLAYLIST_MISS)
        }
        if (!playlist.startsWith("#EXTM3U")) error(PLAYLIST_MISS)
        val tuned = FlixCrypto.preferAudio(playlist, mode == "dub")
        val variant = tuned.lineSequence().firstOrNull { it.startsWith("#EXT-X-STREAM-INF") }.orEmpty()
        val fields = BuiltInSourceMeta.anirex(variant, mode)
        return PlayableSource(
            url = url,
            headers = playbackHeaders(),
            label = fields.quality ?: "AniRex",
            stripBytes = 0,
            subtitles = subtitles(data),
            quality = fields.quality,
            audio = fields.audio,
            flix = FlixUnwrap(
                playlistKey = Base64.getEncoder().encodeToString(key),
                segmentMask = Base64.getEncoder().encodeToString(mask),
                englishAudio = mode == "dub",
            ),
            thumbnails = thumbnailTrackFromJson(data, playbackHeaders()),
            skip = site.anikitty.app.data.skip.skipTimesFromJson(data),
        )
    }

    private suspend fun fetchPlaylist(url: String, key: ByteArray): String {
        val response = get(url, referer = "$FLIX_ORIGIN/", accept = "*/*")
        if (response.timedOut) error(TIMEOUT)
        if (response.challenged) error(BLOCKED)
        if (response.code !in 200..299) error(PLAYLIST_MISS)
        return FlixCrypto.decryptPlaylist(response.body, key)
    }

    private fun subtitles(data: JSONObject): List<SubtitleTrack> {
        val rows = data.optJSONArray("subtitles") ?: return emptyList()
        val out = ArrayList<SubtitleTrack>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val url = row.optString("url")
            if (!url.startsWith("http")) continue
            val label = row.optString("language").ifBlank { "Subtitles" }
            out += SubtitleTrack(
                url = url,
                label = label,
                language = languageCode(label),
                mimeType = "text/vtt",
            )
            if (out.size >= 12) break
        }
        return out.sortedBy { if (it.language == "en") 0 else 1 }
    }

    /**
     * `/api/watch` 404 means this episode is missing — do not sink AniRex as
     * unreachable. Challenges and transport failures stay [BLOCKED].
     */
    private suspend fun watchBody(anilistId: Int, episode: Int): String {
        val reply = get("$SITE/api/watch/$anilistId/$episode", referer = "$SITE/", accept = "application/json")
        if (reply.challenged) error(BLOCKED)
        if (reply.timedOut) error(TIMEOUT)
        if (reply.code == 404) error(MISSING)
        if (reply.code !in 200..299) error(anirexHttpFailure(reply.code, reply.body))
        return reply.body.takeIf { it.isNotBlank() } ?: error(MISSING)
    }

    private suspend fun get(url: String, referer: String, accept: String): Reply {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", accept)
            .header("Referer", referer)
            .header("Origin", originOf(referer))
            .build()
        val call = http.newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            try {
                call.execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (cont.isActive) {
                        cont.resume(Reply(response.code, body, challenged(response.code, body), timedOut = false))
                    }
                }
            } catch (err: IOException) {
                if (!cont.isActive || call.isCanceled()) return@suspendCancellableCoroutine
                val timedOut = err is SocketTimeoutException ||
                    (err.message?.contains("timeout", ignoreCase = true) == true)
                cont.resume(Reply(0, "", challenged = false, timedOut = timedOut))
            }
        }
    }

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private data class Reply(
        val code: Int,
        val body: String,
        val challenged: Boolean,
        val timedOut: Boolean = false,
    )

    companion object {
        const val ID = "anirex"
        const val SITE = "https://anirex.to"
        const val MISSING = "AniRex does not have this episode."
        const val BLOCKED = "AniRex could not be reached."
        const val TIMEOUT = "AniRex timed out."
        /** Soft: one CDN miss must not sink the whole provider mid-race. */
        const val PLAYLIST_MISS = "AniRex playlist was missing."
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val FLIX_ORIGIN = "https://flixcloud.cc"
        private const val PLAY_CACHE_TTL_MS = 3 * 60 * 1000L
        private val playCache = ConcurrentHashMap<String, Pair<Long, List<PlayableSource>>>()

        fun playbackHeaders(): Map<String, String> = linkedMapOf(
            "User-Agent" to UA,
            "Referer" to "$FLIX_ORIGIN/",
            "Origin" to FLIX_ORIGIN,
            "Accept" to "*/*",
        )

        private fun playKey(anilistId: Int, episode: Int, mode: String) = "$anilistId:$episode:$mode"

        internal fun playCacheHit(anilistId: Int, episode: Int, mode: String): List<PlayableSource>? {
            val key = playKey(anilistId, episode, mode)
            val hit = playCache[key] ?: return null
            if (System.currentTimeMillis() - hit.first > PLAY_CACHE_TTL_MS) {
                playCache.remove(key, hit)
                return null
            }
            return hit.second
        }

        internal fun playCachePut(anilistId: Int, episode: Int, mode: String, sources: List<PlayableSource>) {
            playCache[playKey(anilistId, episode, mode)] = System.currentTimeMillis() to sources
            if (playCache.size > 48) {
                val cutoff = System.currentTimeMillis() - PLAY_CACHE_TTL_MS
                playCache.entries.removeIf { it.value.first < cutoff }
            }
        }

        /** Test hook. */
        internal fun clearPlayCache() = playCache.clear()
    }
}

internal data class AniRexPlayer(val url: String, val audio: String)

/** FlixCloud embeds listed by `/api/watch/{anilist}/{ep}`. */
internal fun anirexPlayerUrls(body: String): List<String> =
    anirexPlayers(body).map { it.url }

internal fun anirexPlayers(body: String): List<AniRexPlayer> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    val rows = root.optJSONArray("servers") ?: return emptyList()
    val out = ArrayList<AniRexPlayer>(rows.length())
    for (i in 0 until rows.length()) {
        val row = rows.optJSONObject(i) ?: continue
        val url = row.optString("player_url").trim()
        if (!url.startsWith("http")) continue
        out += AniRexPlayer(
            url = url,
            audio = row.optString("audio").ifBlank { root.optString("audio") }.ifBlank { "dual" },
        )
    }
    return out
}

/**
 * Prefer dual or matching audio embeds. Do not fall back to the opposite
 * audio — that burned the race on sub-only embeds when dub was requested.
 */
internal fun anirexPlayersForAudio(players: List<AniRexPlayer>, mode: String): List<AniRexPlayer> {
    val dub = mode.equals("dub", ignoreCase = true)
    return players.filter { player ->
        val audio = player.audio.trim().lowercase()
        when {
            audio == "dual" || audio.isEmpty() -> true
            dub -> audio == "dub" || audio == "en" || audio.contains("english")
            else -> audio == "sub" || audio == "ja" || audio.contains("japan")
        }
    }
}

internal fun anirexHttpFailure(code: Int, body: String): String =
    providerHttpFailure(
        code = code,
        body = body,
        missing = AniRexServer.MISSING,
        blocked = AniRexServer.BLOCKED,
        challenged = challenged(code, body),
    )

private fun challenged(code: Int, body: String): Boolean {
    val trimmed = body.trimStart()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return false
    if (code == 403 || code == 503) return true
    val head = trimmed.take(800).lowercase()
    return "just a moment" in head || "cf-browser-verification" in head
}

private fun languageCode(label: String): String? = when {
    label.contains("english", ignoreCase = true) -> "en"
    label.contains("japanese", ignoreCase = true) -> "ja"
    label.contains("spanish", ignoreCase = true) -> "es"
    label.contains("portuguese", ignoreCase = true) -> "pt"
    label.contains("french", ignoreCase = true) -> "fr"
    label.contains("german", ignoreCase = true) -> "de"
    label.contains("arabic", ignoreCase = true) -> "ar"
    label.contains("chinese", ignoreCase = true) -> "zh"
    label.contains("korean", ignoreCase = true) -> "ko"
    label.contains("thai", ignoreCase = true) -> "th"
    else -> null
}

private fun originOf(url: String): String {
    val bare = url.substringBefore('?')
    val scheme = bare.substringBefore("://", "")
    if (scheme.isEmpty()) return bare
    val host = bare.substringAfter("://").substringBefore('/')
    return "$scheme://$host"
}
