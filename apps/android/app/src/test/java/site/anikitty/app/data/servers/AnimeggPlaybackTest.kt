// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeggPlaybackTest {
    @Test
    fun searchMatchesTheTitleAndLeavesALongerShowAlone() {
        val html = """
            <a href="/series/one-piece-film-red" class="mse">
              <h2>One Piece Film: Red</h2>
              <div>Alt Titles : ワンピース フィルム レッド</div>
            </a>
            <a href="/series/one-piece" class="mse">
              <h2>One Piece</h2>
              <div>Alt Titles : ワンピース</div>
            </a>
        """.trimIndent()
        val hits = animeggSearchHits(html)
        assertEquals(listOf("one-piece-film-red", "one-piece"), hits.map { it.slug })
        assertEquals("one-piece", animeggPickShow(hits, "One Piece")?.slug)
        assertEquals("one-piece-film-red", animeggPickShow(hits, "One Piece Film: Red")?.slug)
        assertNull(animeggPickShow(hits, "Naruto"))
    }

    @Test
    fun anAlternateTitleMatchesTheShowAndNotAMovie() {
        val html = """
            <a href="/series/attack-on-titan" class="mse">
              <h2>Attack on Titan</h2>
              <div>Alt Titles : Shingeki no Kyojin, 進撃の巨人</div>
            </a>
            <a href="/series/shingeki-no-kyojin-movie-1-guren-no-yumiya" class="mse">
              <h2>Shingeki no Kyojin Movie 1: Guren no Yumiya</h2>
            </a>
            <a href="/series/attack-on-titan-special" class="mse">
              <h2>Attack on Titan Special</h2>
              <div>Alt Titles : Shingeki no Kyojin Episode 13.5</div>
            </a>
        """.trimIndent()
        val hits = animeggSearchHits(html)
        assertEquals("attack-on-titan", animeggPickShow(hits, "Shingeki no Kyojin")?.slug)
        assertEquals("attack-on-titan", animeggPickShow(hits, "進撃の巨人")?.slug)
        assertEquals(
            "shingeki-no-kyojin-movie-1-guren-no-yumiya",
            animeggPickShow(hits, "Shingeki no Kyojin Movie 1: Guren no Yumiya")?.slug,
        )
    }

    @Test
    fun seriesPageListsTheEpisodeAndItsLanguages() {
        val html = """
            <ul class="newmanga">
              <li>
                <div><a href="/one-piece-episode" class="anm_det_pop">skip</a></div>
              </li>
              <li>
                <div><a href="/one-piece-episode-1" class="anm_det_pop"><strong>One Piece 1</strong></a>
                  <i class="anititle">I'm Luffy!</i>
                  <a href="/one-piece-episode-1#subbed"><span class="btn-xs btn-subbed">SUBBED</span></a>
                  <a href="/one-piece-episode-1#dubbed"><span class="btn-xs btn-dubbed">DUBBED</span></a>
                </div>
              </li>
              <li>
                <div><a href="/one-piece-episode-630" class="anm_det_pop"><strong>One Piece 630</strong></a>
                  <a href="/one-piece-episode-630#raw"><span class="btn-xs btn-raw">RAW</span></a>
                </div>
              </li>
            </ul>
        """.trimIndent()
        val rows = animeggEpisodes(html)
        assertEquals(listOf(1, 630), rows.map { it.number })
        assertEquals("https://www.animegg.org/one-piece-episode-1", rows[0].url)
        assertTrue(rows[0].sub)
        assertTrue(rows[0].dub)
        assertFalse(rows[0].raw)
        assertFalse(rows[1].sub)
        assertFalse(rows[1].dub)
        assertTrue(rows[1].raw)
    }

    @Test
    fun episodePageKeepsTheAnimeggEmbedForTheRequestedLanguage() {
        val html = """
            <div id="subbed-Animegg" class="tab-pane">
              <iframe src="/embed/27657" class="video"></iframe>
            </div>
            <div id="dubbed-Other" class="tab-pane">
              <iframe src="https://mp4upload.com/embed-abc.html" class="video"></iframe>
            </div>
            <div id="dubbed-Animegg" class="tab-pane">
              <iframe src="/embed/27658" class="video"></iframe>
            </div>
            <div id="raw-Animegg" class="tab-pane">
              <iframe src="//www.animegg.org/embed/9" class="video"></iframe>
            </div>
        """.trimIndent()
        val mirrors = animeggMirrors(html)
        assertTrue(mirrors.none { it.mirror == "Other" })
        assertEquals("/embed/27657", animeggChooseMirror(mirrors, dub = false)?.embedPath)
        assertEquals("/embed/27658", animeggChooseMirror(mirrors, dub = true)?.embedPath)
        assertEquals(
            "/embed/9",
            animeggChooseMirror(
                mirrors.filterNot { it.version == "subbed" },
                dub = false,
            )?.embedPath,
        )
        assertNull(animeggChooseMirror(mirrors.filter { it.version == "dubbed" && it.mirror == "Other" }, dub = true))
    }

    @Test
    fun embedKeepsThePublicMp4AndDropsTheBackupHost() {
        val html = """
            var videoSources = [
              {file: "/play/55976/video.mp4?for=101", label: "360p", bk: "aHR0cHMlM0ElMkYlMkZtcDR1cGxvYWQuY29t", isBk: false},
              {file: "/play/240636/video.mp4?for=101", label: "1080p", bk: "", isBk: false},
              {file: "https://mp4upload.com/embed-abc.html", label: "1080p", bk: "", isBk: false},
              {file: "/play/240656/video.mp4", label: "720p", bk: "", isBk: false}
            ];
        """.trimIndent()
        val files = animeggFiles(html)
        assertEquals(
            listOf(
                "https://www.animegg.org/play/240636/video.mp4?for=101",
                "https://www.animegg.org/play/240656/video.mp4",
                "https://www.animegg.org/play/55976/video.mp4?for=101",
            ),
            files.map { it.url },
        )
        assertEquals(listOf("1080p", "720p", "360p"), files.map { it.label })
        assertNull(animeggFileUrl("https://cdn.example/master.m3u8"))
        assertNull(animeggFileUrl("/play/nope/video.mp4"))
    }

    @Test
    fun playableUsesTheFileLabelAndTheEmbedHeaders() {
        val source = animeggPlayable(
            url = "https://www.animegg.org/play/240636/video.mp4?for=101",
            label = "1080p",
            dub = false,
            referer = "https://www.animegg.org/embed/27657",
            userAgent = AnimeggServer.USER_AGENT,
        )
        assertEquals("https://www.animegg.org/play/240636/video.mp4?for=101", source?.url)
        assertEquals("1080p", source?.quality)
        assertEquals("1080p", source?.label)
        assertEquals("SUB", source?.audio)
        assertNull(source?.qualityRating)
        assertEquals("ja", source?.audioLanguage)
        assertEquals(0, source?.stripBytes)
        assertEquals(false, source?.hls)
        assertEquals("https://www.animegg.org/embed/27657", source?.headers?.get("Referer"))
        assertEquals(AnimeggServer.ORIGIN, source?.headers?.get("Origin"))
        val dubbed = animeggPlayable(
            url = "/play/55978/video.mp4",
            label = "HD",
            dub = true,
            referer = "https://www.animegg.org/embed/27658",
            userAgent = AnimeggServer.USER_AGENT,
        )
        assertEquals("https://www.animegg.org/play/55978/video.mp4", dubbed?.url)
        assertNull(dubbed?.quality)
        assertEquals("HD", dubbed?.qualityRating)
        assertEquals("DUB", dubbed?.audio)
        assertEquals("en", dubbed?.audioLanguage)
        assertNull(
            animeggPlayable(
                url = "http://www.animegg.org/play/1/video.mp4",
                label = "360p",
                dub = false,
                referer = "https://www.animegg.org/embed/1",
                userAgent = AnimeggServer.USER_AGENT,
            ),
        )
    }

    @Test
    fun pathsStayOnThePublicSite() {
        assertEquals(
            "https://www.animegg.org/search/?q=One%20Piece",
            animeggSearchUrl("One Piece"),
        )
        assertEquals(
            "https://www.animegg.org/one-piece-episode-3",
            animeggEpisodeUrl("/one-piece-episode-3"),
        )
        assertNull(animeggEpisodeUrl("/one-piece-episode"))
        assertEquals(
            listOf("Frieren: Beyond Journey's End", "Frieren"),
            animeggQueries("Frieren: Beyond Journey's End || mal:21"),
        )
        assertEquals("one-piece", animeggSlug("one-piece"))
        assertNull(animeggSlug("../one-piece"))
    }

    @Test
    fun aChallengePageIsBlockedWithoutTheStatusCode() {
        assertTrue(animeggBlocked(403, ""))
        assertTrue(animeggBlocked(200, "<title>Just a moment...</title> challenge-platform"))
        assertFalse(animeggBlocked(200, "<iframe src=\"/embed/1\">"))
        assertFalse(AnimeggServer.MISSING.contains("404"))
        assertFalse(AnimeggServer.BLOCKED.contains("403"))
    }
}
