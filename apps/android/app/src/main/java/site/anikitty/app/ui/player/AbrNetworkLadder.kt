// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

/**
 * Network-class + Race-dock RTT ladder for cold-open LoadControl and
 * AdaptiveTrackSelection. AbrFailover soft-caps after a CDN swap; this
 * shapes the player *before* the first segment so Wi‑Fi and metered LTE
 * do not share one static buffer / climb profile.
 */

internal enum class AbrNetworkClass {
    Wifi,
    UnmeteredCellular,
    Metered,
    Offline,
}

/** Glass chrome tip — null on healthy Wi‑Fi so the dock stays quiet. */
internal fun abrNetworkStatusPill(network: AbrNetworkClass): String? = when (network) {
    AbrNetworkClass.Wifi -> null
    AbrNetworkClass.UnmeteredCellular -> "ABR · NET · CELL"
    AbrNetworkClass.Metered -> "ABR · NET · METERED"
    AbrNetworkClass.Offline -> "ABR · NET · OFFLINE"
}

/**
 * Map connectivity booleans into a ladder class. Callers sample
 * [android.net.ConnectivityManager] once at ExoPlayer build.
 */
internal fun abrNetworkClass(
    hasInternet: Boolean,
    isWifi: Boolean,
    isMetered: Boolean,
): AbrNetworkClass = when {
    !hasInternet -> AbrNetworkClass.Offline
    isWifi -> AbrNetworkClass.Wifi
    isMetered -> AbrNetworkClass.Metered
    else -> AbrNetworkClass.UnmeteredCellular
}

/**
 * Buffer durations for [androidx.media3.exoplayer.DefaultLoadControl].
 * Always satisfies Media3's min ≥ playback / after-rebuffer invariants.
 */
internal data class AbrLoadControlMs(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val backBufferMs: Int,
)

/**
 * AdaptiveTrackSelection windows. Metered climbs slower and dumps faster;
 * hot Race-dock RTT shortens the quality-increase dwell on Wi‑Fi.
 */
internal data class AbrAdaptiveWindows(
    val minDurationForQualityIncreaseMs: Int,
    val maxDurationForQualityDecreaseMs: Int,
    val minDurationToRetainAfterDiscardMs: Int,
    val bandwidthFraction: Float,
)

/** LoadControl profile keyed by network class + optional host RTT. */
internal fun abrLoadControlMs(
    network: AbrNetworkClass,
    hostRttMs: Long? = null,
): AbrLoadControlMs {
    val hot = hostRttMs != null && hostRttMs in 1L..ABR_HOT_RTT_MS
    val warm = hostRttMs != null && hostRttMs in 1L..ABR_WARM_RTT_MS
    return when (network) {
        AbrNetworkClass.Wifi -> {
            val forPlayback = when {
                hot -> 200
                warm -> 260
                else -> 320
            }
            val afterRebuffer = when {
                hot -> 600
                warm -> 700
                else -> 800
            }
            AbrLoadControlMs(
                minBufferMs = 2_000,
                maxBufferMs = 50_000,
                bufferForPlaybackMs = forPlayback,
                bufferForPlaybackAfterRebufferMs = afterRebuffer,
                backBufferMs = 40_000,
            )
        }
        AbrNetworkClass.UnmeteredCellular -> AbrLoadControlMs(
            minBufferMs = 2_800,
            maxBufferMs = 40_000,
            bufferForPlaybackMs = if (hot) 360 else 480,
            bufferForPlaybackAfterRebufferMs = if (hot) 900 else 1_100,
            backBufferMs = 28_000,
        )
        AbrNetworkClass.Metered -> AbrLoadControlMs(
            minBufferMs = 3_600,
            maxBufferMs = 28_000,
            bufferForPlaybackMs = 800,
            bufferForPlaybackAfterRebufferMs = 1_600,
            backBufferMs = 20_000,
        )
        AbrNetworkClass.Offline -> AbrLoadControlMs(
            minBufferMs = 4_000,
            maxBufferMs = 24_000,
            bufferForPlaybackMs = 1_000,
            bufferForPlaybackAfterRebufferMs = 2_000,
            backBufferMs = 16_000,
        )
    }.also { requireAbrLoadControlValid(it) }
}

/** Adaptive windows keyed by network class + optional host RTT. */
internal fun abrAdaptiveWindows(
    network: AbrNetworkClass,
    hostRttMs: Long? = null,
): AbrAdaptiveWindows {
    val hot = hostRttMs != null && hostRttMs in 1L..ABR_HOT_RTT_MS
    val warm = hostRttMs != null && hostRttMs in 1L..ABR_WARM_RTT_MS
    return when (network) {
        AbrNetworkClass.Wifi -> AbrAdaptiveWindows(
            minDurationForQualityIncreaseMs = when {
                hot -> 2_200
                warm -> 2_700
                else -> 3_200
            },
            maxDurationForQualityDecreaseMs = 2_000,
            minDurationToRetainAfterDiscardMs = 10_000,
            bandwidthFraction = if (hot) 0.88f else 0.82f,
        )
        AbrNetworkClass.UnmeteredCellular -> AbrAdaptiveWindows(
            minDurationForQualityIncreaseMs = if (hot) 3_600 else 4_400,
            maxDurationForQualityDecreaseMs = 1_600,
            minDurationToRetainAfterDiscardMs = 8_000,
            bandwidthFraction = 0.78f,
        )
        AbrNetworkClass.Metered -> AbrAdaptiveWindows(
            minDurationForQualityIncreaseMs = 5_500,
            maxDurationForQualityDecreaseMs = 1_200,
            minDurationToRetainAfterDiscardMs = 6_000,
            bandwidthFraction = 0.70f,
        )
        AbrNetworkClass.Offline -> AbrAdaptiveWindows(
            minDurationForQualityIncreaseMs = 7_000,
            maxDurationForQualityDecreaseMs = 1_000,
            minDurationToRetainAfterDiscardMs = 5_000,
            bandwidthFraction = 0.65f,
        )
    }
}

/**
 * Clamp the prefs seed so metered / cellular cold opens do not request a
 * 4K rung the pipe cannot sustain. Wi‑Fi keeps the remembered rung.
 */
internal fun abrSeedBitrate(prefsSeed: Int, network: AbrNetworkClass): Int {
    val seed = prefsSeed.coerceIn(400_000, 8_000_000)
    return when (network) {
        AbrNetworkClass.Wifi -> seed
        AbrNetworkClass.UnmeteredCellular -> seed.coerceAtMost(2_400_000)
        AbrNetworkClass.Metered -> seed.coerceAtMost(1_200_000)
        AbrNetworkClass.Offline -> seed.coerceAtMost(800_000)
    }
}

/** Media3 crashes if minBufferMs is below either playback threshold. */
internal fun requireAbrLoadControlValid(profile: AbrLoadControlMs) {
    require(profile.minBufferMs >= profile.bufferForPlaybackMs) {
        "minBufferMs (${profile.minBufferMs}) < bufferForPlaybackMs (${profile.bufferForPlaybackMs})"
    }
    require(profile.minBufferMs >= profile.bufferForPlaybackAfterRebufferMs) {
        "minBufferMs (${profile.minBufferMs}) < afterRebuffer (${profile.bufferForPlaybackAfterRebufferMs})"
    }
    require(profile.maxBufferMs >= profile.minBufferMs) {
        "maxBufferMs (${profile.maxBufferMs}) < minBufferMs (${profile.minBufferMs})"
    }
    require(profile.backBufferMs >= 0)
}
