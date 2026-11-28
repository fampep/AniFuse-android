// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMenuGestureTest {
    @Test
    fun stickySubsStillBlocksTheaterGesturesSoListsCanScroll() {
        assertTrue(PlayerMenuPage.Subs.isStickyStylePage())
        assertTrue(playerMenuBlocksTheaterGestures(PlayerMenuPage.Subs))
        assertTrue(playerMenuBlocksTheaterGestures(PlayerMenuPage.Size))
        assertTrue(playerMenuBlocksTheaterGestures(PlayerMenuPage.Quality))
        assertTrue(playerMenuBlocksTheaterGestures(PlayerMenuPage.Episodes))
        assertFalse(playerMenuBlocksTheaterGestures(PlayerMenuPage.None))
    }
}
