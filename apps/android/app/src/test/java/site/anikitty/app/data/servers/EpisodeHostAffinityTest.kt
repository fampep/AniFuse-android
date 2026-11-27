// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeHostAffinityTest {
    @Test
    fun boostPutsAffinitySecondAndKeyNamesAudio() {
        val ordered = listOf("pick", "a", "anipm", "b")
        assertEquals(
            listOf("pick", "anipm", "a", "b"),
            boostAffinityProvider(ordered, "anipm") { it },
        )
        assertEquals(ordered, boostAffinityProvider(ordered, null) { it })
        assertEquals(ordered, boostAffinityProvider(ordered, "missing") { it })
        assertEquals("154587:1:sub", EpisodeHostAffinity.key(154587, 1, "sub"))
        assertTrue(EpisodeHostAffinity.key(1, 2, "DUB").endsWith(":dub"))
    }
}
