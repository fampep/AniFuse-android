// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import site.anikitty.app.data.extensions.ExtensionHttp
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Human-readable failure from the AniFuse resolver API. */
class AnifuseApiException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/**
 * Native client for the AniFuse resolver API.
 *
 * `/v1/resolve` playback requests travel through the bundled extension
 * resolver (same protocol); this class fronts the discovery and health
 * endpoints the player does not need: provider registry, aggregated latest
 * feed, and liveness. Responses are cached with short TTLs that mirror the
 * server's own cache so the UI never hammers the host.
 */
class AnifuseApi(private val store: AnifuseApiStore) {

    private data class Cached(val at: Long, val value: Any?)
    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun health(): AnifuseHealth = cached("health", HEALTH_TTL_MS) {
        val (code, body) = get("/healthz")
        if (code !in 200..299) fail(code, body)
        healthFromJson(JSONObject(body))
    }

    suspend fun providers(forceRefresh: Boolean = false): List<AnifuseProvider> =
        cached("providers", PROVIDERS_TTL_MS, forceRefresh) {
            val (code, body) = get("/v1/providers")
            if (code !in 200..299) fail(code, body)
            providersFromJson(JSONObject(body))
        }

    /**
     * Aggregated newest-episodes feed. [provider] pins one site, [search]
     * filters titles case-insensitively, [sort] is `ep`, `name`, or `mixed`.
     */
    suspend fun latest(
        provider: String? = null,
        search: String? = null,
        limit: Int = 40,
        sort: String? = null,
        forceRefresh: Boolean = false,
    ): List<AnifuseLatestItem> {
        val params = buildList {
            provider?.takeIf { it.isNotBlank() }?.let { add("provider=$it") }
            search?.takeIf { it.isNotBlank() }?.let { add("search=$it") }
            if (limit > 0) add("limit=${limit.coerceAtMost(200)}")
            sort?.takeIf { it.isNotBlank() }?.let { add("sort=$it") }
        }
        val path = if (params.isEmpty()) "/v1/latest" else "/v1/latest?${params.joinToString("&")}"
        val key = "latest:$path"
        return cached(key, LATEST_TTL_MS, forceRefresh) {
            val (code, body) = get(path)
            if (code !in 200..299) fail(code, body)
            latestFromJson(JSONObject(body))
        }
    }

    /** All feeds expire at once when the endpoint changes. */
    fun invalidate() = cache.clear()

    private suspend fun <T> cached(
        key: String,
        ttlMs: Long,
        forceRefresh: Boolean = false,
        load: suspend () -> T,
    ): T {
        val now = System.currentTimeMillis()
        val hit = cache[key]
        if (!forceRefresh && hit != null && now - hit.at < ttlMs) {
            @Suppress("UNCHECKED_CAST")
            return hit.value as T
        }
        val value = load()
        cache[key] = Cached(now, value)
        return value
    }

    private suspend fun get(path: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val base = store.baseUrl.value
            val url = "$base$path".toHttpUrlOrNull()
                ?: throw AnifuseApiException("Invalid AniFuse API address: $base")
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "AniFuse-ExtensionClient/1")
                .get()
                .build()
            try {
                ExtensionHttp.client.newCall(req).execute().use { resp ->
                    resp.code to resp.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                throw AnifuseApiException(
                    "Can't reach the AniFuse API at $base — ${e.message ?: "network error"}",
                    e,
                )
            }
        }

    private fun fail(code: Int, body: String): Nothing {
        val msg = runCatching { JSONObject(body).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        throw AnifuseApiException(msg ?: "AniFuse API HTTP $code")
    }

    private companion object {
        const val HEALTH_TTL_MS = 15_000L
        const val PROVIDERS_TTL_MS = 30_000L
        const val LATEST_TTL_MS = 3 * 60_000L
    }
}
