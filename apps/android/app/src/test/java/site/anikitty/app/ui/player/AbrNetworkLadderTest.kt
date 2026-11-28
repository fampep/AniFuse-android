// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbrNetworkLadderTest {

    @Test
    fun abrNetworkClassMapsConnectivityFlags() {
        assertEquals(
            AbrNetworkClass.Offline,
            abrNetworkClass(hasInternet = false, isWifi = false, isMetered = true),
        )
        assertEquals(
            AbrNetworkClass.Wifi,
            abrNetworkClass(hasInternet = true, isWifi = true, isMetered = false),
        )
        assertEquals(
            AbrNetworkClass.Wifi,
            abrNetworkClass(hasInternet = true, isWifi = true, isMetered = true),
        )
        assertEquals(
            AbrNetworkClass.Metered,
            abrNetworkClass(hasInternet = true, isWifi = false, isMetered = true),
        )
        assertEquals(
            AbrNetworkClass.UnmeteredCellular,
            abrNetworkClass(hasInternet = true, isWifi = false, isMetered = false),
        )
    }

    @Test
    fun wifiLoadControlMatchesLegacyDefaultsWithoutRtt() {
        val wifi = abrLoadControlMs(AbrNetworkClass.Wifi)
        assertEquals(2_000, wifi.minBufferMs)
        assertEquals(50_000, wifi.maxBufferMs)
        assertEquals(320, wifi.bufferForPlaybackMs)
        assertEquals(800, wifi.bufferForPlaybackAfterRebufferMs)
        assertEquals(40_000, wifi.backBufferMs)
    }

    @Test
    fun hotRttShortensWifiStartupBuffers() {
        val cold = abrLoadControlMs(AbrNetworkClass.Wifi, hostRttMs = null)
        val hot = abrLoadControlMs(AbrNetworkClass.Wifi, hostRttMs = 80L)
        assertTrue(hot.bufferForPlaybackMs < cold.bufferForPlaybackMs)
        assertTrue(hot.bufferForPlaybackAfterRebufferMs < cold.bufferForPlaybackAfterRebufferMs)
        assertEquals(200, hot.bufferForPlaybackMs)
        assertEquals(600, hot.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun meteredRaisesMinAndShrinksMaxBuffers() {
        val wifi = abrLoadControlMs(AbrNetworkClass.Wifi)
        val metered = abrLoadControlMs(AbrNetworkClass.Metered)
        assertTrue(metered.minBufferMs > wifi.minBufferMs)
        assertTrue(metered.maxBufferMs < wifi.maxBufferMs)
        assertTrue(metered.bufferForPlaybackAfterRebufferMs > wifi.bufferForPlaybackAfterRebufferMs)
        assertTrue(metered.backBufferMs < wifi.backBufferMs)
    }

    @Test
    fun everyNetworkProfileSatisfiesMedia3Invariants() {
        for (network in AbrNetworkClass.entries) {
            for (rtt in listOf(null, 80L, 200L, 500L)) {
                requireAbrLoadControlValid(abrLoadControlMs(network, rtt))
            }
        }
    }

    @Test
    fun meteredAdaptiveClimbsSlowerAndDumpsFasterThanWifi() {
        val wifi = abrAdaptiveWindows(AbrNetworkClass.Wifi)
        val metered = abrAdaptiveWindows(AbrNetworkClass.Metered)
        assertTrue(metered.minDurationForQualityIncreaseMs > wifi.minDurationForQualityIncreaseMs)
        assertTrue(metered.maxDurationForQualityDecreaseMs < wifi.maxDurationForQualityDecreaseMs)
        assertTrue(metered.bandwidthFraction < wifi.bandwidthFraction)
    }

    @Test
    fun hotRttTightensWifiAdaptiveClimbWindow() {
        val cold = abrAdaptiveWindows(AbrNetworkClass.Wifi, hostRttMs = null)
        val hot = abrAdaptiveWindows(AbrNetworkClass.Wifi, hostRttMs = 90L)
        assertTrue(hot.minDurationForQualityIncreaseMs < cold.minDurationForQualityIncreaseMs)
        assertTrue(hot.bandwidthFraction > cold.bandwidthFraction)
    }

    @Test
    fun abrSeedBitrateClampsMeteredAndCellular() {
        assertEquals(4_000_000, abrSeedBitrate(4_000_000, AbrNetworkClass.Wifi))
        assertEquals(2_400_000, abrSeedBitrate(4_000_000, AbrNetworkClass.UnmeteredCellular))
        assertEquals(1_200_000, abrSeedBitrate(4_000_000, AbrNetworkClass.Metered))
        assertEquals(800_000, abrSeedBitrate(4_000_000, AbrNetworkClass.Offline))
        assertEquals(500_000, abrSeedBitrate(500_000, AbrNetworkClass.Metered))
    }

    @Test
    fun abrNetworkStatusPillStaysQuietOnWifi() {
        assertNull(abrNetworkStatusPill(AbrNetworkClass.Wifi))
        assertEquals("ABR · NET · CELL", abrNetworkStatusPill(AbrNetworkClass.UnmeteredCellular))
        assertEquals("ABR · NET · METERED", abrNetworkStatusPill(AbrNetworkClass.Metered))
        assertEquals("ABR · NET · OFFLINE", abrNetworkStatusPill(AbrNetworkClass.Offline))
    }

    @Test
    fun everyAdaptiveProfileSatisfiesMedia3RetainInvariant() {
        for (network in AbrNetworkClass.entries) {
            for (rtt in listOf(null, 80L, 200L, 500L)) {
                requireAbrAdaptiveValid(abrAdaptiveWindows(network, rtt))
            }
        }
    }

    @Test
    fun offlineRetainIsAtLeastQualityIncrease() {
        val offline = abrAdaptiveWindows(AbrNetworkClass.Offline)
        assertTrue(offline.minDurationToRetainAfterDiscardMs >= offline.minDurationForQualityIncreaseMs)
        assertEquals(7_000, offline.minDurationToRetainAfterDiscardMs)
    }

    @Test
    fun abrWifiLikeSurvivesVpnOnlyActiveNetwork() {
        // VPN-only active + unmetered → Wi‑Fi ladder (home Wi‑Fi under VPN).
        assertTrue(
            abrWifiLike(
                activeHasWifiOrEthernet = false,
                activeHasVpn = true,
                isMetered = false,
                anyNetworkWifiOrEthernet = false,
            ),
        )
        // Sibling Wi‑Fi while VPN is active (underlay).
        assertTrue(
            abrWifiLike(
                activeHasWifiOrEthernet = false,
                activeHasVpn = true,
                isMetered = true,
                anyNetworkWifiOrEthernet = true,
            ),
        )
        // Metered VPN without Wi‑Fi underneath → not Wi‑Fi.
        assertTrue(
            !abrWifiLike(
                activeHasWifiOrEthernet = false,
                activeHasVpn = true,
                isMetered = true,
                anyNetworkWifiOrEthernet = false,
            ),
        )
        // Sibling Wi‑Fi with NO VPN while metered cellular is default → not Wi‑Fi.
        assertTrue(
            !abrWifiLike(
                activeHasWifiOrEthernet = false,
                activeHasVpn = false,
                isMetered = true,
                anyNetworkWifiOrEthernet = true,
            ),
        )
    }
}
