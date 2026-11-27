// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertTrue
import org.junit.Test

class PlayableWarmProbeTest {
    @Test
    fun emptyListStaysEmpty() = kotlinx.coroutines.runBlocking {
        assertTrue(PlayableWarmProbe.filterAlive(emptyList()).isEmpty())
    }
}
