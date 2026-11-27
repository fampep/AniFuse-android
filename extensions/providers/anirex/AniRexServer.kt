// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * On-device [AniRex](https://anirex.to/) resolve.
 *
 * The watch page is keyed by AniList id (`/watch/...-{id}/{ep}`). The phone
 * asks `/api/watch/{anilistId}/{ep}` for FlixCloud embeds and decrypts the
 * playlist here, the same way reanime does.
 */
class AniRexServer(
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
        val body = read("$SITE/api/watch/$anilistId/$episode") ?: error(BLOCKED)
        val embeds = anirexPlayerUrls(body)
        if (embeds.isEmpty()) error(MISSING)
        var lastError: String = MISSING
        val out = ArrayList<PlayableSource>()
        for (embed in embeds) {
            val opened = runCatching { openEmbed(embed, mode) }.getOrElse { err ->
                lastError = err.message?.takeIf { it.isNotBlank() } ?: lastError
                null
            } ?: continue
            out += opened
            if (out.size >= 2) break
        }
        if (out.isEmpty()) error(lastError)
        namedByServer(out, server.name)
    }

    private fun openEmbed(pageUrl: String, mode: String): PlayableSource {
        val page = get(pageUrl, referer = "$FLIX_ORIGIN/", accept = "text/html")
        if (page.challenged) error(BLOCKED)
        if (page.code == 404) error(MISSING)
        if (page.code !in 200..299) error(anirexHttpFailure(page.code, page.body))
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
        if (tokenBody.code !in 200..299) error(anirexHttpFailure(tokenBody.code, tokenBody.body))
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
        val mask = cachedMask(page.body)
        val playlist = runCatching { fetchPlaylist(url, key) }.getOrElse { error(BLOCKED) }
        if (!playlist.startsWith("#EXTM3U")) error(BLOCKED)
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

    private fun fetchPlaylist(url: String, key: ByteArray): String {
        val response = get(url, referer = "$FLIX_ORIGIN/", accept = "*/*")
        if (response.challenged || response.code !in 200..299) error(BLOCKED)
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

    private fun cachedMask(html: String): ByteArray {
        FlixCrypto.parseSegmentMask(html)?.let {
            segmentMask = it
            return it
        }
        if (segmentMask.contentEquals(FlixCrypto.fallbackSegmentMask)) {
            val script = runCatching {
                get("$FLIX_ORIGIN/artplayer-new/hls.js", referer = "$FLIX_ORIGIN/", accept = "*/*")
            }.getOrNull()
            if (script != null && script.code in 200..299) {
                FlixCrypto.parseSegmentMask(script.body)?.let { segmentMask = it }
            }
        }
        return segmentMask
    }

    private fun read(url: String): String? {
        val reply = get(url, referer = "$SITE/", accept = "application/json")
        if (reply.code !in 200..299) return null
        return reply.body.takeIf { it.isNotBlank() }
    }

    private fun get(url: String, referer: String, accept: String): Reply {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", accept)
            .header("Referer", referer)
            .header("Origin", originOf(referer))
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Reply(response.code, body, challenged(response.code, body))
            }
        }.getOrElse { Reply(0, "", false) }
    }

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private data class Reply(val code: Int, val body: String, val challenged: Boolean)

    companion object {
        const val ID = "anirex"
        const val SITE = "https://anirex.to"
        const val MISSING = "AniRex does not have this episode."
        const val BLOCKED = "AniRex could not be reached."
        const val UA =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val FLIX_ORIGIN = "https://flixcloud.cc"

        fun playbackHeaders(): Map<String, String> = linkedMapOf(
            "User-Agent" to UA,
            "Referer" to "$FLIX_ORIGIN/",
            "Origin" to FLIX_ORIGIN,
            "Accept" to "*/*",
        )
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

internal fun anirexHttpFailure(code: Int, body: String): String {
    if (challenged(code, body)) return AniRexServer.BLOCKED
    if (code == 404) return AniRexServer.MISSING
    if (code == 0) return "AniRex could not be reached."
    return "AniRex could not load this episode (HTTP $code)."
}

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
