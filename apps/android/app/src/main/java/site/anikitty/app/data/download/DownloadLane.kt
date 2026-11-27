// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.download

import site.anikitty.app.data.servers.PlayableSource
import site.anikitty.app.data.servers.playableHost
import site.anikitty.app.data.servers.rankShadowCandidates

/** One WorkManager chain for every episode. Parallel jobs were pulling the playing CDN. */
internal const val DOWNLOAD_LANE = "episode-download-lane"

/** How long the lane waits before asking WorkManager to try again. */
internal const val LANE_YIELD_SLICE_MS = 1_500L

/** Slices spent inside one run before the lane backs off. 8 × 1.5s. */
internal const val LANE_YIELD_SLICES = 8

internal data class LaneCandidate(
    val anilistId: Int,
    val episode: Int,
    val url: String,
    val updatedAtEpochMs: Long,
)

internal sealed interface LaneChoice {
    data object Idle : LaneChoice
    data object Wait : LaneChoice
    data class Run(val anilistId: Int, val episode: Int) : LaneChoice
}

/**
 * Oldest save that is not on the CDN currently playing.
 * [deferred] rows failed this pass and wait for the next worker run.
 * When every remaining row shares the playing host, the lane waits.
 */
internal fun nextLaneDownload(
    rows: List<LaneCandidate>,
    playingHost: String?,
    deferred: Set<Pair<Int, Int>> = emptySet(),
): LaneChoice {
    if (rows.isEmpty()) return LaneChoice.Idle
    val ordered = rows.sortedWith(compareBy({ it.updatedAtEpochMs }, { it.anilistId }, { it.episode }))
    val open = ordered.filter { (it.anilistId to it.episode) !in deferred }
    if (open.isEmpty()) return LaneChoice.Wait
    val playing = playingHost?.trim()?.lowercase().orEmpty()
    if (playing.isEmpty()) return LaneChoice.Run(open.first().anilistId, open.first().episode)
    val free = open.firstOrNull { row ->
        val host = playableHost(row.url)
        host.isEmpty() || host != playing
    }
    if (free != null) return LaneChoice.Run(free.anilistId, free.episode)
    return LaneChoice.Wait
}

internal enum class DownloadTransfer { Resume, Replace, Restart, Swap, Fail }

/**
 * 206 keeps the partial file. A refusal asks for a different host.
 * 416 means the partial is useless and the next try starts over.
 */
internal fun downloadTransfer(code: Int, partBytes: Long): DownloadTransfer = when {
    code == 206 && partBytes > 0L -> DownloadTransfer.Resume
    code == 416 -> DownloadTransfer.Restart
    code in 200..299 -> DownloadTransfer.Replace
    code == 401 || code == 403 || code == 404 || code == 408 || code == 410 ||
        code == 429 || code in 500..599 -> DownloadTransfer.Swap
    else -> DownloadTransfer.Fail
}

/** A new host cannot append bytes from the CDN that just refused. */
internal fun keepPartialForNextUrl(currentUrl: String, nextUrl: String): Boolean {
    val current = playableHost(currentUrl)
    val next = playableHost(nextUrl)
    return current.isNotEmpty() && current == next
}

/**
 * The probed shadow wins. Otherwise the first healthy resolve on another host.
 * The URL already being saved is never a standby.
 */
internal fun inheritDownloadSource(
    currentUrl: String,
    cached: List<PlayableSource>,
    resolved: List<PlayableSource>,
): PlayableSource? {
    val host = playableHost(currentUrl)
    val dead = if (host.isEmpty()) emptySet() else setOf(host)
    return rankShadowCandidates(cached, host, dead).firstOrNull()
        ?: rankShadowCandidates(resolved, host, dead).firstOrNull()
}

/** Sub / Dub from the source key saved with the row. Missing detail stays sub. */
internal fun downloadAudioHint(sourceKey: String): String {
    val parts = sourceKey.split('\u0001')
    val language = parts.getOrNull(3)?.trim()?.lowercase()?.substringBefore('-').orEmpty()
    val described = parts.getOrNull(2)?.lowercase().orEmpty()
    return if (language == "en" || "dub" in described) "dub" else "sub"
}

internal fun downloadYields(url: String, playingHost: String?, phase: String): Boolean {
    if (phase != DownloadPhase.QUEUED.name) return false
    val playing = playingHost?.trim()?.lowercase().orEmpty()
    if (playing.isEmpty()) return false
    return playableHost(url) == playing
}

internal fun downloadAhead(
    updatedAtEpochMs: Long,
    anilistId: Int,
    episode: Int,
    rows: List<LaneCandidate>,
): Int = rows.count { other ->
    if (other.anilistId == anilistId && other.episode == episode) return@count false
    other.updatedAtEpochMs < updatedAtEpochMs ||
        (other.updatedAtEpochMs == updatedAtEpochMs &&
            (other.anilistId < anilistId || (other.anilistId == anilistId && other.episode < episode)))
}

internal fun downloadShelfSubtitle(
    episode: Int,
    phase: String,
    percent: Int,
    error: String?,
    url: String,
    playingHost: String?,
    ahead: Int,
): String {
    val place = when {
        phase == DownloadPhase.READY.name -> "Saved"
        phase == DownloadPhase.FAILED.name -> error?.takeIf { it.isNotBlank() } ?: "Failed"
        phase == DownloadPhase.RUNNING.name && percent > 0 -> "Saving $percent%"
        downloadYields(url, playingHost, phase) -> "Waiting"
        phase == DownloadPhase.QUEUED.name && ahead > 0 -> "In line"
        phase == DownloadPhase.QUEUED.name -> "Queued"
        phase == DownloadPhase.RUNNING.name -> "Saving"
        else -> "Queued"
    }
    return "EP $episode · $place"
}

internal fun downloadButtonLabel(status: DownloadStatus): String = when {
    status.label == "Waiting" -> "Waiting"
    status.label == "In line" -> "In line"
    status.label == "Queued" -> "Queued"
    status.label == "Saving…" -> "Saving"
    status.label == "Saved" -> "Saved"
    status.label.startsWith("Saving ") -> status.label.removePrefix("Saving ")
    status.label.startsWith("Retry") -> "Retry"
    else -> "Download"
}

/** Status code buried in an OkHttp or Media3 message. */
internal fun httpCodeIn(error: Throwable?): Int? {
    var current = error
    var guard = 0
    while (current != null && guard < 8) {
        val message = current.message.orEmpty()
        Regex("""(?:Response code|HTTP(?: status)?)[: ]+(\d{3})""").find(message)?.let {
            return it.groupValues[1].toInt()
        }
        current = current.cause
        guard++
    }
    return null
}
