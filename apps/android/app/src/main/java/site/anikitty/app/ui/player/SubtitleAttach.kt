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

/** Normalize MediaItem / source URLs so failover identity compares stably. */
internal fun subtitleAttachUriKey(uri: String?): String =
    uri?.trim().orEmpty()

/**
 * True when the player item we would replace is no longer the source this
 * attach was armed for (CDN failover / quality swap won the race).
 */
internal fun subtitleAttachMediaIdentityDrifted(
    expectedUri: String?,
    liveUri: String?,
): Boolean {
    val want = subtitleAttachUriKey(expectedUri)
    val live = subtitleAttachUriKey(liveUri)
    if (want.isEmpty() || live.isEmpty()) return true
    return want != live
}

/**
 * Soft-replace when live, hard-reload when still idle, re-arm when CDN
 * failover drifted the MediaItem under us, or no-op when unchanged.
 */
internal enum class SubtitleAttachAction {
    None,
    SoftReplace,
    HardReload,
    /** Live URI changed — retry attach against the new item once. */
    ReArm,
}

/**
 * Decide the attach mutation after the IDLE poll. Always re-read the live
 * MediaItem identity — a pre-poll snapshot can soft-replace a dead CDN URI
 * back into the player after a successful failover.
 */
internal fun subtitleAttachAction(
    expectedUri: String?,
    liveUri: String?,
    liveItemPresent: Boolean,
    existingUris: List<String>,
    nextUris: List<String>,
    playbackState: Int,
    itemIndex: Int,
    mediaItemCount: Int,
): SubtitleAttachAction {
    if (!liveItemPresent) return SubtitleAttachAction.None
    if (subtitleAttachMediaIdentityDrifted(expectedUri, liveUri)) {
        return SubtitleAttachAction.ReArm
    }
    if (subtitleConfigsUnchanged(existingUris, nextUris)) {
        return SubtitleAttachAction.None
    }
    val soft = !subtitleAttachShouldHardReload(playbackState) &&
        itemIndex >= 0 &&
        mediaItemCount > itemIndex
    return if (soft) SubtitleAttachAction.SoftReplace else SubtitleAttachAction.HardReload
}

/** One CDN failover re-arm — more would loop on a flapping edge. */
internal const val SUBTITLE_ATTACH_MAX_REARMS = 1

/**
 * Theater CC phase — parallel to [AbrFailoverPhase] so cold attach is visible.
 */
internal enum class SubtitleAttachPhase { Idle, Fetch, Soft, Bake, Ready }

internal fun subtitleAttachStatusPill(phase: SubtitleAttachPhase): String? = when (phase) {
    SubtitleAttachPhase.Fetch -> "CC · WARM"
    SubtitleAttachPhase.Soft, SubtitleAttachPhase.Bake -> "CC · LIVE"
    SubtitleAttachPhase.Idle, SubtitleAttachPhase.Ready -> null
}

internal fun subtitleAttachPhaseAfterAction(action: SubtitleAttachAction): SubtitleAttachPhase? =
    when (action) {
        SubtitleAttachAction.SoftReplace -> SubtitleAttachPhase.Soft
        SubtitleAttachAction.HardReload -> SubtitleAttachPhase.Bake
        SubtitleAttachAction.None, SubtitleAttachAction.ReArm -> null
    }

/** 33ms × 36 ≈ 1.2s ceiling — attach as soon as Media3 leaves IDLE. */
internal const val SUBTITLE_ATTACH_POLL_MS = 33L
internal const val SUBTITLE_ATTACH_MAX_POLLS = 36

internal const val PLAYBACK_STATE_IDLE = 1
internal const val PLAYBACK_STATE_ENDED = 4
