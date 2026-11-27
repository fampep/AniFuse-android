// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

/**
 * Hard vs soft resolve failures.
 *
 * Soft ("does not have this episode") must not sink a provider for later
 * titles. Hard ("could not be reached", challenge, timeout) soft-blocks the
 * next race so healthy mirrors lead.
 */
internal fun isHardProviderFailure(message: String?): Boolean {
    val msg = message.orEmpty().lowercase()
    if (msg.isBlank()) return false
    if ("does not have" in msg) return false
    if ("needs a title" in msg) return false
    if ("only has subbed" in msg) return false
    if ("only plays this on their site" in msg) return false
    return "could not be reached" in msg ||
        "blocked" in msg ||
        "timeout" in msg ||
        "empty sources" in msg ||
        " 403" in " $msg" ||
        "http 403" in msg ||
        " 503" in " $msg" ||
        "http 503" in msg ||
        "challenge" in msg ||
        "cloudflare" in msg ||
        "just a moment" in msg
}

/**
 * Map an HTTP status to the provider's missing or blocked copy.
 * 404 is always missing so one miss cannot soft-block the source.
 */
internal fun providerHttpFailure(
    code: Int,
    body: String,
    missing: String,
    blocked: String,
    challenged: Boolean = providerChallenged(code, body),
): String {
    if (challenged) return blocked
    if (code == 404) return missing
    if (code == 0) return blocked
    if (code !in 200..299) return blocked
    return missing
}

internal fun providerChallenged(code: Int, body: String): Boolean {
    val trimmed = body.trimStart()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) return false
    if (code == 403 || code == 503) return true
    val head = trimmed.take(800).lowercase()
    return "just a moment" in head || "cf-browser-verification" in head
}
