// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anifuse

import org.json.JSONArray
import org.json.JSONObject

/**
 * Response models for the AniFuse resolver API
 * (https://github.com/fampep/anifuse). The server is a self-hosted Go
 * service; these mirror its JSON wire format.
 */

/** `/healthz` — liveness of the resolver and its provider registry. */
data class AnifuseHealth(
    val status: String,
    val providers: Int,
    val uptimeSeconds: Long,
    val demo: Boolean,
    val unhealthy: Int,
) {
    val ok: Boolean get() = status.equals("ok", ignoreCase = true)
}

internal fun healthFromJson(root: JSONObject): AnifuseHealth = AnifuseHealth(
    status = root.optString("status", ""),
    providers = root.optInt("providers", 0),
    uptimeSeconds = root.optLong("uptimeSeconds", 0L),
    demo = root.optBoolean("demo", false),
    unhealthy = root.optInt("unhealthy", 0),
)

/** One entry of `/v1/providers` — a bundled resolver site with live health. */
data class AnifuseProvider(
    val id: String,
    val name: String,
    val siteUrl: String?,
    val bundled: Boolean,
    val status: String,
    val successRate: Double,
    val attempts: Int,
    val cooldownSeconds: Int,
) {
    val healthy: Boolean get() = status.equals("ok", ignoreCase = true)
    val degraded: Boolean get() = status.equals("degraded", ignoreCase = true)
    val down: Boolean get() = status.equals("down", ignoreCase = true)
    val ratePercent: Int get() = (successRate * 100).toInt().coerceIn(0, 100)
}

internal fun providerFromJson(obj: JSONObject): AnifuseProvider = AnifuseProvider(
    id = obj.optString("id", ""),
    name = obj.optString("name", ""),
    siteUrl = obj.optString("siteUrl").takeIf { it.isNotBlank() },
    bundled = obj.optBoolean("bundled", false),
    status = obj.optString("status", ""),
    successRate = obj.optDouble("successRate", 0.0),
    attempts = obj.optInt("attempts", 0),
    cooldownSeconds = obj.optInt("cooldownSeconds", 0),
)

internal fun providersFromJson(root: JSONObject): List<AnifuseProvider> {
    val arr = root.optJSONArray("providers") ?: JSONArray()
    return buildList {
        for (i in 0 until arr.length()) {
            add(providerFromJson(arr.getJSONObject(i)))
        }
    }.sortedWith(compareByDescending<AnifuseProvider> { it.healthy }
        .thenByDescending { it.successRate }
        .thenBy { it.name.lowercase() })
}

/** One row of the aggregated `/v1/latest` newest-episodes feed. */
data class AnifuseLatestItem(
    val provider: String,
    val title: String,
    val episode: Int,
    val imageUrl: String?,
    val siteUrl: String?,
    val updatedAt: String?,
)

internal fun latestFromJson(root: JSONObject): List<AnifuseLatestItem> {
    val arr = root.optJSONArray("latest") ?: JSONArray()
    return buildList {
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            add(
                AnifuseLatestItem(
                    provider = obj.optString("provider", ""),
                    title = obj.optString("title", "").trim(),
                    episode = obj.optInt("episode", 0),
                    imageUrl = obj.optString("imageUrl").takeIf { it.isNotBlank() },
                    siteUrl = obj.optString("siteUrl").takeIf { it.isNotBlank() },
                    updatedAt = obj.optString("updatedAt").takeIf { it.isNotBlank() },
                ),
            )
        }
    }
}
