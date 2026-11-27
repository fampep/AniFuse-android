// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Cheap HEAD/GET probe so a warm next-episode URL that already 403s is dropped
 * before autoplay hands it to Media3.
 */
internal object PlayableWarmProbe {
    suspend fun alive(source: PlayableSource, timeoutMs: Long = 2_500L): Boolean =
        withContext(Dispatchers.IO) {
            val client = PlaybackHttp.client.newBuilder()
                .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            val head = Request.Builder()
                .url(source.url)
                .head()
                .apply {
                    source.headers.forEach { (k, v) -> header(k, v) }
                }
                .build()
            runCatching {
                client.newCall(head).execute().use { response ->
                    if (response.code == 405 || response.code == 501) {
                        return@use getAlive(client, source)
                    }
                    response.isSuccessful || response.code in 300..399
                }
            }.getOrElse {
                // Some CDNs reject HEAD; fall back to a ranged GET.
                runCatching { getAlive(client, source) }.getOrDefault(false)
            }
        }

    private fun getAlive(client: okhttp3.OkHttpClient, source: PlayableSource): Boolean {
        val get = Request.Builder()
            .url(source.url)
            .get()
            .header("Range", "bytes=0-1")
            .apply {
                source.headers.forEach { (k, v) -> header(k, v) }
            }
            .build()
        return client.newCall(get).execute().use { response ->
            response.isSuccessful || response.code == 206 || response.code in 300..399
        }
    }

    /**
     * Keep sources whose first URL still answers. Empty means the warm cache
     * must be discarded and the player should re-resolve.
     */
    suspend fun filterAlive(sources: List<PlayableSource>): List<PlayableSource> {
        if (sources.isEmpty()) return emptyList()
        val first = sources.first()
        return if (alive(first)) sources else emptyList()
    }
}
