// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.download

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import site.anikitty.app.data.servers.AnipmXorDataSource
import site.anikitty.app.data.servers.FlixUnwrapDataSource
import site.anikitty.app.data.servers.GotakuObfDataSource
import site.anikitty.app.data.servers.PlaybackHttp
import site.anikitty.app.data.servers.SegmentStripDataSource
import site.anikitty.app.data.servers.isHlsMediaSegment
import site.anikitty.app.data.servers.segmentCacheIdentity
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * One cache for every saved HLS episode. The worker and the player share it,
 * so a finished download plays with no network when the segment is already there.
 * Keys are prefixed per episode so Remove drops only that episode.
 */
@UnstableApi
internal object EpisodeDownloadCache {
    private val lock = Any()
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = synchronized(lock) {
        cache ?: SimpleCache(
            File(context.applicationContext.filesDir, "episode-cache"),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }
}

@UnstableApi
internal fun episodeCacheKey(anilistId: Int, episode: Int): CacheKeyFactory =
    CacheKeyFactory { spec ->
        val raw = spec.key ?: spec.uri.toString()
        val identity = if (isHlsMediaSegment(raw)) segmentCacheIdentity(raw) else raw
        episodeCachePrefix(anilistId, episode) + identity
    }

@UnstableApi
internal fun upstreamFactory(
    headers: Map<String, String>,
    userAgent: String,
    stripBytes: Int,
    pngXor: Boolean = false,
): DataSource.Factory {
    val http = PlaybackHttp.factory(headers, userAgent)
    val gotaku = GotakuObfDataSource.Factory(http)
    val flix = FlixUnwrapDataSource.Factory(gotaku)
    val xor = AnipmXorDataSource.Factory(flix, AtomicBoolean(pngXor))
    return SegmentStripDataSource.Factory(
        xor,
        stripBytes = AtomicInteger(
            if (pngXor) 0 else stripBytes.coerceAtLeast(0),
        ),
    )
}

@UnstableApi
internal fun cacheDataSourceFactory(
    context: Context,
    anilistId: Int,
    episode: Int,
    upstream: DataSource.Factory,
): CacheDataSource.Factory =
    CacheDataSource.Factory()
        .setCache(EpisodeDownloadCache.get(context))
        .setCacheKeyFactory(episodeCacheKey(anilistId, episode))
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

/** Papa / ani.pm HLS — playlist or PNG segment leaf. */
internal fun downloadUsesPngXor(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains(".direct.pls") ||
        lower.contains("/png/") ||
        lower.endsWith(".png")
}

@UnstableApi
internal fun hlsDownloader(
    context: Context,
    anilistId: Int,
    episode: Int,
    url: String,
    headers: Map<String, String>,
    userAgent: String,
    stripBytes: Int,
    streamKeys: List<StreamKey> = emptyList(),
): HlsDownloader {
    val pngXor = downloadUsesPngXor(url)
    val upstream = upstreamFactory(headers, userAgent, stripBytes, pngXor = pngXor)
    val item = MediaItem.Builder()
        .setUri(url)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .apply { if (streamKeys.isNotEmpty()) setStreamKeys(streamKeys) }
        .build()
    return HlsDownloader(item, cacheDataSourceFactory(context, anilistId, episode, upstream))
}

/** Stream keys for a saved plan. Empty means the player and the saver use the whole playlist. */
@UnstableApi
internal fun hlsStreamKeys(raw: String?): List<StreamKey> {
    val plan = decodeHlsStreams(raw) ?: return emptyList()
    val keys = mutableListOf(
        StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, plan.variantIndex),
    )
    plan.audioIndices.forEach { index ->
        keys += StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_AUDIO, index)
    }
    return keys
}

@UnstableApi
internal fun hlsPlanFromPlaylist(playlist: HlsMultivariantPlaylist): HlsSavePlan? {
    if (playlist.variants.isEmpty()) return null
    return chooseHlsSavePlan(
        variants = playlist.variants.map { variant ->
            HlsVariantFacts(
                height = variant.format.height.takeIf { it > 0 } ?: 0,
                bandwidth = variant.format.bitrate.takeIf { it > 0 } ?: 0,
                audioGroupId = variant.audioGroupId,
                trickPlay = (variant.format.roleFlags and C.ROLE_FLAG_TRICK_PLAY) != 0,
            )
        },
        audios = playlist.audios.map { audio ->
            HlsAudioFacts(groupId = audio.groupId)
        },
    )
}

@UnstableApi
internal fun removeSavedBytes(context: Context, row: EpisodeDownloadEntity, keepSegments: Boolean = false) {
    row.localPath?.let { path ->
        File(path).delete()
        File("$path.part").delete()
    }
    progressiveLeftovers(
        EpisodeDownloads.filesRoot(context),
        row.anilistId,
        row.episode,
        row.url,
        row.hls,
    ).forEach { it.delete() }
    if (!row.hls || keepSegments) return
    val headers = decodeHeaders(row.headersJson)
    val userAgent = headers["User-Agent"] ?: DOWNLOAD_USER_AGENT
    try {
        hlsDownloader(
            context,
            row.anilistId,
            row.episode,
            row.url,
            headers,
            userAgent,
            row.stripBytes,
        ).remove()
    } catch (_: Exception) {
        // The playlist may already be gone. The prefix sweep still drops segments.
    }
    val cache: Cache = EpisodeDownloadCache.get(context)
    val prefix = episodeCachePrefix(row.anilistId, row.episode)
    cache.keys.filter { it.startsWith(prefix) }.toList().forEach { cache.removeResource(it) }
}
