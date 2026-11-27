// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniVaultPlaybackTest {
    @Test
    fun subButtonsFollowTheSiteOrder() {
        val buttons = anivaultButtons("sub")
        assertEquals("Hd-1", buttons.first().server)
        assertEquals("anikoto_stream.php", buttons.first().endpoint)
        assertEquals("AnimeHeaven", buttons.first { it.server == null }.label)
        assertEquals("WatchAnimeWorld", buttons.last().label)
        assertEquals(
            listOf("Hd-1", "Vidstream-2", "Hd-2", "Vidplay", "BYFMS", "AnimeHeaven"),
            buttons.take(6).map { it.label },
        )
        assertTrue(AniVaultServer.BUTTON_TIMEOUT_MS < RESOLVE_TIMEOUT_MS)
    }

    @Test
    fun dubKeepsEveryEnglishServerThenTheirHindiServers() {
        val buttons = anivaultButtons("dub")
        assertTrue(buttons.none { it.endpoint == "animeheaven_stream.php" })
        assertEquals("English", buttons.first { it.endpoint == "anizone_stream.php" }.server)
        assertEquals("PlayerXdub", buttons.last().server)
        assertTrue(buttons.any { it.server == "Rubydub" })
        assertTrue(buttons.any { it.endpoint == "watchanimeworld_stream.php" })
    }

    @Test
    fun selectingAniVaultStaysOnAniVaultServers() {
        val all = listOf(stub("anikoto"), stub("anivault"), stub("reanime"))
        assertEquals(listOf("anivault"), playbackResolvers("anivault", all).map { it.server.id })
        assertEquals(listOf("anikoto", "reanime"), playbackResolvers("anikoto", all).map { it.server.id })
    }

    @Test
    fun streamUrlCarriesTheHoshikaIdAndServer() {
        val url = anivaultStreamUrl(
            origin = "https://www.anivault.co",
            button = anivaultButtons("sub").first(),
            shortId = "ab12",
            episode = 3,
        )
        assertEquals(
            "https://www.anivault.co/api/anikoto_stream.php?ep=3&audio=sub&hoshikaId=ab12&server=Hd-1",
            url,
        )
    }

    @Test
    fun titlesDropTheMalMarker() {
        assertEquals(
            listOf("Sousou no Frieren", "Frieren"),
            anivaultTitles("Sousou no Frieren || Frieren || mal:52991"),
        )
    }

    @Test
    fun aVerifiedMapWinsOverATitleGuess() {
        val body = """{"ok":true,"mapped":true,"data":{"shortId":"fr13","title":"Frieren"}}"""
        assertEquals("fr13", anivaultMappedShortId(body))
        assertNull(anivaultMappedShortId("""{"ok":false,"mapped":false}"""))
    }

    @Test
    fun searchKeepsOnlyAnExactTitle() {
        val body = """
            {"ok":true,"data":[
              {"shortId":"other","title":"Frieren Beyond"},
              {"shortId":"fr13","titleEnglish":"Frieren","titleRomaji":"Sousou no Frieren"}
            ]}
        """.trimIndent()
        assertEquals("fr13", anivaultSearchShortId(body, listOf("Frieren")))
        assertNull(anivaultSearchShortId(body, listOf("Beyond")))
    }

    @Test
    fun aPlaylistBecomesTheFileAndAnEmbedIsSkipped() {
        val headers = mapOf("Referer" to "https://www.anivault.co/")
        val playable = anivaultPlayable(
            """{"m3u8":"https://cdn.example/a.m3u8","server":"Hd-1"}""",
            "Hd-1",
            headers,
        )
        assertEquals("https://cdn.example/a.m3u8", playable?.url)
        assertEquals(true, playable?.hls)
        assertEquals("https://www.anivault.co/", playable?.headers?.get("Referer"))
        assertNull(anivaultPlayable("""{"iframeOnly":true,"embedUrl":"https://embed.example/x"}""", "Hd-1", headers))
        assertNull(anivaultPlayable("""{"error":"Could not resolve anime"}""", "Hd-1", headers))
    }

    @Test
    fun everyListedServerNameIsKept() {
        val names = anivaultListedServers(
            """{"server":"Hd-1","servers":["Vidstream-2",{"name":"Hd-1"},{"server":"Mega"}]}""",
        )
        assertEquals(listOf("Vidstream-2", "Hd-1", "Mega"), names)
    }

    @Test
    fun filesStayInButtonOrderWithoutRepeatingAUrl() {
        val first = PlayableSource(url = "https://cdn.example/a.m3u8", label = "Hd-1")
        val again = PlayableSource(url = "https://cdn.example/a.m3u8", label = "Hd-1 again")
        val second = PlayableSource(url = "https://cdn.example/b.m3u8", label = "Hd-2")
        assertEquals(
            listOf("Hd-1", "Hd-2"),
            anivaultOrdered(listOf(4 to second, 0 to first, 1 to again)).map { it.label },
        )
    }

    private fun stub(id: String) = object : StreamResolver {
        override val server = StreamServer(id = id, name = id, description = "")
        override suspend fun resolve(
            anilistId: Int,
            episode: Int,
            audio: String,
            titleHint: String?,
        ): List<PlayableSource> = emptyList()
    }
}
