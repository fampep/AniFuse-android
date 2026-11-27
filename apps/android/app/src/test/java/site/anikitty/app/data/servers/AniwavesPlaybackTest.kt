// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniwavesPlaybackTest {
    @Test
    fun searchKeepsTheExactTitleAndTheShortestContainingTitle() {
        val html = """
            <div class="item">
              <a href="/watch/one-piece-film-red-999">
                <img src="/a.jpg">
              </a>
              <a class="name d-title">One Piece Film Red</a>
            </div>
            <div class="item">
              <a href="/watch/one-piece-21">
                <img src="/b.jpg">
              </a>
              <a class="name d-title">One Piece</a>
            </div>
            <div class="item">
              <a href="/watch/kusuriya-no-hitorigoto-3rd-season-82762">
                <img src="/c.jpg">
              </a>
              <a class="name d-title">The Apothecary Diaries Season 3</a>
            </div>
        """.trimIndent()
        val hits = aniwavesSearchHits(html)
        assertEquals("21", aniwavesPickShow(hits, "One Piece")?.id)
        assertEquals("999", aniwavesPickShow(hits, "One Piece Film Red")?.id)
        assertEquals(
            "82762",
            aniwavesPickShow(hits, "The Apothecary Diaries Season 3")?.id,
        )
        assertEquals(
            "kusuriya-no-hitorigoto-3rd-season-82762",
            aniwavesPickShow(hits, "The Apothecary Diaries Season 3")?.slug,
        )
        assertNull(aniwavesPickShow(hits, "Naruto"))
        assertEquals(
            "82762",
            aniwavesShowId("/watch/kusuriya-no-hitorigoto-3rd-season-82762/ep-1"),
        )
    }

    @Test
    fun searchSlugDropsTheEpisodeAndTheCacheKeepsTheShow() {
        val html = """
            <a href="/watch/one-piece-21/ep-1"><img src="/b.jpg"></a>
            <a class="name d-title">One Piece</a>
        """.trimIndent()
        val show = aniwavesSearchHits(html).single()
        assertEquals("one-piece-21", show.slug)
        assertEquals("21", show.id)
        val cached = aniwavesCachedShow(show.slug, "https://aniwaves.tv/watch/${show.slug}/ep-1")
        assertEquals("21", cached?.id)
        assertEquals("one-piece-21", cached?.slug)
        val numeric = aniwavesCachedShow(
            "82762",
            "https://aniwaves.tv/watch/kusuriya-no-hitorigoto-3rd-season-82762/ep-1",
        )
        assertEquals("82762", numeric?.id)
        assertEquals("kusuriya-no-hitorigoto-3rd-season-82762", numeric?.slug)
    }

    @Test
    fun serverListKeepsEveryMirrorNameInTheAudioBlock() {
        val html = """
            <div class="type" data-type="sub">
              <ul>
                <li data-link-id="sub-a" data-sv-id="1">Vidplay</li>
                <li data-link-id="sub-b" data-sv-id="2">MegaPlay</li>
              </ul>
            </div>
            <div class="type" data-type="dub">
              <ul>
                <li data-link-id="dub-a" data-sv-id="1">Vidplay</li>
              </ul>
            </div>
        """.trimIndent()
        val sub = aniwavesServers(html, dub = false)
        assertEquals(listOf("sub-a", "sub-b"), sub.map { it.id })
        assertEquals(listOf("Vidplay", "MegaPlay"), sub.map { it.name })
        val dub = aniwavesServers(html, dub = true)
        assertEquals(listOf("dub-a"), dub.map { it.id })
        // Opposite audio is only a fallback when the preferred block is empty.
        val subOnly = """
            <div class="type" data-type="sub">
              <li data-link-id="only-sub">Moonlight</li>
            </div>
        """.trimIndent()
        assertEquals(listOf("only-sub"), aniwavesLinkIds(subOnly, dub = true))
        assertEquals("sub-a", aniwavesPickLink(html, dub = false))
        assertEquals(
            "https://play.vhscdn.to/embed-1/e/1",
            aniwavesEmbedUrl("""{"result":{"url":"https://play.vhscdn.to/embed-1/e/1"}}"""),
        )
        assertEquals("180398", aniwavesDataId("""<div id="player" data-id="180398"></div>"""))
        assertEquals(
            listOf(
                "https://megaplay.buzz/stream/getSourcesNew?id=180398",
                "https://megaplay.buzz/embed-1/getSources?id=180398",
            ),
            aniwavesSourcesCandidates("https://megaplay.buzz/stream/s-2/1/sub", "180398"),
        )
        assertEquals(
            "https://play.vhscdn.to/embed-1/getSources?id=180398",
            aniwavesSourcesUrl("https://play.vhscdn.to/embed-1/e/1", "180398"),
        )
    }

    @Test
    fun playlistBecomesAPlayableSource() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720
            720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080
            1080.m3u8
        """.trimIndent()
        val source = aniwavesPlayable(
            url = "https://cdn.example/master.m3u8",
            playlist = playlist,
            dub = false,
            tracks = emptyList(),
            skip = aniwavesSkip("""{"intro":{"start":1,"end":2}}"""),
            referer = "https://megaplay.buzz/",
            origin = "https://megaplay.buzz",
            userAgent = AniwavesServer.USER_AGENT,
        )
        assertEquals("1080p", source?.quality)
        assertEquals("ja", source?.audioLanguage)
        assertEquals("https://megaplay.buzz/", source?.headers?.get("Referer"))
        assertEquals("https://megaplay.buzz", source?.headers?.get("Origin"))
        assertTrue(source?.hls == true)
        assertEquals(1.0, source?.skip?.intro?.startSec)
        assertNull(
            aniwavesPlayable(
                url = "https://aniwaves.tv/master.m3u8",
                playlist = playlist,
                dub = false,
                tracks = emptyList(),
                skip = site.anikitty.app.data.skip.SkipTimes(),
                referer = "https://aniwaves.tv/",
                origin = "https://aniwaves.tv",
                userAgent = AniwavesServer.USER_AGENT,
            ),
        )
    }

    @Test
    fun mediaUrlReadsPlainAndEncryptedSources() {
        assertEquals(
            "https://cdn.example/master.m3u8",
            aniwavesMediaUrl("""{"sources":[{"file":"https://cdn.example/master.m3u8","type":"hls"}]}"""),
        )
        assertEquals(
            "https://cdn.example/master.m3u8",
            aniwavesMediaUrl(
                """{"file":"https://cdn.example/master.m3u8"}""",
                MegaPlayCrypto(),
                "https://megaplay.buzz",
            ),
        )
        assertNull(aniwavesMediaUrl("""{"sources":"not-a-url"}"""))
        assertFalse(aniwavesPlayableHost("https://aniwaves.tv/watch/show-1/"))
        assertTrue(aniwavesPlayableHost("https://cdn.example/master.m3u8"))
    }

    @Test
    fun messagesStayFreeOfStatusCodes() {
        assertTrue(aniwavesBlocked(403, ""))
        assertTrue(aniwavesBlocked(200, "<title>Just a moment...</title>"))
        assertTrue(aniwavesBlocked(200, "Performing security verification"))
        assertFalse(aniwavesBlocked(200, """{"result":{"url":"https://x"}}"""))
        assertFalse(AniwavesServer.MISSING.contains("404"))
        assertFalse(AniwavesServer.BLOCKED.contains("403"))
        assertTrue(AniwavesServer.ORIGIN.contains("aniwaves.tv"))
        assertFalse(AniwavesServer.ORIGIN.contains("aniwaves.org"))
    }
}
