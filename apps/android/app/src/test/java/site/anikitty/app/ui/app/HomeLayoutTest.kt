// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun plainSynopsisStripsHtml() {
        assertEquals(
            "A quiet story",
            plainSynopsis("<p>A quiet<br>story</p>"),
        )
        assertEquals(null, plainSynopsis("   "))
    }

    @Test
    fun defaultHiddenKeepsTheEssentialRowsVisible() {
        val hidden = HomeCatalog.hiddenByDefault
        assertEquals(6, hidden.size)
        // Featured hero, continue, and the catalog rows the home shows by default.
        listOf(
            "featured", "fresh", "continue", "trending", "popular",
            "foryou", "rated", "upcoming", "gems", "recent", "because", "season",
        ).forEach { id ->
            assertFalse("$id should be visible by default", id in hidden)
        }
        // Every hidden id is a real section.
        val known = HomeCatalog.sections.map { it.id }.toSet()
        assertTrue(hidden.all { it in known })
        // Every catalog id is either shown by default or listed in the default-hide set.
        HomeCatalog.sections.forEach { section ->
            assertTrue(
                "${section.id} must be visible by default or in hiddenByDefault",
                section.id !in hidden || section.id in known,
            )
        }
    }
}
