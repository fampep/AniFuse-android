// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import site.anikitty.app.data.extensions.ExtensionHttp
import java.io.File
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
class AnifuseApi(
    private val store: AnifuseApiStore,
    diskRoot: File? = null,
) {

    private data class Cached(val at: Long, val value: Any?)
    private val cache = ConcurrentHashMap<String, Cached>()
    private val disk = diskRoot?.let { AnifuseFeedCache(it) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Unit>>()

    suspend fun health(): AnifuseHealth = cached("health", HEALTH_TTL_MS) {
        val (code, body) = get("/healthz")
        if (code !in 200..299) fail(code, body)
        healthFromJson(JSONObject(body))
    }

    suspend fun providers(forceRefresh: Boolean = false): List<AnifuseProvider> {
        val key = "providers"
        fresh<List<AnifuseProvider>>(key, PROVIDERS_TTL_MS, forceRefresh)?.let { return it }
        staleBody("providers.json", AnifuseFeedCache.STALE_FEED_MS, forceRefresh)?.let { body ->
            val parsed = providersFromJson(JSONObject(body))
            rememberProviders(parsed, diskAge("providers.json"))
            refreshLater(key) { loadProviders() }
            return parsed
        }
        return loadProviders()
    }

    private suspend fun loadProviders(): List<AnifuseProvider> {
        val (code, body) = get("/v1/providers")
        if (code !in 200..299) fail(code, body)
        disk?.write("providers.json", body)
        val parsed = providersFromJson(JSONObject(body))
        disk?.writeDown(parsed.filter { it.down }.flatMap { listOf(it.id, it.name) })
        cache["providers"] = Cached(System.currentTimeMillis(), parsed)
        return parsed
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
        val diskName = "latest-${path.hashCode().toUInt().toString(16)}.json"
        fresh<List<AnifuseLatestItem>>(key, LATEST_TTL_MS, forceRefresh)?.let { return it }
        staleBody(diskName, AnifuseFeedCache.STALE_FEED_MS, forceRefresh)?.let { body ->
            val parsed = latestFromJson(JSONObject(body))
            val age = diskAge(diskName)
            cache[key] = Cached(System.currentTimeMillis() - age, parsed)
            if (age >= LATEST_TTL_MS) refreshLater(key) { loadLatest(path, key, diskName) }
            return parsed
        }
        return loadLatest(path, key, diskName)
    }

    private suspend fun loadLatest(path: String, key: String, diskName: String): List<AnifuseLatestItem> {
        val (code, body) = get(path)
        if (code !in 200..299) fail(code, body)
        disk?.write(diskName, body)
        val parsed = latestFromJson(JSONObject(body))
        cache[key] = Cached(System.currentTimeMillis(), parsed)
        return parsed
    }

    /** All feeds expire at once when the endpoint changes. */
    fun invalidate() {
        cache.clear()
        disk?.clear()
    }

    private fun <T> fresh(key: String, ttlMs: Long, forceRefresh: Boolean): T? {
        if (forceRefresh) return null
        val hit = cache[key] ?: return null
        if (System.currentTimeMillis() - hit.at >= ttlMs) return null
        @Suppress("UNCHECKED_CAST")
        return hit.value as T
    }

    private fun staleBody(name: String, maxAgeMs: Long, forceRefresh: Boolean): String? {
        if (forceRefresh) return null
        return disk?.read(name, maxAgeMs)
    }

    private fun diskAge(name: String): Long = disk?.ageMs(name) ?: 0L

    private fun rememberProviders(parsed: List<AnifuseProvider>, age: Long) {
        cache["providers"] = Cached(System.currentTimeMillis() - age, parsed)
    }

    private fun refreshLater(key: String, block: suspend () -> Unit) {
        val existing = refreshing[key]
        if (existing != null && existing.isActive) return
        val job = scope.async {
            try {
                block()
            } catch (_: Exception) {
            } finally {
                refreshing.remove(key)
            }
        }
        val raced = refreshing.putIfAbsent(key, job)
        if (raced != null && raced !== job) job.cancel()
    }

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
