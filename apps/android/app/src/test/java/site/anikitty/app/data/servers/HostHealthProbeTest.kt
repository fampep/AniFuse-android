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
    fun stampKeepsTheFasterRtt() {
        assertEquals(90L, hostProbeLatencyToStamp(previousMs = 90L, sampleMs = 1_200L))
        assertEquals(40L, hostProbeLatencyToStamp(previousMs = 90L, sampleMs = 40L))
        assertEquals(55L, hostProbeLatencyToStamp(previousMs = null, sampleMs = 55L))
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
}
