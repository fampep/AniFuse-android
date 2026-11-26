// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnigatoPlaybackTest {
    @Test
    fun watchUsesTheAnilistIdAndTheRequestedLanguage() {
        assertEquals(
            "https://anigato.lol/api/watch/116674/sub/1",
            anigatoWatchUrl(116674, 1, dub = false),
        )
        assertEquals(
            "https://anigato.lol/api/watch/20/dub/1",
            anigatoWatchUrl(20, 1, dub = true),
        )
        assertEquals("", anigatoWatchUrl(0, 1, dub = false))
        assertEquals("", anigatoWatchUrl(20, 0, dub = false))
        assertEquals("https://anigato.lol/api/episodes/116674", anigatoEpisodesUrl(116674))
        assertEquals("", anigatoEpisodesUrl(-1))
    }

    @Test
    fun episodeListKeepsNumbersAndAMissingShowIsEmpty() {
        val numbers = anigatoEpisodeNumbers(
            """{"success":true,"episodes":[{"number":1},{"number":220},{"number":0}]}""",
        )
        assertEquals(listOf(1, 220), numbers)
        assertEquals(emptyList<Int>(), anigatoEpisodeNumbers("""{"success":false,"episodes":[]}"""))
        assertNull(anigatoEpisodeNumbers("<html></html>"))
    }

    @Test
    fun proxyPlaylistIsKeptAndTheSelfHostIsDropped() {
        val json = """
            {
              "success": true,
              "streams": [
                {"name":"Self","type":"sub","url":"https://anigato.lol/20/1/sub/master.m3u8?t=1"},
                {
                  "name":"Main",
                  "type":"sub",
                  "url":"/api/stream/proxy?url=token",
                  "subtitles":[{"url":"/api/sub.vtt?url=caption","lang":"English","label":"English"}]
                },
                {"name":"Other","type":"sub","url":"https://evil.example/api/stream/proxy?url=1"},
                {"name":"Dub","type":"dub","url":"/api/stream/main2.m3u8?url=token"}
              ]
            }
        """.trimIndent()
        val sub = anigatoStreams(json, dub = false)
        assertEquals(1, sub?.size)
        assertEquals("https://anigato.lol/api/stream/proxy?url=token", sub!![0].url)
        assertEquals("https://anigato.lol/api/sub.vtt?url=caption", sub[0].subtitles.single().url)
        assertEquals("en", sub[0].subtitles.single().language)
        assertEquals(
            "https://anigato.lol/api/stream/main2.m3u8?url=token",
            anigatoStreams(json, dub = true)!!.single().url,
        )
    }

    @Test
    fun dubRequestDropsAResponseTheSiteMarksUnavailable() {
        val json = """
            {"success":true,"dubUnavailable":true,"streams":[
              {"type":"dub","url":"/api/stream/proxy?url=token"}
            ]}
        """.trimIndent()
        assertTrue(anigatoStreams(json, dub = true)!!.isEmpty())
        assertTrue(anigatoStillLoading("""{"success":true,"loadingMore":true,"streams":[]}"""))
        assertFalse(anigatoStillLoading("""{"success":false,"loadingMore":true}"""))
    }

    @Test
    fun playlistUrlStaysOnTheSiteStreamPath() {
        assertEquals(
            "https://anigato.lol/api/stream/proxy?url=token",
            anigatoPlaylistUrl("/api/stream/proxy?url=token"),
        )
        assertNull(anigatoPlaylistUrl("https://anigato.lol/20/1/sub/master.m3u8"))
        assertNull(anigatoPlaylistUrl("https://evil.example/api/stream/proxy?url=1"))
        assertNull(anigatoPlaylistUrl("http://anigato.lol/api/stream/proxy?url=1"))
        assertNull(anigatoPlaylistUrl("https://user:pass@anigato.lol/api/stream/proxy?url=1"))
        assertNull(anigatoPlaylistUrl("/api/stream/../watch/1"))
        assertTrue(anigatoPlaylistBody("\n#EXTM3U\n"))
        assertFalse(anigatoPlaylistBody("SELFP1.token"))
        assertEquals(1080, anigatoHeight("#EXT-X-STREAM-INF:RESOLUTION=1920x1080"))
        assertEquals(0, anigatoHeight("#EXTM3U"))
    }

    @Test
    fun playableCarriesTheMasterAndTheSiteHeaders() {
        val playlist = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2320106,RESOLUTION=1920x1080\nvideo.m3u8\n"
        val source = anigatoPlayable(
            url = "/api/stream/proxy?url=token",
            playlist = playlist,
            dub = false,
            tracks = emptyList(),
            userAgent = AnigatoServer.USER_AGENT,
        )
        assertEquals("https://anigato.lol/api/stream/proxy?url=token", source?.url)
        assertEquals("https://anigato.lol/", source?.headers?.get("Referer"))
        assertEquals("https://anigato.lol", source?.headers?.get("Origin"))
        assertEquals("1080p", source?.quality)
        assertEquals("SUB", source?.audio)
        assertEquals("ja", source?.audioLanguage)
        assertTrue(source?.hls == true)
        assertNull(
            anigatoPlayable(
                url = "https://anigato.lol/20/1/sub/master.m3u8",
                playlist = playlist,
                dub = false,
                tracks = emptyList(),
                userAgent = AnigatoServer.USER_AGENT,
            ),
        )
        assertNull(
            anigatoPlayable(
                url = "/api/stream/proxy?url=token",
                playlist = "SELFP1.token",
                dub = true,
                tracks = emptyList(),
                userAgent = AnigatoServer.USER_AGENT,
            ),
        )
    }

    @Test
    fun challengePageIsAMiss() {
        assertTrue(anigatoChallenge(403, ""))
        assertTrue(anigatoChallenge(200, "<title>Just a moment...</title> challenge-platform"))
        assertFalse(anigatoChallenge(404, """{"success":false}"""))
        assertFalse(AnigatoServer.MISSING.contains("403"))
        assertFalse(AnigatoServer.BLOCKED.contains("404"))
        assertFalse(AnigatoServer.UNPLAYABLE.contains("403"))
    }
}
