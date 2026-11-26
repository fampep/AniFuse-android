// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import site.anikitty.app.data.download.DOWNLOAD_USER_AGENT
import site.anikitty.app.data.download.resolvedStripBytes
import site.anikitty.app.data.servers.FlixUnwrapDataSource
import site.anikitty.app.data.servers.GotakuObfDataSource
import site.anikitty.app.data.servers.PlaybackHttp
import site.anikitty.app.data.servers.PlayableSource
import site.anikitty.app.data.servers.SegmentStripDataSource
import site.anikitty.app.data.servers.playbackHeaders
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * One data-source chain for the life of the player.
 * A CDN 403 swaps the media item; the factories stay and read these holders,
 * so Referer, Gotaku, and Flix follow the new URL without releasing ExoPlayer.
 * Cookies set by the playlist host are kept for its segments and cleared when
 * the URL changes.
 */
@OptIn(UnstableApi::class)
internal class PlaybackPipeline {
    private val stripBytes = AtomicInteger(SegmentStripDataSource.DEFAULT_STRIP_BYTES)
    private val obf = AtomicReference(null as site.anikitty.app.data.servers.GotakuObf?)
    private val flix = AtomicReference(null as site.anikitty.app.data.servers.FlixUnwrap?)
    private val cookies = PlaybackCookieJar()
    private var appliedUrl: String? = null
    private val client = PlaybackHttp.client.newBuilder().cookieJar(cookies).build()
    val http: OkHttpDataSource.Factory = OkHttpDataSource.Factory(client)
    val gotaku: GotakuObfDataSource.Factory = GotakuObfDataSource.Factory(http, obf)
    val flixFactory: FlixUnwrapDataSource.Factory = FlixUnwrapDataSource.Factory(gotaku, flix)
    val strip: SegmentStripDataSource.Factory = SegmentStripDataSource.Factory(
        upstreamFactory = flixFactory,
        stripBytes = stripBytes,
    )

    fun apply(playing: PlayableSource) {
        if (appliedUrl != playing.url) {
            cookies.clear()
            appliedUrl = playing.url
        }
        val agent = playing.headers["User-Agent"]?.takeIf { it.isNotBlank() } ?: DOWNLOAD_USER_AGENT
        http.setUserAgent(agent)
        http.setDefaultRequestProperties(playbackHeaders(playing.headers))
        obf.set(playing.gotakuObf)
        flix.set(playing.flix)
        stripBytes.set(resolvedStripBytes(playing))
    }
}

/** In-memory cookies for the source that is playing. Cleared on a new URL. */
internal class PlaybackCookieJar : CookieJar {
    private val lock = Any()
    private val cookies = HashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val kept = this.cookies[url.host].orEmpty().filter { old ->
                cookies.none { it.name == old.name }
            }
            this.cookies[url.host] = kept + cookies
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        cookies[url.host].orEmpty().filter { it.matches(url) }
    }

    fun clear() {
        synchronized(lock) { cookies.clear() }
    }
}
