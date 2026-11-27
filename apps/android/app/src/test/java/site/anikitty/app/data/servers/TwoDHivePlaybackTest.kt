// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoDHivePlaybackTest {
    @Test
    fun picksMalSearchHitAndBuildsPrimaryHls() {
        val payload = """
            {"results":[
              {"id":21,"title":"One Piece","englishTitle":"One Piece"},
              {"id":813,"title":"Dragon Ball Z","englishTitle":"Dragon Ball Z"}
            ]}
        """.trimIndent()
        val hit = twodhivePick(twodhiveSearchHits(payload), "Dragon Ball Z")
        assertEquals(813, hit?.malId)
        assertEquals(100, hit?.score)

        val cfg = twodhiveParseCfg(
            """
            <script>
            var CFG = {"primary":"https://cdn.babastream.top/","mal":"813","ep":"1","sub":"sub","sid":"abc","pk":"dGVzdA=="};
            </script>
            """.trimIndent(),
        )
        assertEquals(
            "https://cdn.babastream.top/v2/813/1/sub/.m3u8?v=3",
            twodhivePrimaryHls(cfg!!),
        )
        assertEquals(
            listOf("Dragon Ball Z"),
            twodhiveTitles("Dragon Ball Z || mal:813"),
        )
    }

    @Test
    fun neverKeepsMegaPlayAndDedupsMirrors() {
        assertTrue(twodhiveIsMegaPlay("https://megaplay.buzz/stream/mal/813/1/sub"))
        assertFalse(twodhiveIsMegaPlay("https://babastream.top/embed/813/1/sub"))
        val html = """
            <iframe src="https://megaplay.buzz/stream/mal/813/1/sub"></iframe>
            <iframe src="https://babastream.top/embed/813/1/sub"></iframe>
            <iframe src="https://babastream.top/embed/813/1/sub"></iframe>
        """.trimIndent()
        val iframes = twodhiveIframes(html).filterNot { twodhiveIsMegaPlay(it) }
        assertEquals(listOf("https://babastream.top/embed/813/1/sub"), iframes)

        val sources = twodhiveDedup(
            listOf(
                PlayableSource(url = "https://megaplay.buzz/x", label = "Mega"),
                PlayableSource(url = "https://wavy.babastream.top/stream.m3u8", label = "Wavy"),
                PlayableSource(url = "https://wavy.babastream.top/stream.m3u8", label = "Wavy 2"),
            ),
        )
        assertEquals(1, sources.size)
        assertEquals("Wavy", sources[0].label)
    }

    @Test
    fun parsesAstroServersAndPrefetchUrl() {
        val escapedPage =
            """<astro-island component-url="/EpisodePlayer.js" props="{&quot;servers&quot;:[0,{&quot;0&quot;:[0,{&quot;server_name&quot;:[0,&quot;HAdfree&quot;],&quot;slug&quot;:[0,&quot;had-1&quot;],&quot;dub&quot;:[0,false]}],&quot;1&quot;:[0,{&quot;server_name&quot;:[0,&quot;MegaPlay&quot;],&quot;slug&quot;:[0,&quot;https://megaplay.buzz/stream/mal/813/1/sub&quot;],&quot;dub&quot;:[0,false]}]}],&quot;prefetchedHls&quot;:[0,{&quot;sub&quot;:[0,{&quot;url&quot;:[0,&quot;https://cdn.example/sub.m3u8&quot;]}]}]}"></astro-island>"""
        val servers = twodhiveServers(escapedPage)
        assertTrue(servers.any { it.name == "HAdfree" && it.slug == "had-1" })
        assertTrue(servers.any { twodhiveIsMegaPlay(it.slug) })
        assertEquals("https://cdn.example/sub.m3u8", twodhivePrefetchUrl(escapedPage, "sub"))
        assertNull(twodhivePrefetchUrl(escapedPage, "dub"))
        assertTrue(TwoDHiveServer.MISSING.contains("2Dhive"))
        assertTrue(isHardProviderFailure(TwoDHiveServer.BLOCKED))
        assertFalse(isHardProviderFailure(TwoDHiveServer.MISSING))
    }

    @Test
    fun aesGcmRoundTrip() {
        val key = java.util.Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
        val plain = """{"ts":1,"u":"https://cdn.example/a.m3u8","t":"hls"}"""
        val enc = TwoDHiveCrypto.encryptAesGcm(key, plain)
        assertEquals(plain, TwoDHiveCrypto.decryptAesGcm(key, enc))
        val parsed = twodhiveParseResolve(plain)
        assertEquals("hls", parsed?.kind)
        assertEquals("https://cdn.example/a.m3u8", parsed?.url)
        assertFalse(parsed!!.needsVerify)
    }
}
