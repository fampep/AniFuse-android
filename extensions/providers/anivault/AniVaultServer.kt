// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import site.anikitty.app.data.skip.malIdFromTitleHint

/**
 * On-device AniVault.
 *
 * Playback asks anivault.co for the same source buttons the site shows
 * (Hd-1, Vidstream-2, Hd-2, and the rest). The phone plays the file those
 * buttons return. It does not call the private scraper, and it does not
 * reuse the other resolvers on this phone.
 */
class AniVaultServer(
    private val cache: ResolveCache? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build(),
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "AniVault",
        description = "AniVault's own sources. The file plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=anivault.tv&sz=128",
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
        val shortId = findShortId(anilistId, titleHint)
        val buttons = anivaultButtons(audio)
        val avoid = coroutineContext[AvoidPlaybackHost]?.host
        val sameHost = AtomicReference<PlayableSource?>(null)
        val raced = raceHeadStart(
            count = buttons.size,
            parallel = buttons.size,
            headStartMs = 0L,
            timeoutMs = BUTTON_TIMEOUT_MS,
        ) { index ->
            val button = buttons[index]
            val file = try {
                read(anivaultStreamUrl(API, button, shortId, episode))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return@raceHeadStart null
            val source = anivaultPlayable(file, button.label, playbackHeaders()) ?: return@raceHeadStart null
            val accepted = sourcesAvoidingHost(listOf(source), avoid)
            if (accepted.isNotEmpty()) {
                accepted.first()
            } else {
                sameHost.compareAndSet(null, source)
                null
            }
        }
        val chosen = raced?.second ?: sameHost.get() ?: error("AniVault could not find this episode")
        namedByServer(listOf(chosen), server.name)
    }

    private fun findShortId(anilistId: Int, titleHint: String?): String {
        cache?.getShow(ID, anilistId)?.siteId?.takeIf { anivaultShortId(it) }?.let { return it }
        val mal = malIdFromTitleHint(titleHint)
        if (mal > 0) {
            val mapped = read("$API/api/hoshika/identity-by-legacy/$mal")?.let(::anivaultMappedShortId)
            if (mapped != null) {
                cache?.putShow(ID, anilistId, mapped, "$SITE/watch?anime=$mapped")
                return mapped
            }
        }
        val titles = anivaultTitles(titleHint)
        if (titles.isEmpty()) error("AniVault needs a title to find this show.")
        for (title in titles) {
            val encoded = java.net.URLEncoder.encode(title, Charsets.UTF_8.name())
            val body = read("$API/api/hoshika/search?q=$encoded&limit=8") ?: continue
            val shortId = anivaultSearchShortId(body, titles) ?: continue
            cache?.putShow(ID, anilistId, shortId, "$SITE/watch?anime=$shortId")
            return shortId
        }
        error("AniVault could not find this show.")
    }

    private fun read(url: String): String? {
        val request = Request.Builder().url(url).get()
        siteHeaders().forEach { (name, value) -> request.header(name, value) }
        val response = runCatching { http.newCall(request.build()).execute() }.getOrNull() ?: return null
        response.use {
            if (it.code !in 200..299) return null
            return it.body?.string()?.takeIf { body -> body.isNotBlank() }
        }
    }

    private fun siteHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Referer" to "$API/",
        "User-Agent" to USER_AGENT,
    )

    private fun playbackHeaders(): Map<String, String> = mapOf(
        "Referer" to "$API/",
        "Origin" to API,
        "User-Agent" to USER_AGENT,
    )

    companion object {
        const val ID = "anivault"
        const val SITE = "https://anivault.tv/"
        const val API = "https://www.anivault.co"

        /** Inside the outer per-resolver cap, so the last button is not cancelled first. */
        const val BUTTON_TIMEOUT_MS = RESOLVE_TIMEOUT_MS - 500L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

/** Host the player is already using. AniVault keeps asking until another CDN answers. */
internal class AvoidPlaybackHost(val host: String) : AbstractCoroutineContextElement(AvoidPlaybackHost) {
    companion object Key : CoroutineContext.Key<AvoidPlaybackHost>
}

/** One button on the AniVault watch page. `server` is the scraper's exact name. */
internal data class AniVaultButton(
    val endpoint: String,
    val server: String?,
    val label: String,
    val audio: String,
)

/** Sub, dub, and Hindi buttons, in the order the site renders them. */
internal fun anivaultButtons(audio: String): List<AniVaultButton> = when {
    audio.equals("hindi", ignoreCase = true) -> ANIVAULT_HINDI
    audio.equals("dub", ignoreCase = true) -> ANIVAULT_DUB
    else -> ANIVAULT_SUB
}

internal fun anivaultStreamUrl(origin: String, button: AniVaultButton, shortId: String, episode: Int): String {
    val root = origin.trimEnd('/')
    val server = button.server?.let { "&server=${java.net.URLEncoder.encode(it, Charsets.UTF_8.name())}" }.orEmpty()
    val id = java.net.URLEncoder.encode(shortId, Charsets.UTF_8.name())
    return "$root/api/${button.endpoint}?ep=$episode&audio=${button.audio}&hoshikaId=$id$server"
}

internal fun anivaultShortId(value: String): Boolean = value.trim().length >= 2

internal fun anivaultTitles(titleHint: String?): List<String> =
    titleHint.orEmpty()
        .split("||")
        .map { it.trim() }
        .filter { it.length >= 2 && !it.startsWith("mal:", ignoreCase = true) }
        .distinct()

internal fun anivaultMappedShortId(body: String): String? {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
    if (!root.optBoolean("mapped")) return null
    return root.optJSONObject("data")?.optString("shortId")?.trim()?.takeIf(::anivaultShortId)
}

internal fun anivaultSearchShortId(body: String, titles: List<String>): String? {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
    val rows = root.optJSONArray("data") ?: return null
    val wanted = titles.map(::anivaultNorm).filter { it.isNotEmpty() }.toSet()
    if (wanted.isEmpty()) return null
    for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        val shortId = row.optString("shortId").trim()
        if (!anivaultShortId(shortId)) continue
        val names = listOf("title", "titleEnglish", "titleRomaji", "titleJapanese")
            .map { row.optString(it) }
            .map(::anivaultNorm)
        if (names.any { it in wanted }) return shortId
    }
    return null
}

/** Direct file from one AniVault button. Embed-only answers are skipped. */
internal fun anivaultPlayable(body: String, label: String, headers: Map<String, String>): PlayableSource? {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
    if (root.optBoolean("iframeOnly")) return null
    val playlist = root.optString("m3u8").trim()
    val file = root.optString("mp4").trim()
    val url = when {
        playlist.startsWith("http://") || playlist.startsWith("https://") -> playlist
        file.startsWith("http://") || file.startsWith("https://") -> file
        else -> return null
    }
    val hls = url == playlist || url.contains(".m3u8", ignoreCase = true)
    return PlayableSource(
        url = url,
        headers = headers,
        label = label,
        hls = hls,
        audioLanguage = null,
    )
}

private fun anivaultNorm(value: String): String =
    value.lowercase()
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

private val ANIVAULT_SUB = listOf(
    AniVaultButton("anikoto_stream.php", "Hd-1", "Hd-1", "sub"),
    AniVaultButton("anikoto_stream.php", "Vidstream-2", "Vidstream-2", "sub"),
    AniVaultButton("reanime_stream.php", "Hd-2", "Hd-2", "sub"),
    AniVaultButton("aniwaves_stream.php", "Vidplay", "Vidplay", "sub"),
    AniVaultButton("aniwaves_stream.php", "BYFMS", "BYFMS", "sub"),
    AniVaultButton("animeheaven_stream.php", null, "AnimeHeaven", "sub"),
    AniVaultButton("anizone_stream.php", "Japanese", "Japanese (Zone)", "sub"),
    AniVaultButton("animesalt_stream.php", "Japanese", "Japanese", "sub"),
    AniVaultButton("animenosub_stream.php", "Sub - Moon", "Moon", "sub"),
    AniVaultButton("animenosub_stream.php", "Sub - Omega", "Omega", "sub"),
    AniVaultButton("animenosub_stream.php", "Sub - Nova", "Nova", "sub"),
    AniVaultButton("animenosub_stream.php", "Sub - Turbo", "Turbo", "sub"),
)

private val ANIVAULT_DUB = listOf(
    AniVaultButton("anikoto_stream.php", "Hd-1", "Hd-1", "dub"),
    AniVaultButton("anikoto_stream.php", "Vidstream-2", "Vidstream-2", "dub"),
    AniVaultButton("reanime_stream.php", "Hd-2", "Hd-2", "dub"),
    AniVaultButton("aniwaves_stream.php", "Vidplay", "Vidplay", "dub"),
    AniVaultButton("aniwaves_stream.php", "BYFMS", "BYFMS", "dub"),
    AniVaultButton("anizone_stream.php", "English", "English (Zone)", "dub"),
    AniVaultButton("animesalt_stream.php", "English", "English", "dub"),
    AniVaultButton("animenosub_stream.php", "Dub - Moon", "Moon", "dub"),
    AniVaultButton("animenosub_stream.php", "Dub - Omega", "Omega", "dub"),
    AniVaultButton("animenosub_stream.php", "Dub - Nova", "Nova", "dub"),
    AniVaultButton("animenosub_stream.php", "Dub - Turbo", "Turbo", "dub"),
)

private val ANIVAULT_HINDI = listOf(
    AniVaultButton("animesalt_stream.php", "Hindi", "Hindi", "dub"),
    AniVaultButton("desidub_stream.php", "Abyssdub", "Abyss", "dub"),
    AniVaultButton("desidub_stream.php", "VMolydub", "VMoly", "dub"),
    AniVaultButton("desidub_stream.php", "Mirrordub", "Mirror", "dub"),
)
