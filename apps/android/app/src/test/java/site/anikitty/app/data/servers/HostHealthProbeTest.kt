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
        // Preferred not in the healthy list → just take the freshest.
        assertEquals(
            listOf("cdn.new"),
            hostsForProbeWave(healthy, preferredHost = "cdn.sunk", limit = 1),
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
