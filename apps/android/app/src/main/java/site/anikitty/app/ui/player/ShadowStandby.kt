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

internal enum class FailureRoute { SameSource, NextInPlaylist, ShadowSwap, ColdResolve }

/**
 * A mid-roll 403, 5xx, or exhausted CDN swaps to the probed standby before any
 * cold resolve. Same-host mirrors are not a recovery; the standby is a different host.
 */
internal fun failureRoute(
    recovery: SourceRecovery,
    playlistHasHealthyNext: Boolean,
    shadowReady: Boolean,
): FailureRoute = when (recovery) {
    SourceRecovery.SeekLive, SourceRecovery.RetrySame, SourceRecovery.GiveUp -> FailureRoute.SameSource
    SourceRecovery.NextSource -> when {
        shadowReady -> FailureRoute.ShadowSwap
        playlistHasHealthyNext -> FailureRoute.NextInPlaylist
        else -> FailureRoute.ColdResolve
    }
}
