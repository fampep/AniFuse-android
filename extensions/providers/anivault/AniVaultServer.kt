// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import site.anikitty.app.data.skip.malIdFromTitleHint

/**
 * On-device AniVault.
 *
 * Playback asks anivault.co for every server button the site has.
 * Each answer is kept, in the site's order, so the player can move to the
 * next AniVault server. Other sites are not used. Their key stays on their
 * server; the phone only calls the public buttons.
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
        description = "Every AniVault server. The file plays on this phone.",
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
        val lead = AniVaultLead.preferred(anilistId, audio)
        val buttons = anivaultLeadFirst(anivaultButtons(audio), lead)
        val avoid = coroutineContext[AvoidPlaybackHost]?.host
        val hits = CopyOnWriteArrayList<Pair<Int, PlayableSource>>()
        val asked = ConcurrentHashMap.newKeySet<String>()
        buttons.forEach { asked += anivaultAskKey(it) }
        coroutineScope {
            val pending = CopyOnWriteArrayList(buttons.mapIndexed { index, button -> index to button })
            var wave = 0
            while (pending.isNotEmpty() && wave < 3) {
                wave += 1
                val batch = pending.toList()
                pending.clear()
                batch.map { (order, button) ->
                    async {
                        val body = try {
                            read(anivaultStreamUrl(API, button, shortId, episode))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        } ?: return@async
                        anivaultPlayable(
                            body,
                            button.label,
                            playbackHeaders(),
                            leadKey = anivaultAskKey(button),
                        )?.let { hits += order to it }
                        anivaultListedServers(body).forEach { name ->
                            val extra = button.copy(server = name, label = name)
                            if (asked.add(anivaultAskKey(extra))) pending += (order + 1_000) to extra
                        }
                    }
                }.awaitAll()
            }
        }
        val ordered = anivaultLeadSources(anivaultOrdered(hits), lead)
        val accepted = sourcesAvoidingHost(ordered, avoid).ifEmpty { ordered }
        if (accepted.isEmpty()) error("AniVault could not find this episode")
        return@withContext namedByServer(accepted, server.name)
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
        val response = try {
            http.newCall(request.build()).execute()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
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
    audio.equals("dub", ignoreCase = true) -> ANIVAULT_DUB + ANIVAULT_HINDI
    else -> ANIVAULT_SUB
}

internal fun anivaultAskKey(button: AniVaultButton): String =
    "${button.endpoint}|${button.audio}|${button.server.orEmpty().trim().lowercase()}"

/** The server that played this show last, then every other server in site order. */
internal fun anivaultLeadFirst(buttons: List<AniVaultButton>, leadKey: String?): List<AniVaultButton> {
    if (leadKey.isNullOrBlank()) return buttons
    val index = buttons.indexOfFirst { anivaultAskKey(it) == leadKey }
    if (index <= 0) return buttons
    val lead = buttons[index]
    return buildList(buttons.size) {
        add(lead)
        buttons.forEachIndexed { i, button -> if (i != index) add(button) }
    }
}

/** Same order for the files that actually came back. A missing lead stays out. */
internal fun anivaultLeadSources(sources: List<PlayableSource>, leadKey: String?): List<PlayableSource> {
    if (leadKey.isNullOrBlank()) return sources
    val index = sources.indexOfFirst { it.leadKey == leadKey }
    if (index <= 0) return sources
    val lead = sources[index]
    return buildList(sources.size) {
        add(lead)
        sources.forEachIndexed { i, source -> if (i != index) add(source) }
    }
}

/** Keep every file AniVault returned, in the order their buttons are drawn. */
internal fun anivaultOrdered(hits: List<Pair<Int, PlayableSource>>): List<PlayableSource> {
    val seen = HashSet<String>()
    return hits.sortedBy { it.first }.map { it.second }.filter { seen.add(it.url) }
}

/**
 * AniVault is the only racer when it is selected, so playback stays on
 * their servers. Otherwise it stays out and the chosen site runs alone.
 */
internal fun playbackResolvers(selectedId: String, resolvers: List<StreamResolver>): List<StreamResolver> {
    if (selectedId == AniVaultServer.ID) {
        val vault = resolvers.filter { it.server.id == AniVaultServer.ID }
        return if (vault.isEmpty()) resolvers else vault
    }
    return resolvers.filter { it.server.id != AniVaultServer.ID }
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

/** Server names AniVault listed on this answer, strings or `{name}` objects. */
internal fun anivaultListedServers(body: String): List<String> {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
    val rows = root.optJSONArray("servers") ?: return emptyList()
    val names = LinkedHashSet<String>()
    for (index in 0 until rows.length()) {
        when (val item = rows.opt(index)) {
            is String -> item.trim().takeIf { it.isNotEmpty() }?.let(names::add)
            is JSONObject -> {
                val name = item.optString("name").ifBlank { item.optString("server") }.trim()
                if (name.isNotEmpty()) names += name
            }
        }
    }
    val current = root.optString("server").trim()
    if (current.isNotEmpty()) names += current
    return names.toList()
}

/** Direct file from one AniVault button. Embed-only answers are skipped. */
internal fun anivaultPlayable(
    body: String,
    label: String,
    headers: Map<String, String>,
    leadKey: String? = null,
): PlayableSource? {
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
        leadKey = leadKey,
    )
}

/**
 * Which AniVault server played a show. The next episode asks that server
 * first. The file sits beside host health so a cache clear does not wipe it.
 */
internal object AniVaultLead {
    private val memory = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val lock = Any()
    private var file: java.io.File? = null

    fun attach(dir: java.io.File) {
        synchronized(lock) {
            dir.mkdirs()
            file = java.io.File(dir, "anivault-lead.txt")
            loadLocked()
        }
    }

    fun reset() {
        synchronized(lock) {
            memory.clear()
            file?.delete()
        }
    }

    fun note(anilistId: Int, audio: String, leadKey: String) {
        if (anilistId <= 0 || leadKey.isBlank()) return
        synchronized(lock) {
            memory[slot(anilistId, audio)] = leadKey.trim()
            saveLocked()
        }
    }

    fun preferred(anilistId: Int, audio: String): String? = memory[slot(anilistId, audio)]

    private fun slot(anilistId: Int, audio: String) = "$anilistId\t${audio.trim().lowercase()}"

    private fun loadLocked() {
        val text = runCatching { file?.takeIf { it.isFile }?.readText() }.getOrNull().orEmpty()
        memory.clear()
        text.lineSequence().forEach { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size == 3 && parts[0].isNotBlank() && parts[2].isNotBlank()) {
                memory["${parts[0]}\t${parts[1]}"] = parts[2]
            }
        }
    }

    private fun saveLocked() {
        val target = file ?: return
        val body = memory.entries.joinToString("\n") { (slot, key) ->
            val id = slot.substringBefore('\t')
            val mode = slot.substringAfter('\t')
            "$id\t$mode\t$key"
        }
        runCatching {
            val tmp = java.io.File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(body)
            if (!tmp.renameTo(target)) {
                target.writeText(body)
                tmp.delete()
            }
        }
    }
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
    AniVaultButton("watchanimeworld_stream.php", null, "WatchAnimeWorld", "sub"),
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
    AniVaultButton("watchanimeworld_stream.php", null, "WatchAnimeWorld", "dub"),
)

private val ANIVAULT_HINDI = listOf(
    AniVaultButton("animesalt_stream.php", "Hindi", "Hindi", "dub"),
    AniVaultButton("desidub_stream.php", "Abyssdub", "Abyss", "dub"),
    AniVaultButton("desidub_stream.php", "VMolydub", "VMoly", "dub"),
    AniVaultButton("desidub_stream.php", "Mirrordub", "Mirror", "dub"),
    AniVaultButton("desidub_stream.php", "Rubydub", "Ruby", "dub"),
    AniVaultButton("desidub_stream.php", "VMoly (Muse)dub", "VMoly (Muse)", "dub"),
    AniVaultButton("desidub_stream.php", "Mirror (Muse)dub", "Mirror (Muse)", "dub"),
    AniVaultButton("desidub_stream.php", "Abyss (Muse)dub", "Abyss (Muse)", "dub"),
    AniVaultButton("desidub_stream.php", "FileMoondub", "FileMoon", "dub"),
    AniVaultButton("desidub_stream.php", "PlayerXdub", "PlayerX", "dub"),
)
