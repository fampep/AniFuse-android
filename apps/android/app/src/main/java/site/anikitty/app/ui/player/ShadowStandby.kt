// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/** A probed standby is stale after this. Signed CDN links rot faster than the next-episode cache. */
internal const val SHADOW_FRESH_MS = 8L * 60L * 1000L

/** Let the primary's first segments leave before the shadow probe opens its own connection. */
internal const val SHADOW_ARM_DELAY_MS = 400L

/** Stall budget once a different host is already probed and prefetched. */
internal const val SHADOW_STALL_MS = 2_400L

/** Stall budget when nothing warm exists yet — cut from 8s so cold failover feels instant. */
internal const val COLD_STALL_MS = 4_500L

internal enum class ShadowArm { Hold, Arm }

/**
 * Synthetic host key while a progressive file:// is on screen — empty
 * [playbackHost] would otherwise refuse to arm a live CDN standby.
 */
internal const val LOCAL_SHADOW_HOST = "local"

/**
 * Live remote and HLS-cache always arm. Progressive file:// arms only while
 * playingOffline so a spent offline hold can leave onto a probed CDN.
 */
internal fun liveShadowArmAllowed(
    playbackLocal: Boolean,
    playingOffline: Boolean = false,
): Boolean = !playbackLocal || playingOffline

/**
 * Never swap off a download onto a live CDN on a seek hiccup — only after
 * the session has left offline / HLS-cache playback.
 */
internal fun liveShadowSwapAllowed(playbackLocal: Boolean, playingOffline: Boolean): Boolean =
    !playbackLocal && !playingOffline

/** Stall walk / NextSource must not abandon an offline or HLS-cache copy. */
internal fun offlinePlaybackHoldsSource(playingOffline: Boolean): Boolean = playingOffline

/**
 * After the offline hold is spent, leaveOffline may use an armed shadow —
 * including progressive file:// (host key [LOCAL_SHADOW_HOST]).
 */
internal fun liveShadowLeaveOfflineAllowed(
    playbackLocal: Boolean,
    leaveOffline: Boolean,
): Boolean = leaveOffline

/** Host identity for shadow arm/swap — progressive local always uses [LOCAL_SHADOW_HOST]. */
internal fun shadowPlayingHost(url: String, playbackLocal: Boolean): String {
    if (playbackLocal) return LOCAL_SHADOW_HOST
    return playbackHost(url)
}

/**
 * Host to feed [armLiveShadow] / [shadowStandbyReady] so progressive offline
 * excludes the CDN that was saved to disk. Passing [LOCAL_SHADOW_HOST] would
 * keep that remote URL in the ranked set and arm the same source already on file.
 */
internal fun shadowArmAvoidHost(
    playbackLocal: Boolean,
    sourceUrl: String,
    armKey: String,
): String {
    if (!playbackLocal) return armKey
    return playbackHost(sourceUrl).ifBlank { armKey }
}

/**
 * Progressive file:// never hits the HLS segment interceptor. Arming
 * [SegmentShadow] with the saved CDN as fromHost would retarget concurrent
 * downloads of that host onto this episode's standby.
 */
internal fun shadowShouldArmSegmentLane(playbackLocal: Boolean): Boolean = !playbackLocal

/** @deprecated Prefer [liveShadowArmAllowed] / [liveShadowSwapAllowed]. */
internal fun liveShadowAllowed(playbackLocal: Boolean): Boolean = liveShadowArmAllowed(playbackLocal)

/**
 * Arm the moment playback is ready, and again when failover lands on a new host.
 * The same host must not start a second race.
 */
internal fun shadowArm(playbackReady: Boolean, armedHost: String?, playingHost: String): ShadowArm {
    val host = playingHost.trim().lowercase()
    if (!playbackReady || host.isEmpty()) return ShadowArm.Hold
    if (armedHost?.trim()?.lowercase() == host) return ShadowArm.Hold
    return ShadowArm.Arm
}

/** True when the standby was probed recently and does not share the host that just failed. */
internal fun shadowStandbyReady(
    playingHost: String,
    standbyHost: String?,
    standbyAgeMs: Long,
    freshMs: Long = SHADOW_FRESH_MS,
): Boolean {
    val playing = playingHost.trim().lowercase()
    val standby = standbyHost?.trim()?.lowercase().orEmpty()
    if (playing.isEmpty() || standby.isEmpty() || standby == playing) return false
    return standbyAgeMs in 0..freshMs
}

internal fun shadowStallBudgetMs(standbyFresh: Boolean): Long =
    if (standbyFresh) SHADOW_STALL_MS else COLD_STALL_MS

/**
 * CastPlayer RTT is slower than phone Exo — give TV a wider budget before
 * trickle-stall fires, still far under forever-buffering.
 */
internal const val CAST_STALL_MS = 3_600L
internal const val CAST_COLD_STALL_MS = 6_000L

internal fun castStallBudgetMs(standbyFresh: Boolean): Long =
    if (standbyFresh) CAST_STALL_MS else CAST_COLD_STALL_MS

/** Cast soft-stall only after the receiver clock belongs to this episode. */
internal fun castTrickleStallAllowed(
    isCasting: Boolean,
    castItemStarted: Boolean,
    castClockStale: Boolean,
): Boolean = isCasting && castItemStarted && !castClockStale

/**
 * Per-tick buffered advance still counted as a zombie CDN trickle.
 * Real ABR refill jumps hundreds of ms; a dying edge creeps ≤ this.
 */
internal const val TRICKLE_STALL_EPSILON_MS = 64L

internal data class TrickleStallState(
    val stallBuffered: Long,
    val stallSince: Long,
    val trip: Boolean,
)

/**
 * Stall detector that survives zombie CDNs slowly advancing [bufferedPosition].
 * Exact equality never held across ticks for those edges — this treats
 * per-tick advances ≤ [epsilonMs] as frozen and keeps [stallSince] running.
 * [stallBuffered] always tracks the latest sample so the next tick compares
 * against it (cumulative creep must not reset the wall clock).
 */
internal fun trickleStallTick(
    prevBuffered: Long,
    stallSince: Long,
    nowBuffered: Long,
    nowMs: Long,
    stalled: Boolean,
    budgetMs: Long,
    epsilonMs: Long = TRICKLE_STALL_EPSILON_MS,
): TrickleStallState {
    if (!stalled) {
        return TrickleStallState(
            stallBuffered = nowBuffered,
            stallSince = 0L,
            trip = false,
        )
    }
    // First sample or a real ABR refill jump — restart the wall clock.
    if (prevBuffered < 0L) {
        return TrickleStallState(
            stallBuffered = nowBuffered,
            stallSince = nowMs,
            trip = false,
        )
    }
    val tickDelta = kotlin.math.abs(nowBuffered - prevBuffered)
    if (tickDelta > epsilonMs) {
        return TrickleStallState(
            stallBuffered = nowBuffered,
            stallSince = nowMs,
            trip = false,
        )
    }
    val since = if (stallSince <= 0L) nowMs else stallSince
    return TrickleStallState(
        stallBuffered = nowBuffered,
        stallSince = since,
        trip = nowMs - since >= budgetMs.coerceAtLeast(0L),
    )
}

internal enum class FailureRoute { SameSource, NextInPlaylist, ShadowSwap, ColdResolve }

/**
 * A mid-roll 403, 5xx, exhausted CDN, or GiveUp with an armed standby swaps
 * to the probed runner-up before any cold resolve. Same-host mirrors are not
 * a recovery; the standby is a different host.
 */
internal fun failureRoute(
    recovery: SourceRecovery,
    playlistHasHealthyNext: Boolean,
    shadowReady: Boolean,
): FailureRoute = when (recovery) {
    SourceRecovery.SeekLive, SourceRecovery.RetrySame -> FailureRoute.SameSource
    SourceRecovery.GiveUp -> if (shadowReady) FailureRoute.ShadowSwap else FailureRoute.SameSource
    SourceRecovery.NextSource -> when {
        shadowReady -> FailureRoute.ShadowSwap
        playlistHasHealthyNext -> FailureRoute.NextInPlaylist
        else -> FailureRoute.ColdResolve
    }
}
