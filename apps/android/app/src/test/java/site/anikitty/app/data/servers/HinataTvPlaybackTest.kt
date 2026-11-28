// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HinataTvPlaybackTest {
    @Test
    fun bundledCardStaysHinataBranded() {
        assertEquals("hinatatv", HinataTvServer.ID)
        assertEquals("HinataTV", HinataTvServer(cache = null).server.name)
        assertEquals(HinataTvServer.SITE, HinataTvServer(cache = null).server.siteUrl)
        assertTrue(HinataTvServer.MISSING.contains("HinataTV"))
        assertTrue(HinataTvServer.BLOCKED.contains("reached"))
    }

    @Test
    fun aniEmbedRedirectYieldsMalAndPlaylist() {
        assertEquals(
            "https://animepapa.cyou/embed/ani/195539/1/sub",
            hinataPapaAniEmbedUrl(195539, 1, "sub"),
        )
        assertEquals(
            "https://animepapa.cyou/embed/ani/195539/2/dub",
            hinataPapaAniEmbedUrl(195539, 2, "dub"),
        )
        assertEquals(61990, hinataMalFromPapaEmbedUrl("/embed/mal/61990/1/sub"))
        assertEquals(
            61990,
            hinataMalFromPapaEmbedUrl("https://animepapa.cyou/embed/mal/61990/10/dub"),
        )
        assertNull(hinataMalFromPapaEmbedUrl("/embed/ani/195539/1/sub"))
        assertNull(hinataMalFromPapaEmbedUrl(null))

        assertEquals(
            "https://animepapa.cyou/playlists/61990_ep1/sub.direct.pls?t=tok",
            anipmMediaPlaylistUrl("https://animepapa.cyou", 61990, 1, "sub", "tok"),
        )
        assertTrue(anipmIsHls("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nsub.direct.pls"))
    }

    @Test
    fun availabilityJsonGatesMissingEpisodes() {
        val payload = """{"episodes":[1,2,3,4,5,6,7,8,9,10]}"""
        assertTrue(hinataAvailabilityHasEpisode(payload, 1))
        assertTrue(hinataAvailabilityHasEpisode(payload, 10))
        assertFalse(hinataAvailabilityHasEpisode(payload, 11))
        assertFalse(hinataAvailabilityHasEpisode("""{"episodes":[]}""", 1))
        // Unknown / non-JSON → do not hard-block resolve.
        assertTrue(hinataAvailabilityHasEpisode("nope", 1))
        assertFalse(hinataChallenged(200, "#EXTM3U"))
        assertTrue(hinataChallenged(403, "Just a moment…"))
    }

    @Test
    fun malLookupFailureMarksOutagesHardAndMissesSoft() {
        assertEquals(HinataTvServer.BLOCKED, hinataMalLookupFailure(reached = false, challenged = false))
        assertEquals(HinataTvServer.BLOCKED, hinataMalLookupFailure(reached = true, challenged = true))
        assertEquals(HinataTvServer.MISSING, hinataMalLookupFailure(reached = true, challenged = false))
        assertTrue(isHardProviderFailure(HinataTvServer.BLOCKED))
        assertFalse(isHardProviderFailure(HinataTvServer.MISSING))
        assertEquals(
            "https://animepapa.cyou/embed/mal/61990/1/sub",
            hinataAbsolutizePapaUrl("/embed/mal/61990/1/sub"),
        )
        assertEquals(
            "https://animepapa.cyou/embed/mal/61990/1/sub",
            hinataAbsolutizePapaUrl("https://animepapa.cyou/embed/mal/61990/1/sub"),
        )
    }
}
