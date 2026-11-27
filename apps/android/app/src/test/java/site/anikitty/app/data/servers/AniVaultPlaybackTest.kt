// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniVaultPlaybackTest {
    @Test
    fun animeHeavenPrefersTheExactTitleAndFindsEpisodeOne() {
        val search = """
            <a class='ac' href='/anime.php?hgj3i'><div class='fastname'>Frieren: Beyond Journey&#039;s End Season 2</div></a>
            <a class='ac' href='/anime.php?ak2gr'><div class='fastname'>Frieren: Beyond Journey&#039;s End</div></a>
        """.trimIndent()
        val pick = anivaultHeavenPick(anivaultHeavenSearch(search), "Frieren: Beyond Journey's End")
        assertEquals("ak2gr", pick?.id)
        assertEquals(100, pick?.score)
        val page = """
            <a class='c' onclick='gatea( "cd9a4996ea3e27887d22a26e6ce3113f")' href='gate.php'>
            <div class='watch2 bc'>1</div></a>
            <a class='c' onclick='gatea( "b9e57993a80d8ed82019db23e8e7764c")' href='gate.php'>
            <div class='watch2 bc'>2</div></a>
        """.trimIndent()
        assertEquals("cd9a4996ea3e27887d22a26e6ce3113f", anivaultHeavenEpisodeKey(page, 1))
        val gate = "<source src='https://ax.animeheaven.me/video.mp4?token' type='video/mp4'>"
        assertEquals("https://ax.animeheaven.me/video.mp4?token", anivaultHeavenMp4s(gate).single())
    }

    @Test
    fun aWarmAsksOnlyTheServerThatPlayed() {
        val buttons = anivaultButtons("sub")
        val lead = anivaultAskKey(buttons.first { it.server == "Hd-2" })
        val only = anivaultLeadFirst(buttons, lead).filter { anivaultAskKey(it) == lead }
        assertEquals(listOf("Hd-2"), only.map { it.server })
        assertTrue(AniVaultServer.RESOLVE_BUDGET_MS > RESOLVE_TIMEOUT_MS)
        assertTrue(AniVaultServer.LOOKUP_TIMEOUT_MS < AniVaultServer.STREAM_TIMEOUT_MS)
    }

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
    fun dubButtonsMapOntoOnDeviceOriginPeers() {
        assertEquals("anikoto", anivaultDevicePeerId("anikoto_stream.php"))
        assertEquals("reanime", anivaultDevicePeerId("reanime_stream.php"))
        assertEquals("aniwaves", anivaultDevicePeerId("aniwaves_stream.php"))
        assertEquals("anizone", anivaultDevicePeerId("anizone_stream.php"))
        assertNull(anivaultDevicePeerId("animeheaven_stream.php"))
        assertNull(anivaultDevicePeerId("desidub_stream.php"))
        assertNull(anivaultDevicePeerId("animenosub_stream.php"))
        val dub = anivaultButtons("dub")
        val jobs = anivaultDeviceJobs(dub, lead = null, warm = false)
        assertEquals(listOf("anikoto", "reanime", "aniwaves", "anizone"), jobs.map { it.first })
        assertEquals("Hd-1", jobs.first().third.label)
        assertEquals("English (Zone)", jobs.last().third.label)
        val hd1 = anivaultAskKey(dub.first { it.label == "Hd-1" })
        val warm = anivaultDeviceJobs(dub, lead = hd1, warm = true)
        assertEquals(listOf("anikoto"), warm.map { it.first })
        assertTrue(anivaultDeviceJobs(dub, lead = null, warm = true).isEmpty())
        val stamped = anivaultDeviceSource(
            PlayableSource(url = "https://cdn.example/d.m3u8", label = "Mega", hls = true),
            dub.first { it.label == "Hd-1" },
        )
        assertEquals("Hd-1", stamped.label)
        assertEquals(hd1, stamped.leadKey)
        assertEquals("en", stamped.audioLanguage)
        assertEquals(
            listOf(1 to dub.first { it.label == "Vidstream-2" }),
            anivaultDeviceExtras(dub, "anikoto", skipIndex = 0),
        )
        val vid = dub.first { it.label == "Vidstream-2" }
        assertEquals(1, anivaultDeviceSlot(dub, "anikoto", vid))
        assertEquals(0, anivaultDeviceSlot(dub, "anikoto", dub.first { it.label == "Hd-1" }))
        val peerFiles = listOf(
            PlayableSource(url = "https://cdn.example/hd1.m3u8", label = "A"),
            PlayableSource(url = "https://cdn.example/vid2.m3u8", label = "B"),
        )
        assertEquals("https://cdn.example/vid2.m3u8", anivaultDevicePick(peerFiles, 1)?.url)
        assertEquals("https://cdn.example/hd1.m3u8", anivaultDevicePick(peerFiles, 9)?.url)
        assertTrue(AniVaultServer.PEER_TIMEOUT_MS < AniVaultServer.RESOLVE_BUDGET_MS)
        assertTrue(AniVaultServer.FAILOVER_GRACE_MS < AniVaultServer.PEER_TIMEOUT_MS)
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
    fun theServerThatPlayedStartsTheNextEpisode() {
        val dir = java.nio.file.Files.createTempDirectory("anivault-lead").toFile()
        AniVaultLead.attach(dir)
        AniVaultLead.reset()
        val hd2 = anivaultButtons("sub").first { it.server == "Hd-2" }
        val key = anivaultAskKey(hd2)
        AniVaultLead.note(21, key)
        assertEquals("sub", anivaultLeadMode(key))
        val next = anivaultLeadFirst(anivaultButtons("sub"), AniVaultLead.preferred(21, "sub"))
        assertEquals("Hd-2", next.first().server)
        assertEquals("Hd-1", next[1].server)
        assertEquals(anivaultButtons("sub").size, next.size)
        assertEquals(
            "Hd-1",
            anivaultLeadFirst(anivaultButtons("dub"), AniVaultLead.preferred(21, "sub")).first().server,
        )
        assertNull(AniVaultLead.preferred(21, "dub"))
        AniVaultLead.attach(dir)
        assertEquals(key, AniVaultLead.preferred(21, "sub"))
        val files = listOf(
            PlayableSource(url = "https://cdn.example/a.m3u8", label = "Hd-1", leadKey = anivaultAskKey(anivaultButtons("sub").first())),
            PlayableSource(url = "https://cdn.example/b.m3u8", label = "Hd-2", leadKey = key),
        )
        assertEquals("Hd-2", anivaultLeadSources(files, key).first().label)
        AniVaultLead.reset()
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
