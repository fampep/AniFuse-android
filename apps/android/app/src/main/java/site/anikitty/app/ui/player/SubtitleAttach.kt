// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import site.anikitty.app.data.servers.SubtitleTrack

/**
 * Library tracks fill gaps; provider sidecars win on the same URL.
 * Bake the merge into the first MediaItem so dialogue is never caption-dark
 * while a warm SubtitleLibrary.peek is already in memory.
 */
internal fun playbackSubtitleTracks(
    provider: List<SubtitleTrack>,
    library: List<SubtitleTrack>,
): List<SubtitleTrack> = mergeSubtitleTracks(provider, library)

/**
 * Peek is tri-state: null = unknown (must fetch), empty = known miss (skip
 * network), non-empty = warm hit. Collapsing null with empty used to skip the
 * library fetch whenever the provider already had any sidecar.
 */
internal fun shouldFetchLibraryCaptions(
    peeked: List<SubtitleTrack>?,
    anilistId: Int,
    episode: Int,
): Boolean = peeked == null && anilistId > 0 && episode > 0

/** Provider sidecars first; warm/empty peek fills gaps without inventing a fetch. */
internal fun captionsAfterPeek(
    provider: List<SubtitleTrack>,
    peeked: List<SubtitleTrack>?,
): List<SubtitleTrack> = mergeSubtitleTracks(provider, peeked.orEmpty())

/** Attach once Media3 is buffering or ready — do not wait out a 6s READY poll. */
internal fun subtitleAttachMayProceed(playbackState: Int): Boolean =
    playbackState == PLAYBACK_STATE_BUFFERING ||
        playbackState == PLAYBACK_STATE_READY ||
        playbackState == PLAYBACK_STATE_ENDED

/**
 * After the poll budget, still-idle players must hard-reload via setMediaItem —
 * abandoning the attach leaves the first spoken lines caption-dark.
 */
internal fun subtitleAttachShouldHardReload(playbackState: Int): Boolean =
    playbackState == PLAYBACK_STATE_IDLE

internal fun subtitleConfigsUnchanged(
    existingUris: List<String>,
    nextUris: List<String>,
): Boolean = existingUris == nextUris

/** 50ms × 40 = 2s ceiling — was 125ms × 48 ≈ 6s. */
internal const val SUBTITLE_ATTACH_POLL_MS = 50L
internal const val SUBTITLE_ATTACH_MAX_POLLS = 40

internal const val PLAYBACK_STATE_IDLE = 1
internal const val PLAYBACK_STATE_ENDED = 4
