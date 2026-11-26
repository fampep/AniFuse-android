// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

/**
 * Headers a resolver may attach to a CDN request.
 * Hop-by-hop and transport headers are dropped: OkHttp already decodes gzip,
 * and a copied Range races the range Media3 adds for a segment.
 */
internal fun playbackHeaders(raw: Map<String, String>): Map<String, String> {
    if (raw.isEmpty()) return emptyMap()
    val out = LinkedHashMap<String, String>(raw.size)
    for ((key, value) in raw) {
        if (value.isBlank()) continue
        val name = key.trim()
        if (name.isEmpty() || blockedPlaybackHeader(name)) continue
        out[name] = value
    }
    return out
}

internal fun blockedPlaybackHeader(name: String): Boolean {
    return when (name.lowercase()) {
        "user-agent",
        "accept-encoding",
        "connection",
        "host",
        "content-length",
        "transfer-encoding",
        "te",
        "upgrade",
        "range",
        "keep-alive",
        -> true
        else -> false
    }
}
