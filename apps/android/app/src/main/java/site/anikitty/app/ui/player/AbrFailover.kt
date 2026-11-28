// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * After a CDN swap the bandwidth meter still believes the previous host.
 * Cap the ladder briefly so the new pipe is not judged by a stale estimate,
 * then release so ABR climbs again.
 */

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
