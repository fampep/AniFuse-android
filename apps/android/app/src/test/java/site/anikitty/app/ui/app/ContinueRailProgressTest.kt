// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueRailProgressTest {
    @Test
    fun clampsProgressOntoTheGlassRail() {
        assertEquals(0f, continueRailProgress(-0.2f), 0f)
        assertEquals(0.42f, continueRailProgress(0.42f), 0f)
        assertEquals(1f, continueRailProgress(1.5f), 0f)
    }
}
