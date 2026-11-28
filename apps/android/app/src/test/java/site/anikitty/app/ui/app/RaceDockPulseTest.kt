// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaceDockPulseTest {
    @Test
    fun leadWinsOverEveryPulse() {
        assertEquals(
            "LEAD · 40ms",
            raceDockPulseLabel(selected = true, cold = true, score = -1, latencyMs = 40L),
        )
        assertEquals(
            "LEAD",
            raceDockPulseLabel(selected = true, cold = false, score = 0, latencyMs = null),
        )
        assertEquals("LEAD · 12ms", raceDockLeadLabel(12L))
        assertEquals("STANDBY", raceDockStandbyLabel(null))
        assertEquals("STANDBY · 55ms", raceDockStandbyLabel(55L))
    }

    @Test
    fun downWhenColdOrSunk() {
        assertEquals(
            "DOWN",
            raceDockPulseLabel(selected = false, cold = true, score = 0, latencyMs = null),
        )
        assertEquals(
            "DOWN",
            raceDockPulseLabel(selected = false, cold = false, score = -1_000, latencyMs = null),
        )
    }

    @Test
    fun latencyBeatsHotWhenMeasured() {
        assertEquals(
            "42ms",
            raceDockPulseLabel(selected = false, cold = false, score = 900, latencyMs = 42L),
        )
        assertEquals(
            "HOT",
            raceDockPulseLabel(selected = false, cold = false, score = 900, latencyMs = null),
        )
        assertNull(
            raceDockPulseLabel(selected = false, cold = false, score = 0, latencyMs = null),
        )
        assertEquals("lead", raceDockPulseKind(selected = true, cold = false, score = 0, latencyMs = 40L))
        assertEquals("rtt", raceDockPulseKind(selected = false, cold = false, score = 900, latencyMs = 42L))
        assertEquals("hot", raceDockPulseKind(selected = false, cold = false, score = 900, latencyMs = null))
        assertEquals("down", raceDockPulseKind(selected = false, cold = true, score = 0, latencyMs = null))
        assertEquals("none", raceDockPulseKind(selected = false, cold = false, score = 0, latencyMs = null))
        assertEquals(42L, raceDockPulseMs(42L))
        assertNull(raceDockPulseMs(0L))
    }

    @Test
    fun dockOrdersByScoreDescending() {
        assertEquals(
            listOf("fast", "mid", "cold"),
            raceDockOrdered(listOf("cold", "fast", "mid")) {
                when (it) {
                    "fast" -> 900
                    "mid" -> 100
                    else -> 0
                }
            },
        )
    }
}
