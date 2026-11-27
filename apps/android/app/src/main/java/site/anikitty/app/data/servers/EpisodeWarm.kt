// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * A warm hit is only useful if the CDN still answers.
 * A dead cached URL is dropped and resolved once more. The second miss returns
 * empty so autoplay races live instead of opening a link we already know is dead.
 */
internal suspend fun warmPlayable(
    existing: List<PlayableSource>?,
    resolve: suspend () -> List<PlayableSource>,
    probe: suspend (List<PlayableSource>) -> List<PlayableSource> = { PlayableWarmProbe.filterAlive(it) },
): List<PlayableSource> {
    if (!existing.isNullOrEmpty()) {
        val alive = probe(existing)
        if (alive.isNotEmpty()) return alive
    }
    val fresh = resolve()
    if (fresh.isEmpty()) return emptyList()
    return probe(fresh)
}

/** Host of a playable URL, lowercased, without port or userinfo. */
internal fun playableHost(url: String): String {
    val afterScheme = url.substringAfter("://", url)
    return afterScheme
        .substringBefore("/")
        .substringBefore("?")
        .substringAfter("@")
        .substringBefore(":")
        .lowercase()
}

/**
 * Backup URLs for a credits-time failure of the file that is already playing.
 * The host that just died is not a backup.
 */
internal fun creditsStandby(sources: List<PlayableSource>, playingHost: String): List<PlayableSource> {
    if (sources.isEmpty()) return emptyList()
    val host = playingHost.trim().lowercase()
    if (host.isEmpty()) return sources
    return sources.filter { source ->
        val candidate = playableHost(source.url)
        candidate.isNotEmpty() && candidate != host
    }
}

/** Encrypted unwraps are not plain HTTP, so a range GET would not warm the CDN. */
internal fun canPrefetch(source: PlayableSource): Boolean {
    if (source.gotakuObf != null || source.flix != null) return false
    val url = source.url
    return url.startsWith("https://") || url.startsWith("http://")
}

/**
 * Pulls the playlist or the first bytes of a file through the shared playback
 * client so DNS, TLS, and the CDN edge are hot before the player asks.
 */
internal object StreamWarmPrefetch {
    suspend fun touch(source: PlayableSource): Boolean {
        if (!canPrefetch(source)) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(source.url)
                playbackHeaders(source.headers).forEach { (key, value) ->
                    request.header(key, value)
                }
                val playlist = source.hls || source.url.contains(".m3u8", ignoreCase = true)
                if (!playlist) request.header("Range", "bytes=0-8191")
                PlaybackHttp.client.newCall(request.build()).execute().use { response ->
                    val ok = response.isSuccessful || response.code == 206
                    if (ok) {
                        val stream = response.body?.byteStream()
                        if (stream != null) {
                            val buffer = ByteArray(8192)
                            var read = 0
                            while (read < buffer.size) {
                                val n = stream.read(buffer, read, buffer.size - read)
                                if (n < 0) break
                                read += n
                            }
                        }
                    }
                    ok
                }
            }.getOrDefault(false)
        }
    }
}
