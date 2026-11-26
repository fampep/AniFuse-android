// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

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
              <a href="/watch/tsuihou-sareta-tensei-juukishi-wa-game-chishiki-de-musou-suru-82737">
                <img src="/c.jpg">
              </a>
              <a class="name d-title">The Exiled Heavy Knight Knows How to Game the System</a>
            </div>
        """.trimIndent()
        val hits = aniwavesSearchHits(html)
        assertEquals("21", aniwavesPickShow(hits, "One Piece")?.id)
        assertEquals("999", aniwavesPickShow(hits, "One Piece Film Red")?.id)
        assertEquals(
            "82737",
            aniwavesPickShow(hits, "The Exiled Heavy Knight Knows How to Game the System")?.id,
        )
        assertEquals(
            "tsuihou-sareta-tensei-juukishi-wa-game-chishiki-de-musou-suru-82737",
            aniwavesPickShow(hits, "The Exiled Heavy Knight Knows How to Game the System")?.slug,
        )
        assertNull(aniwavesPickShow(hits, "Naruto"))
        assertEquals("82737", aniwavesShowId("/watch/tsuihou-sareta-tensei-juukishi-wa-game-chishiki-de-musou-suru-82737/ep-1"))
    }

    @Test
    fun serverListKeepsSubAndDubApart() {
        val html = """
            <div class="type" data-type="sub"><a data-link-id="sub-1"></a><a data-link-id="sub-2"></a></div>
            <div class="type" data-type="dub"><a data-link-id="dub-9"></a></div>
        """.trimIndent()
        assertEquals("sub-1", aniwavesPickLink(html, dub = false))
        assertEquals("dub-9", aniwavesPickLink(html, dub = true))
        assertNull(aniwavesPickLink("""<div class="type" data-type="sub"><a data-link-id="only-sub"></a></div>""", dub = true))
        assertNull(aniwavesPickLink("""<div class="type" data-type="dub"><a data-link-id="only-dub"></a></div>""", dub = false))
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
        val cached = aniwavesCachedShow(show.slug, "https://aniwaves.tv/watch/${show.slug}")
        assertEquals("21", cached?.id)
        assertEquals("one-piece-21", cached?.slug)
        val numeric = aniwavesCachedShow("82737", "https://aniwaves.tv/watch/tsuihou-sareta-tensei-juukishi-wa-game-chishiki-de-musou-suru-82737")
        assertEquals("82737", numeric?.id)
        assertEquals("tsuihou-sareta-tensei-juukishi-wa-game-chishiki-de-musou-suru-82737", numeric?.slug)
    }

    @Test
    fun embedResolvesToThePlaylistOnThatHost() {
        val sourced = """{"result":{"url":"https://play.example/embed-1/abc?v=1","skip_data":{"intro":[12,90],"outro":[1300,1400]}}}"""
        assertEquals("https://play.example/embed-1/abc?v=1", aniwavesEmbedUrl(sourced))
        assertEquals("https://play.example", aniwavesOrigin(aniwavesEmbedUrl(sourced)!!))
        assertEquals(
            "https://play.example/embed-1/getSources?id=src-1",
            aniwavesSourcesUrl("https://play.example/embed-1/abc?v=1", "src-1"),
        )
        val page = """<div id="player" data-id="src-1"></div>"""
        assertEquals("src-1", aniwavesDataId(page))
        val sources = """
            {"sources":[{"file":"https://cdn.example/master.m3u8","type":"hls"}],
             "tracks":[{"file":"https://cdn.example/en.vtt","label":"English","kind":"captions","lang":"en"}]}
        """.trimIndent()
        assertEquals("https://cdn.example/master.m3u8", aniwavesMediaUrl(sources))
        assertEquals("English", aniwavesTracks(sources).single().label)
        assertEquals(12.0, aniwavesSkip(sourced).intro?.startSec)
        assertEquals(1400.0, aniwavesSkip(sourced).outro?.endSec)
        assertNull(aniwavesMediaUrl("""{"sources":"not-a-url"}"""))
        assertFalse(aniwavesPlayableHost("https://aniwaves.tv/watch/show-1/ep-1"))
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
            skip = aniwavesSkip("""{"skip_data":{"intro":[1,2]}}"""),
            referer = "https://play.example/",
            origin = "https://play.example",
            userAgent = AniwavesServer.USER_AGENT,
        )
        assertEquals("1080p", source?.quality)
        assertEquals("ja", source?.audioLanguage)
        assertEquals("https://play.example/", source?.headers?.get("Referer"))
        assertEquals("https://play.example", source?.headers?.get("Origin"))
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
    fun messagesStayFreeOfStatusCodes() {
        assertTrue(aniwavesBlocked(403, ""))
        assertTrue(aniwavesBlocked(200, "<title>Just a moment...</title>"))
        assertFalse(aniwavesBlocked(200, """{"result":{"url":"https://play.example/a"}}"""))
        assertFalse(AniwavesServer.MISSING.contains("404"))
        assertFalse(AniwavesServer.BLOCKED.contains("403"))
    }
}
