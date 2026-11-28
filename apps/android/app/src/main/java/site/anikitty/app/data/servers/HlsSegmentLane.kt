// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicReference

/**
 * A media segment can move to the shadow CDN without replacing the playlist.
 * The player keeps the same media item, and bytes already cached under the
 * path stay valid because the cache key ignores the host.
 */
internal fun isHlsPlaylistUrl(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#')
    return path.endsWith(".m3u8", ignoreCase = true) || path.endsWith(".m3u", ignoreCase = true)
}

internal fun isHlsMediaSegment(url: String): Boolean {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false
    if (isHlsPlaylistUrl(url)) return false
    val leaf = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
    val ext = leaf.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (ext in SEGMENT_EXTENSIONS) return true
    val path = leaf.lowercase()
    return "segment" in path || path.contains("seg-") || path.contains("seg_")
}

/**
 * Same path on the shadow host. Query and scheme stay. Userinfo is dropped
 * because it belonged to the host that just refused.
 */
internal fun retargetSegmentHost(url: String, shadowHost: String): String? {
    val host = shadowHost.trim().lowercase()
    if (host.isEmpty() || playableHost(url) == host) return null
    val scheme = url.substringBefore("://", missingDelimiterValue = "")
    if (scheme != "http" && scheme != "https") return null
    val after = url.substringAfter("://")
    val authority = after.substringBefore('/')
    val rest = after.substring(authority.length)
    if (!rest.startsWith("/")) return null
    return "$scheme://$host$rest"
}

/** Path identity so a host swap still finds the bytes already in the cache. */
internal fun segmentCacheIdentity(url: String): String {
    val after = url.substringAfter("://", url)
    val path = after.substringAfter('/', missingDelimiterValue = "").substringBefore('?').substringBefore('#')
    return path.ifBlank { url.substringBefore('?') }
}

/**
 * Rewrite only a failed media segment whose host is the one that is playing.
 * A playlist refusal stays untouched so the existing item failover can run.
 */
internal fun segmentRetargetUrl(
    code: Int,
    url: String,
    fromHost: String,
    toHost: String,
): String? {
    if (!isSegmentFailure(code) || !isHlsMediaSegment(url)) return null
    if (playableHost(url) != fromHost.trim().lowercase()) return null
    return retargetSegmentHost(url, toHost)
}

internal fun isSegmentFailure(code: Int): Boolean =
    code == 401 || code == 403 || code == 404 || code == 410 || code == 429 || code in 500..599

internal data class SegmentStandby(
    val fromHost: String,
    val toHost: String,
    val headers: Map<String, String>,
)

/**
 * The warm shadow for the episode on screen. A newer player replaces it.
 * The player that left does not clear a standby the next episode already armed.
 * Downloads use [tryArm] so they never steal a live playback standby.
 */
internal object SegmentShadow {
    private val lock = Any()
    private val standby = AtomicReference<SegmentStandby?>(null)
    private val ownerRef = AtomicReference<Any?>(null)

    fun arm(fromHost: String, toHost: String, headers: Map<String, String>, owner: Any) {
        val from = fromHost.trim().lowercase()
        val to = toHost.trim().lowercase()
        if (from.isEmpty() || to.isEmpty() || from == to) return
        synchronized(lock) {
            standby.set(SegmentStandby(from, to, headers))
            ownerRef.set(owner)
        }
    }

    /**
     * Claim an empty slot only. Returns false when playback (or another owner)
     * already holds the standby — never overwrites.
     */
    fun tryArm(fromHost: String, toHost: String, headers: Map<String, String>, owner: Any): Boolean {
        val from = fromHost.trim().lowercase()
        val to = toHost.trim().lowercase()
        if (from.isEmpty() || to.isEmpty() || from == to) return false
        synchronized(lock) {
            if (ownerRef.get() != null) return false
            standby.set(SegmentStandby(from, to, headers))
            ownerRef.set(owner)
            return true
        }
    }

    fun current(): SegmentStandby? = standby.get()

    fun clear(owner: Any) {
        synchronized(lock) {
            if (ownerRef.get() !== owner) return
            standby.set(null)
            ownerRef.set(null)
        }
    }

    fun reset() {
        synchronized(lock) {
            standby.set(null)
            ownerRef.set(null)
        }
    }
}

/** One retry onto the shadow host. A second refusal is returned to the player. */
internal class SegmentRetargetInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.header(SEGMENT_RETRY) != null) return response
        val armed = SegmentShadow.current() ?: return response
        val rewritten = segmentRetargetUrl(
            code = response.code,
            url = request.url.toString(),
            fromHost = armed.fromHost,
            toHost = armed.toHost,
        ) ?: return response
        response.close()
        val next = request.newBuilder().url(rewritten).header(SEGMENT_RETRY, "1")
        armed.headers.forEach { (key, value) ->
            if (!key.equals("Range", ignoreCase = true) && !key.equals("Host", ignoreCase = true)) {
                next.header(key, value)
            }
        }
        return chain.proceed(next.build())
    }
}

private val SEGMENT_EXTENSIONS = setOf(
    // Papa / ani.pm PNG trailer segments participate in shadow retarget.
    "ts", "m4s", "mp4", "m4v", "m4a", "aac", "cmfv", "cmfa", "mp3", "png",
)

internal const val SEGMENT_RETRY = "X-AniFuse-Segment"
