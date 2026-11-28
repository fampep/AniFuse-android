// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmaniPlaybackTest {

    @Test
    fun identityAndHardFailures() {
        assertEquals("mmani", MmaniServer.ID)
        assertEquals("MMani", MmaniServer(cache = null).server.name)
        assertEquals(MmaniServer.SITE, MmaniServer(cache = null).server.siteUrl)
        assertTrue(MmaniServer.MISSING.contains("MMani"))
        assertTrue(MmaniServer.DUB.contains("subbed"))
        assertTrue(isHardProviderFailure(MmaniServer.BLOCKED))
        assertFalse(isHardProviderFailure(MmaniServer.MISSING))
        assertFalse(isHardProviderFailure(MmaniServer.DUB))
    }

    @Test
    fun kfTokenFromFetchWrapper() {
        val html = """
            <script>
            if (KF.ajax) (function (f, k) { window.fetch = function (u, o) { return f.call(this, u, o); }; })(window.fetch, "20821feefa3cd385c5850cc8");
            </script>
        """.trimIndent()
        assertEquals("20821feefa3cd385c5850cc8", mmaniKfToken(html))
        assertNull(mmaniKfToken("<html></html>"))
    }

    @Test
    fun episodesPickServerAndSlug() {
        val payload = """
            [
              {
                "server_name": "S-sub",
                "id": 10,
                "server_data": [
                  {"name": "01", "slug": "01", "link": "19ebdmonster1"},
                  {"name": "02", "slug": "02", "link": "19ebdmonster2"}
                ]
              }
            ]
        """.trimIndent()
        val servers = mmaniEpisodeServers(payload)
        assertEquals(1, servers.size)
        assertEquals(10, servers[0].id)
        val lane = mmaniPickServer(servers, preferDub = false)!!
        assertEquals("S-sub", lane.name)
        assertEquals("19ebdmonster1", mmaniPickEpisode(lane.episodes, 1)?.link)
        assertEquals("19ebdmonster2", mmaniPickEpisode(lane.episodes, 2)?.link)
        assertNull(mmaniPickEpisode(lane.episodes, 99))
        assertNull(mmaniPickServer(servers, preferDub = true))
    }

    @Test
    fun eplinkAndPlayerConfigResolveAbsoluteHls() {
        val eplink = """
            [{"server":"R-HD","link":"https://play.anien.top/playsub.php?id=mmani6&sub=sub&url=abc"}]
        """.trimIndent()
        val embeds = mmaniEplinkSources(eplink)
        assertEquals(1, embeds.size)
        assertEquals("R-HD", embeds[0].server)

        val html = """
            <script>window.PLAYER_CONFIG = {"videoUrl":"/hls/mmani6/TOKEN/index.m3u8","tracks":[{"label":"English","file":"https://cdn.example/sub.vtt","kind":"captions","default":true}],"title":"anibd"};</script>
        """.trimIndent()
        val config = mmaniPlayerConfig(html)!!
        assertEquals("/hls/mmani6/TOKEN/index.m3u8", config.videoUrl)
        assertEquals(1, config.tracks.size)
        assertEquals(
            "https://play.anien.top/hls/mmani6/TOKEN/index.m3u8",
            mmaniAbsoluteUrl(embeds[0].url, config.videoUrl),
        )
        assertTrue(mmaniLooksLikeHls("#EXTM3U\n#EXTINF:6,\nseg.jpg\n"))
        assertFalse(mmaniLooksLikeHls("{\"error\":true}"))
    }

    @Test
    fun episodeNumberParsesPaddedSlugs() {
        assertEquals(1, mmaniEpisodeNumber("01"))
        assertEquals(12, mmaniEpisodeNumber("12"))
        assertEquals(1, mmaniEpisodeNumber("EP 01"))
        assertNull(mmaniEpisodeNumber(""))
        assertNull(mmaniEpisodeNumber("OVA"))
    }

    @Test
    fun ajaxForbiddenDetectsStaleKfToken() {
        assertTrue(mmaniAjaxForbidden("""{"status":false,"msg":"Forbidden"}"""))
        assertFalse(mmaniAjaxForbidden("""[{"server_name":"S-sub","id":10,"server_data":[]}]"""))
    }
}
