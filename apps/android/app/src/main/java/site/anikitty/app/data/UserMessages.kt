// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data

/** Short copy for failures the person can act on. */
fun friendlyFailure(raw: String?): String {
    val msg = raw?.trim().orEmpty()
    if (msg.isEmpty()) return "Something went wrong. Try again."
    val lower = msg.lowercase()
    return when {
        "429" in msg || "rate limit" in lower ->
            "AniList is busy. Wait a moment and try again."
        "timeout" in lower || "timed out" in lower ||
            "failed to connect" in lower || "unable to resolve" in lower ||
            "network" in lower || "connection reset" in lower ->
            "No connection. Check the network and try again."
        msg.startsWith("All installed providers failed") && msg.length > 160 ->
            "None of the sources have this episode. Try again in a moment."
        "is blocked by the site" in lower -> msg.take(180)
        "http 403" in lower || "response code: 403" in lower || " 403" in lower ->
            "This source is blocked by the site. Try another source."
        "http 404" in lower || " 404" in lower ->
            "That page was not found."
        else -> msg.take(160)
    }
}

/** "1 ep" or "12 eps". */
fun episodeCountLabel(count: Int): String = if (count == 1) "1 ep" else "$count eps"

/** Readable AniList format. Unknown codes stay as the site sent them. */
fun mediaFormatLabel(format: String?): String? = when (val raw = format?.trim().orEmpty()) {
    "" -> null
    else -> when (raw.uppercase()) {
        "TV" -> "TV"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music"
        else -> raw
    }
}

/** Minutes still left in an episode, or null when there is nothing to resume. */
fun minutesLeft(positionMs: Long, durationMs: Long): Int? {
    if (durationMs <= 30_000L || positionMs < 15_000L) return null
    val left = durationMs - positionMs
    if (left < 15_000L) return null
    return (left / 60_000L).toInt().coerceAtLeast(1)
}

/** Clock label, or a short countdown when the episode is about to air. */
fun airingLabel(airingAtEpochSec: Long, nowEpochSec: Long, clock: String): String {
    val delta = airingAtEpochSec - nowEpochSec
    return when {
        delta < -20 * 60 -> clock
        delta <= 60 -> "Now"
        delta < 60 * 60 -> "in ${delta / 60} min"
        delta < 6 * 60 * 60 -> {
            val hours = delta / 3600
            val mins = (delta % 3600) / 60
            if (mins == 0L) "in ${hours}h" else "in ${hours}h ${mins}m"
        }
        else -> clock
    }
}

/** Short relative time for a library row. Blank when the watch is older than two weeks. */
fun watchedAgo(updatedAtEpochMs: Long, nowEpochMs: Long = System.currentTimeMillis()): String {
    if (updatedAtEpochMs <= 0L) return ""
    val minutes = ((nowEpochMs - updatedAtEpochMs).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 60L * 24L -> "${minutes / 60L}h ago"
        minutes < 60L * 24L * 14L -> "${minutes / (60L * 24L)}d ago"
        else -> ""
    }
}

/** Drop AniList source footnotes and leftover entities after tag stripping. */
fun plainSynopsis(raw: String): String {
    val withoutSpoilers = raw.replace(Regex("""~!.*?!~""", RegexOption.DOT_MATCHES_ALL), " ")
    return withoutSpoilers
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("""&#(?:x([0-9a-fA-F]+)|(\d+));""")) { match ->
            val code = match.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull(16)
                ?: match.groupValues[2].toIntOrNull()
            synopsisCodePoint(code)
        }
        .replace(Regex("""\s*\(?\s*Source:.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

/** A numeric character reference, or a space when the code is not a character. */
private fun synopsisCodePoint(code: Int?): String {
    if (code == null || code !in 0..0x10FFFF || code in 0xD800..0xDFFF) return " "
    if (code < 32 || code == 0x7F || code == 0xA0) return " "
    return if (code <= 0xFFFF) code.toChar().toString() else String(Character.toChars(code))
}
