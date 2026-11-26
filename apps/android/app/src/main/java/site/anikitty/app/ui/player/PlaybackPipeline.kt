// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import site.anikitty.app.data.download.DOWNLOAD_USER_AGENT
import site.anikitty.app.data.download.resolvedStripBytes
import site.anikitty.app.data.servers.FlixUnwrapDataSource
import site.anikitty.app.data.servers.GotakuObfDataSource
import site.anikitty.app.data.servers.PlaybackHttp
import site.anikitty.app.data.servers.PlayableSource
import site.anikitty.app.data.servers.SegmentStripDataSource
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * One data-source chain for the life of the player.
 * A CDN 403 swaps the media item; the factories stay and read these holders,
 * so Referer, Gotaku, and Flix follow the new URL without releasing ExoPlayer.
 */
@OptIn(UnstableApi::class)
internal class PlaybackPipeline {
    private val stripBytes = AtomicInteger(SegmentStripDataSource.DEFAULT_STRIP_BYTES)
    private val obf = AtomicReference(null as site.anikitty.app.data.servers.GotakuObf?)
    private val flix = AtomicReference(null as site.anikitty.app.data.servers.FlixUnwrap?)
    val http: OkHttpDataSource.Factory = OkHttpDataSource.Factory(PlaybackHttp.client)
    val gotaku: GotakuObfDataSource.Factory = GotakuObfDataSource.Factory(http, obf)
    val flixFactory: FlixUnwrapDataSource.Factory = FlixUnwrapDataSource.Factory(gotaku, flix)
    val strip: SegmentStripDataSource.Factory = SegmentStripDataSource.Factory(
        upstreamFactory = flixFactory,
        stripBytes = stripBytes,
    )

    fun apply(playing: PlayableSource) {
        val agent = playing.headers["User-Agent"]?.takeIf { it.isNotBlank() } ?: DOWNLOAD_USER_AGENT
        http.setUserAgent(agent)
        http.setDefaultRequestProperties(playing.headers)
        obf.set(playing.gotakuObf)
        flix.set(playing.flix)
        stripBytes.set(resolvedStripBytes(playing))
    }
}
