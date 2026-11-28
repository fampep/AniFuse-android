// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostHealthProbeTest {
    @Test
    fun probeUrlRequiresAHostname() {
        assertEquals("https://cdn.example/", hostProbeUrl("CDN.example"))
        assertNull(hostProbeUrl(""))
        assertNull(hostProbeUrl("localhost"))
        assertNull(hostProbeUrl("cdn.example/path"))
    }

    @Test
    fun anyHttpAnswerMeansTheEdgeIsUp() {
        assertTrue(hostProbeOutcome(200, connected = true))
        assertTrue(hostProbeOutcome(403, connected = true))
        assertTrue(hostProbeOutcome(404, connected = true))
        assertFalse(hostProbeOutcome(null, connected = false))
        assertFalse(hostProbeOutcome(null, connected = true))
    }

    @Test
    fun softMissNeverStampsAFailedBackgroundProbe() {
        assertTrue(shouldStampHostProbe(ok = true))
        assertFalse(shouldStampHostProbe(ok = false))
    }

    @Test
    fun stampRulesDifferForHeadAndRangeGet() {
        // HEAD replaces — live edge truth for ABR soft-cap / Race dock.
        assertEquals(
            800L,
            hostProbeLatencyToStamp(
                previousMs = 40L,
                sampleMs = 800L,
                method = HostProbeMethod.Head,
            ),
        )
        assertEquals(
            40L,
            hostProbeLatencyToStamp(
                previousMs = 90L,
                sampleMs = 40L,
                method = HostProbeMethod.Head,
            ),
        )
        // Range-GET still mins so a slow fallback cannot wipe a fast stamp.
        assertEquals(
            40L,
            hostProbeLatencyToStamp(
                previousMs = 40L,
                sampleMs = 800L,
                method = HostProbeMethod.RangeGet,
            ),
        )
        assertEquals(55L, hostProbeLatencyToStamp(previousMs = null, sampleMs = 55L))
    }

    @Test
    fun probeWavePrefersLeadAndSkipsSunkBudget() {
        val healthy = listOf("cdn.new", "cdn.old", "cdn.mid")
        assertEquals(
            listOf("cdn.old", "cdn.new", "cdn.mid"),
            hostsForProbeWave(healthy, preferredHost = "cdn.old", limit = 6),
        )
        assertEquals(
            listOf("cdn.new", "cdn.old"),
            hostsForProbeWave(healthy, preferredHost = null, limit = 2),
        )
        // Preferred not yet healthy still leads — Race dock must re-probe it.
        assertEquals(
            listOf("cdn.sunk"),
            hostsForProbeWave(healthy, preferredHost = "cdn.sunk", limit = 1),
        )
        assertEquals(
            listOf("cdn.sunk", "cdn.new"),
            hostsForProbeWave(healthy, preferredHost = "cdn.sunk", limit = 2),
        )
    }

    @Test
    fun probeWaveSeedsAffinityCdnsAheadOfHealthyTail() {
        val healthy = listOf("cdn.hold", "cdn.tail")
        assertEquals(
            listOf("cdn.lead", "play.anien.top", "cdn.hold"),
            hostsForProbeWave(
                healthy,
                preferredHost = "cdn.lead",
                seedHosts = listOf("play.anien.top", "cdn.hold", "localhost"),
                limit = 3,
            ),
        )
        // Seeds alone warm the dock after HOLD expires.
        assertEquals(
            listOf("play.anien.top", "cdn.affinity"),
            hostsForProbeWave(
                healthyHostsNewestFirst = emptyList(),
                preferredHost = null,
                seedHosts = listOf("play.anien.top", "cdn.affinity", "play.anien.top"),
                limit = 4,
            ),
        )
        // Quality sheet open: live host + affinity seeds fill the first Race wave
        // even when the short HOLD window has already dropped every remembered CDN.
        assertEquals(
            listOf("cdn.live", "play.anien.top", "cdn.affinity"),
            hostsForProbeWave(
                healthyHostsNewestFirst = emptyList(),
                preferredHost = "cdn.live",
                seedHosts = listOf("play.anien.top", "cdn.affinity"),
                limit = 4,
            ),
        )
        // Invalid hostnames never steal a slot.
        assertEquals(
            listOf("cdn.hold"),
            hostsForProbeWave(
                healthy,
                preferredHost = "localhost",
                seedHosts = listOf("", "cdn.example/path"),
                limit = 1,
            ),
        )
    }

    @Test
    fun concurrentRefreshJoinsTheActiveFlight() {
        assertTrue(shouldReuseProbeFlight(flightActive = true))
        assertFalse(shouldReuseProbeFlight(flightActive = false))
    }

    @Test
    fun ledgerAdvanceInvalidatesAnActiveFlight() {
        assertTrue(shouldInvalidateProbeFlight(true, flightGeneration = 1L, ledgerGeneration = 2L))
        assertFalse(shouldInvalidateProbeFlight(true, flightGeneration = 2L, ledgerGeneration = 2L))
        assertFalse(shouldInvalidateProbeFlight(false, flightGeneration = 1L, ledgerGeneration = 2L))
        assertTrue(shouldStartProbeFlight(false, flightGeneration = -1L, ledgerGeneration = 0L))
        assertTrue(shouldStartProbeFlight(true, flightGeneration = 1L, ledgerGeneration = 3L))
        assertFalse(shouldStartProbeFlight(true, flightGeneration = 3L, ledgerGeneration = 3L))
    }

    @Test
    fun preferredKickDoesNotJoinABackgroundWave() {
        // Background store refresh (no preferred) must restart for a Race-dock lead.
        assertTrue(
            shouldInvalidateProbeFlightForPreferred(
                flightActive = true,
                flightPreferred = null,
                requestedPreferred = "cdn.lead",
            ),
        )
        assertTrue(
            shouldInvalidateProbeFlightForPreferred(
                flightActive = true,
                flightPreferred = "cdn.other",
                requestedPreferred = "cdn.lead",
            ),
        )
        // Same preferred can coalesce; null request joins any active flight.
        assertFalse(
            shouldInvalidateProbeFlightForPreferred(
                flightActive = true,
                flightPreferred = "cdn.lead",
                requestedPreferred = "cdn.lead",
            ),
        )
        assertFalse(
            shouldInvalidateProbeFlightForPreferred(
                flightActive = true,
                flightPreferred = "cdn.lead",
                requestedPreferred = null,
            ),
        )
    }
}
