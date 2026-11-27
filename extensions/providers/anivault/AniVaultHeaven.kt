// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * AniVault's AnimeHeaven button, resolved on the phone.
 *
 * Their catalog id hangs when Hoshika does not answer. This is the same
 * search, episode key, and gate their worker uses, and it does not need that id.
 * The file is a direct mp4. Playback stays on this phone.
 */
internal fun anivaultHeavenFiles(
    http: OkHttpClient,
    titles: List<String>,
    episode: Int,
): List<PlayableSource> {
    if (titles.isEmpty() || episode < 1) return emptyList()
    val jar = linkedMapOf<String, String>()
    var best: AniVaultHeavenHit? = null
    for (title in titles) {
        val page = heavenGet(http, jar, heavenSearchUrl(title), HEAVEN_ORIGIN + "/") ?: continue
        val hit = anivaultHeavenPick(anivaultHeavenSearch(page), title) ?: continue
        if (best == null || hit.score > best.score) best = hit
    }
    val show = best ?: return emptyList()
    val animeUrl = "$HEAVEN_ORIGIN/anime.php?${show.id}"
    val anime = heavenGet(http, jar, animeUrl, HEAVEN_ORIGIN + "/") ?: return emptyList()
    val key = anivaultHeavenEpisodeKey(anime, episode) ?: return emptyList()
    jar["key"] = key
    val gate = heavenGet(http, jar, "$HEAVEN_ORIGIN/gate.php", animeUrl) ?: return emptyList()
    val lead = anivaultAskKey(AniVaultButton("animeheaven_stream.php", null, "AnimeHeaven", "sub"))
    return anivaultHeavenMp4s(gate).mapIndexed { index, url ->
        PlayableSource(
            url = url,
            headers = mapOf(
                "Referer" to "$HEAVEN_ORIGIN/gate.php",
                "User-Agent" to HEAVEN_AGENT,
            ),
            label = if (index == 0) "AnimeHeaven" else "AnimeHeaven ${index + 1}",
            hls = false,
            leadKey = if (index == 0) lead else null,
        )
    }
}

internal data class AniVaultHeavenHit(val id: String, val title: String, val score: Int)

internal fun heavenSearchUrl(title: String): String {
    val query = java.net.URLEncoder.encode(title.trim(), Charsets.UTF_8.name())
    return "$HEAVEN_ORIGIN/fastsearch.php?xhr=1&s=$query"
}

internal fun anivaultHeavenSearch(html: String): List<AniVaultHeavenHit> {
    val found = ArrayList<AniVaultHeavenHit>()
    for (anchor in htmlAnchors(html)) {
        val href = anchorAttr(anchor, "href")
        if (!href.contains("anime.php?", ignoreCase = true)) continue
        val id = href.substringAfter('?', "").trim()
        if (id.isEmpty()) continue
        val fast = Regex("(?is)<[^>]*class\\s*=\\s*[\"'][^\"']*fastname[^\"']*[\"'][^>]*>(.*?)</").find(anchor)?.groupValues?.getOrNull(1)
        val image = Regex("(?i)<img\\b[^>]*>").find(anchor)?.value.orEmpty()
        val title = heavenPlain(fast.orEmpty()).ifBlank { anchorAttr(image, "alt") }
        if (title.isNotBlank()) found += AniVaultHeavenHit(id, title, 0)
    }
    return found
}

internal fun anivaultHeavenPick(hits: List<AniVaultHeavenHit>, query: String): AniVaultHeavenHit? =
    hits.map { it.copy(score = anivaultHeavenScore(query, it.title)) }
        .filter { it.score >= 60 }
        .maxByOrNull { it.score }

internal fun anivaultHeavenScore(query: String, candidate: String): Int {
    val left = heavenFold(query)
    val right = heavenFold(candidate)
    if (left.isEmpty() || right.isEmpty()) return 0
    if (left == right) return 100
    val ratio = minOf(left.length, right.length).toDouble() / maxOf(left.length, right.length)
    if (right.startsWith(left) || left.startsWith(right)) return if (ratio >= 0.6) 80 else 30
    if (right.contains(left) || left.contains(right)) return if (ratio >= 0.6) 60 else 25
    return 0
}

internal fun anivaultHeavenEpisodeKey(html: String, episode: Int): String? {
    for (anchor in htmlAnchors(html)) {
        val opening = anchor.substringBefore('>') + ">"
        val attrs = listOf("onmouseover", "onclick", "href").joinToString(" ") { anchorAttr(opening, it) }
        val key = Regex("""gate[ha]\s*\(\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.getOrNull(1)
            ?: continue
        val number = Regex("(?is)watch2[^\"']*[\"'][^>]*>([0-9]+)").find(anchor)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: continue
        if (number == episode) return key
    }
    return null
}

internal fun anivaultHeavenMp4s(html: String): List<String> {
    if (Regex("(?i)watch\\s+episode\\s+on\\s+animeheaven\\.me").containsMatchIn(heavenPlain(html))) return emptyList()
    return Regex("(?i)<source\\b[^>]*>").findAll(html).map { anchorAttr(it.value, "src") }
        .filter { it.startsWith("http://") || it.startsWith("https://") }
        .filter { runCatching { java.net.URI(it).path.orEmpty().endsWith(".mp4", ignoreCase = true) }.getOrDefault(false) }
        .distinct()
        .toList()
}

internal const val HEAVEN_ORIGIN = "https://animeheaven.me"
internal const val HEAVEN_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private fun heavenGet(http: OkHttpClient, jar: MutableMap<String, String>, url: String, referer: String): String? {
    val request = Request.Builder().url(url).get()
        .header("Accept", "text/html,*/*")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("Referer", referer)
        .header("User-Agent", HEAVEN_AGENT)
    if (jar.isNotEmpty()) {
        request.header("Cookie", jar.entries.joinToString("; ") { (name, value) -> "$name=$value" })
    }
    val call = http.newCall(request.build())
    call.timeout().timeout(8, TimeUnit.SECONDS)
    val response = try {
        call.execute()
    } catch (_: Exception) {
        return null
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
        if (it.code !in 200..299) return null
        return it.body?.string()?.takeIf { body -> body.isNotBlank() }
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
    heavenDecode(value.replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), " ")
        .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim())

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
