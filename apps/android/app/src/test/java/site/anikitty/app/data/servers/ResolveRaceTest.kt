// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveRaceTest {
    @Test
    fun absoluteTimeoutCoversSlidingWaves() {
        assertEquals(0L, raceAbsoluteTimeoutMs(0, 3, 1_500L, 8_000L))
        assertEquals(1_500L + 8_000L + 500L, raceAbsoluteTimeoutMs(2, 3, 1_500L, 8_000L))
        assertEquals(1_500L + 16_000L + 500L, raceAbsoluteTimeoutMs(5, 3, 1_500L, 8_000L))
        assertEquals(5L, RACE_POLL_MS)
    }

    @Test
    fun firstNonNullWinsAndMissesReturnNull() = runBlocking {
        val won = raceHeadStart(
            count = 3,
            parallel = 3,
            headStartMs = 0L,
            timeoutMs = 500L,
        ) { index ->
            if (index == 1) "ok" else null
        }
        assertEquals(1 to "ok", won)

        val miss = raceHeadStart(
            count = 2,
            parallel = 2,
            headStartMs = 0L,
            timeoutMs = 80L,
        ) { null }
        assertNull(miss)
        assertTrue(RACE_POLL_MS < 20L)
    }
}
