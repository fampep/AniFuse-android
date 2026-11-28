// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.extensions.isRetiredExtension

class AniPmPlaybackTest {
    @Test
    fun onlyOneBundledAniPmCard() {
        assertEquals("anipm", AniPmServer.ID)
        assertEquals("ani.pm", AniPmServer(cache = null).server.name)
        assertEquals(AniPmServer.SITE, AniPmServer(cache = null).server.siteUrl)
        assertEquals(listOf("https://anipm.pk", "https://ani.pm"), AniPmServer.ORIGINS)
        assertFalse(isRetiredExtension("anipm", "ani.pm", "https://ani.pm"))
        // Second / community ani.pm packs are retired so the Store shows one card.
        assertTrue(isRetiredExtension("site.anikitty.anipm", "Ani.pm", "https://anipm.pk"))
        assertTrue(isRetiredExtension("anipm-mirror", "ani.pm", null))
        assertTrue(isRetiredExtension("community-pack", "Something", "https://anipm.pk/watch"))
        assertFalse(AniPmServer.MISSING.contains("404"))
        assertFalse(AniPmServer.BLOCKED.contains("403"))
    }

    @Test
    fun playerApiLookupAndSessionBuildPlaylist() {
        val lookup = """
            {"data":[{"found":true,"query":{"anilist":154587},
              "title":{"anilistId":154587,"malId":52991,"title":"Frieren: Beyond Journey's End"}}]}
        """.trimIndent()
        assertEquals(52991, anipmMalFromLookup(lookup, 154587))

        val titles = """
            {"data":[
              {"anilistId":21,"malId":21,"title":"One Piece"},
              {"anilistId":154587,"malId":52991,"title":"Frieren: Beyond Journey's End"}
            ],"total":2}
        """.trimIndent()
        assertEquals(52991, anipmPickTitle(titles, 154587, "Frieren"))

        val session = anipmParseSession(
            """{"expiresAt":1795830775,"token":"2.52991.1.tok"}""",
        )
        assertEquals("2.52991.1.tok", session?.token)
        // Wire expiry is Unix seconds — cache stores millis.
        assertEquals(1_795_830_775_000L, session?.expiresAt)
        assertEquals(1_795_830_775_000L, anipmExpiresAtMs(1_795_830_775L))
        assertEquals(1_795_830_775_000L, anipmExpiresAtMs(1_795_830_775_000L))
        assertEquals(0L, anipmExpiresAtMs(0L))
        assertEquals(
            "https://anipm.pk/playlists/52991_ep1/sub.direct.pls?t=2.52991.1.tok",
            anipmMediaPlaylistUrl("https://anipm.pk", 52991, 1, "sub", "2.52991.1.tok"),
        )
        assertEquals(
            "https://ani.pm/playlists/52991_ep1/dub.direct.pls?t=2.52991.1.tok",
            anipmMediaPlaylistUrl("https://ani.pm", 52991, 1, "dub", "2.52991.1.tok"),
        )
        assertTrue(anipmIsHls("#EXTM3U\n#EXTINF:4.0,\nseg.ts"))
        assertFalse(anipmIsHls("player required"))
        assertNull(anipmParseSession("""{"error":{"code":"media-unavailable"}}"""))
    }

    @Test
    fun skipAndSubtitlesParse() {
        val skip = anipmSkip("""{"op":{"start":10.5,"end":90.0},"ed":{"start":1300.0,"end":1350.0}}""")
        assertEquals(10.5, skip.intro?.startSec ?: -1.0, 0.01)
        assertEquals(1350.0, skip.outro?.endSec ?: -1.0, 0.01)

        val tracks = anipmSubtitles(
            """{"tracks":[{"default":true,"label":"English","lang":"en","type":"vtt","url":"/subtitles/52991/1/sub/1.vtt"}]}""",
            origin = "https://anipm.pk",
            token = "tok",
        )
        assertEquals(1, tracks.size)
        assertEquals("English", tracks[0].label)
        assertTrue(tracks[0].url.startsWith("https://anipm.pk/subtitles/"))
        assertTrue(tracks[0].url.contains("t=tok"))
        assertEquals(listOf("Frieren"), anipmTitles("Frieren || mal:52991"))
    }

    @Test
    fun sessionCacheSkipsColdRoundTripUntilSkew() {
        AnipmSessionCache.clear()
        // Live wire seconds → millis via parse; cache keys compare wall-clock millis.
        val parsed = anipmParseSession("""{"expiresAt":1795830775,"token":"warm-tok"}""")!!
        AnipmSessionCache.put("https://anipm.pk", 52991, 1, "sub", parsed)
        assertEquals(
            "warm-tok",
            AnipmSessionCache.get(
                "https://anipm.pk/",
                52991,
                1,
                "SUB",
                nowMs = parsed.expiresAt - AnipmSessionCache.SKEW_MS - 1_000L,
            )?.token,
        )
        // Inside skew window → miss so Media3 never rides an expiring token.
        assertNull(
            AnipmSessionCache.get(
                "https://anipm.pk",
                52991,
                1,
                "sub",
                nowMs = parsed.expiresAt - AnipmSessionCache.SKEW_MS + 1L,
            ),
        )
        // Papa origin is a separate lane from ani.pm.
        assertNull(AnipmSessionCache.get(HinataTvServer.PAPA, 52991, 1, "sub", nowMs = 1_000L))
        AnipmSessionCache.put(HinataTvServer.PAPA, 61990, 1, "dub", AnipmSession("papa", 9_999_999L))
        assertEquals(
            "papa",
            AnipmSessionCache.get(HinataTvServer.PAPA, 61990, 1, "dub", nowMs = 1_000L)?.token,
        )
        AnipmSessionCache.clear()
    }
}
