// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Last-good provider + CDN host for a specific episode.
 * The next open of that episode burns the race head-start on a mirror that
 * already played, instead of cold-probing every site again.
 */
class EpisodeHostAffinity(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, Hit>()

    data class Hit(
        val providerId: String,
        val host: String,
        val atMs: Long,
    )

    fun recall(anilistId: Int, episode: Int, audio: String, nowMs: Long = System.currentTimeMillis()): Hit? {
        if (anilistId <= 0 || episode <= 0) return null
        val key = key(anilistId, episode, audio)
        memory[key]?.let { hit ->
            if (nowMs - hit.atMs <= TTL_MS) return hit
            memory.remove(key)
        }
        val provider = prefs.getString("$key.provider", null)?.takeIf { it.isNotBlank() } ?: return null
        val host = prefs.getString("$key.host", null)?.takeIf { it.isNotBlank() } ?: return null
        val at = prefs.getLong("$key.at", 0L)
        if (at <= 0L || nowMs - at > TTL_MS) {
            prefs.edit().remove("$key.provider").remove("$key.host").remove("$key.at").apply()
            return null
        }
        return Hit(providerId = provider, host = host, atMs = at).also { memory[key] = it }
    }

    fun remember(
        anilistId: Int,
        episode: Int,
        audio: String,
        providerId: String,
        host: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (anilistId <= 0 || episode <= 0) return
        val provider = providerId.trim().lowercase()
        val cdn = host.trim().lowercase()
        if (provider.isEmpty() || cdn.isEmpty()) return
        val key = key(anilistId, episode, audio)
        val hit = Hit(providerId = provider, host = cdn, atMs = nowMs)
        memory[key] = hit
        prefs.edit()
            .putString("$key.provider", provider)
            .putString("$key.host", cdn)
            .putLong("$key.at", nowMs)
            .apply()
    }

    companion object {
        private const val PREFS = "anikitty_episode_affinity"
        /** Affinity stays hot across a binge night, then expires. */
        internal const val TTL_MS = 18L * 60L * 60L * 1000L

        internal fun key(anilistId: Int, episode: Int, audio: String): String {
            val mode = if (audio.equals("dub", ignoreCase = true)) "dub" else "sub"
            return "$anilistId:$episode:$mode"
        }
    }
}

/**
 * Keep the preferred head. Pull the affinity provider to second place so the
 * parallel window opens on a mirror that already played this episode.
 */
internal fun <T> boostAffinityProvider(
    ordered: List<T>,
    affinityProviderId: String?,
    id: (T) -> String,
): List<T> {
    val want = affinityProviderId?.trim()?.lowercase().orEmpty()
    if (want.isEmpty() || ordered.size < 2) return ordered
    val head = ordered.first()
    if (id(head).trim().lowercase() == want) return ordered
    val match = ordered.firstOrNull { id(it).trim().lowercase() == want } ?: return ordered
    return listOf(head) + match + ordered.drop(1).filterNot { id(it).trim().lowercase() == want }
}
