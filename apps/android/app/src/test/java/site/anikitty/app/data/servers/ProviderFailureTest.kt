// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFailureTest {
    @Test
    fun missingEpisodesStaySoft() {
        assertFalse(isHardProviderFailure("AniRex does not have this episode."))
        assertFalse(isHardProviderFailure("AnimeHeaven only has subbed episodes."))
        assertFalse(isHardProviderFailure("gogoanime needs a title to find this show."))
        assertFalse(isHardProviderFailure(null))
        assertFalse(isHardProviderFailure(""))
    }

    @Test
    fun unreachableAndChallengesAreHard() {
        assertTrue(isHardProviderFailure("AniRex could not be reached."))
        assertTrue(isHardProviderFailure("This source is blocked by the site. Try another source."))
        assertTrue(isHardProviderFailure("timeout waiting for CDN"))
        assertFalse(isHardProviderFailure("empty sources"))
        assertTrue(isHardProviderFailure("HTTP 403 from mirror"))
        assertTrue(isHardProviderFailure("cloudflare challenge"))
    }

    @Test
    fun httpStatusMaps404ToMissing() {
        assertEquals(
            "gone",
            providerHttpFailure(404, """{"message":"No stream"}""", missing = "gone", blocked = "down"),
        )
        assertEquals(
            "down",
            providerHttpFailure(403, "Just a moment...", missing = "gone", blocked = "down"),
        )
        assertEquals(
            "down",
            providerHttpFailure(0, "", missing = "gone", blocked = "down"),
        )
        assertEquals(
            "down",
            providerHttpFailure(500, "<html>", missing = "gone", blocked = "down"),
        )
    }
}
