// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

@file:Suppress("UnsafeOptInUsageError")
@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package site.anikitty.app.ui.player

import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

/**
 * After a CDN swap the bandwidth meter still believes the previous host.
 * Cap the ladder briefly so the new pipe is not judged by a stale estimate,
 * then release so ABR climbs again. [ReseedableBandwidthMeter] also forces
 * the estimate itself so AdaptiveTrackSelection is not fighting a polluted
 * DefaultBandwidthMeter reading.
 */

/** Soft-cap ladder phases after a CDN host swap — surfaces on the chrome pill. */
internal enum class AbrFailoverPhase { Idle, Cap, Soft, Climb }

/** Glass chrome label while ABR is proving the new pipe. Null when uncapped. */
internal fun abrFailoverStatusPill(phase: AbrFailoverPhase): String? = when (phase) {
    AbrFailoverPhase.Cap -> "ABR · HOLD"
    AbrFailoverPhase.Soft -> "ABR · SOFT"
    AbrFailoverPhase.Climb -> "ABR · CLIMB"
    AbrFailoverPhase.Idle -> null
}

/** Soft ceiling while the new host proves itself. Seeded from the last good rung. */
internal fun abrFailoverCapBitrate(seedBitrate: Int): Int =
    seedBitrate.coerceIn(500_000, 2_400_000).coerceAtMost(1_200_000)

/** Hold the ceiling longer when the previous rung was high — the meter is more wrong. */
internal fun abrFailoverHoldMs(seedBitrate: Int): Long = when {
    seedBitrate >= 3_000_000 -> 3_200L
    seedBitrate >= 1_800_000 -> 2_600L
    else -> 2_000L
}

/**
 * After the hold, climb through a soft release instead of jumping to uncapped.
 * Instant MAX invites a stall on a still-cold meter; 1.6× the seed (bounded)
 * lets ABR step up without a bitrate thrash.
 */
internal fun abrFailoverReleaseBitrate(seedBitrate: Int): Int {
    val seed = seedBitrate.coerceAtLeast(500_000)
    return (seed * 8L / 5L).toInt().coerceIn(1_200_000, 4_000_000)
}

/**
 * Second soft climb after [abrFailoverReleaseBitrate] — still capped so a
 * cold CDN cannot thrash to the top rung before the meter has samples.
 */
internal fun abrFailoverClimbBitrate(seedBitrate: Int): Int {
    val soft = abrFailoverReleaseBitrate(seedBitrate)
    return (soft * 7L / 4L).toInt().coerceIn(2_000_000, 6_000_000)
}

/** Soft-release dwell before the second climb — long enough for a segment sample. */
internal fun abrFailoverReleaseHoldMs(): Long = 1_400L

/** Hold the second climb long enough for a few segment samples. */
internal fun abrFailoverClimbHoldMs(): Long = 1_100L

/** Forced meter reading while the soft cap is live — matches the ladder ceiling. */
internal fun abrFailoverReseedBitrate(seedBitrate: Int): Long =
    abrFailoverCapBitrate(seedBitrate).toLong()

/**
 * Only reseed ABR when the CDN host actually changes. Same-host quality or
 * playlist swaps must not thrash the meter / soft-cap ladder. Callers must
 * key the soft-cap effect on the host (not the full URL) so a same-host swap
 * cannot cancel a mid-flight release and leave maxVideoBitrate stuck.
 */
internal fun abrFailoverShouldReseed(previousHost: String?, nextHost: String?): Boolean {
    val prev = previousHost?.trim()?.lowercase().orEmpty()
    val next = nextHost?.trim()?.lowercase().orEmpty()
    if (prev.isEmpty() || next.isEmpty()) return false
    return prev != next
}

/**
 * Wraps [DefaultBandwidthMeter] so a CDN failover can stamp a sane estimate
 * until the soft-release window ends. Without this, [getBitrateEstimate] still
 * reports the dead host and ABR fights the max-bitrate cap.
 */
@UnstableApi
internal class ReseedableBandwidthMeter(
    private val delegate: DefaultBandwidthMeter,
) : BandwidthMeter {
    @Volatile
    private var forcedBitrateBps: Long = -1L

    fun reseed(bitrateBps: Long) {
        forcedBitrateBps = bitrateBps.coerceAtLeast(100_000L)
    }

    fun clearReseed() {
        forcedBitrateBps = -1L
    }

    /** Exposed for unit tests — negative means the live meter is in charge. */
    internal fun forcedBitrateOrNull(): Long? =
        forcedBitrateBps.takeIf { it > 0L }

    override fun getBitrateEstimate(): Long {
        val forced = forcedBitrateBps
        if (forced > 0L) return forced
        return delegate.bitrateEstimate
    }

    override fun getTimeToFirstByteEstimateUs(): Long = delegate.timeToFirstByteEstimateUs

    override fun addEventListener(eventHandler: Handler, eventListener: BandwidthMeter.EventListener) {
        delegate.addEventListener(eventHandler, eventListener)
    }

    override fun removeEventListener(eventListener: BandwidthMeter.EventListener) {
        delegate.removeEventListener(eventListener)
    }

    override fun getTransferListener(): TransferListener = delegate.transferListener
}
