// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test

class AniHostsPlaybackTest {
    @Test
    fun embedUrlUsesTheAnilistId() {
        assertEquals(
            "https://anihosts.xyz/p/home/embed/anilist/16498/1",
            anihostsEmbedUrl(16498, 1),
        )
        assertEquals("", anihostsEmbedUrl(0, 1))
        assertEquals("", anihostsEmbedUrl(16498, 0))
    }

    @Test
    fun stateJsonKeepsStatusAndEpisodes() {
        val ready = anihostsState(
            """<script type="application/json" id="state-data">{"nav":false,"requested":{"type":"anilist","id":"16498"},"episode":1,"episodes":[1,2,3,0],"status":"ready","src":"https://uqload.vc/embed-x.html","source":"uqload"}</script>""",
        )
        assertEquals("ready", ready?.optString("status"))
        assertEquals("https://uqload.vc/embed-x.html", ready?.optString("src"))
        assertEquals(listOf(1, 2, 3), ready?.let { anihostsEpisodeNumbers(it) })
        assertEquals(
            emptyList<Int>(),
            anihostsEpisodeNumbers(JSONObject("""{"episodes":[],"status":"no_episode"}""")),
        )
        assertNull(anihostsState("<html>no state</html>"))
    }

    @Test
    fun packedConfigUnwrapsToTheMaster() {
        val config = """jwplayer("v").setup({sources:[{file:"https://strm.example/hls2/master.m3u8?t=TOKEN&s=EXP"}],image:"https://strm.example/poster.jpg",tracks:[{file:"https://strm.example/vtt/ep_eng.vtt",label:"English",kind:"captions","default":true},{file:"https://strm.example/vtt/ep_sli.vtt",kind:"thumbnails"},{file:"/srt/empty.srt",label:"Upload captions",kind:"captions"}]})"""
        val packed = pack(config)
        val unpacked = anihostsUnpackConfig(packed) ?: error("unpack failed")
        assertEquals(config, unpacked)
        assertEquals(
            "https://strm.example/hls2/master.m3u8?t=TOKEN&s=EXP",
            anihostsFileFromConfig(unpacked),
        )
    }

    @Test
    fun fileAndCaptionsAreScopedToTheirArrays() {
        val config = """jwplayer("v").setup({sources:[{file:"https://strm.example/hls2/master.m3u8"}],image:"https://strm.example/poster.jpg",tracks:[{file:"https://strm.example/vtt/ep_eng.vtt",label:"English",kind:"captions"},{file:"https://strm.example/vtt/ep_sli.vtt",kind:"thumbnails"},{file:"/srt/empty.srt",label:"Upload captions",kind:"captions"}]})"""
        assertEquals("https://strm.example/hls2/master.m3u8", anihostsFileFromConfig(config))
        val captions = anihostsCaptions(config)
        assertEquals(1, captions.size)
        assertEquals("https://strm.example/vtt/ep_eng.vtt", captions[0].url)
        assertEquals("en", captions[0].language)
        assertEquals("text/vtt", captions[0].mimeType)
    }

    @Test
    fun mediaUrlStaysHttpsAndPlayable() {
        assertEquals(
            "https://strm.example/hls2/master.m3u8?t=1&s=2",
            anihostsMediaUrl("https://strm.example/hls2/master.m3u8?t=1&s=2"),
        )
        assertEquals("https://strm.example/file.mp4", anihostsMediaUrl("https://strm.example/file.mp4"))
        assertNull(anihostsMediaUrl("http://strm.example/master.m3u8"))
        assertNull(anihostsMediaUrl("https://user:pass@strm.example/master.m3u8"))
        assertNull(anihostsMediaUrl("https://strm.example/../master.m3u8"))
        assertNull(anihostsMediaUrl("https://strm.example/master.m3u8\\x"))
        assertNull(anihostsMediaUrl("/relative/master.m3u8"))
    }

    @Test
    fun playableCarriesTheEmbedHeaders() {
        val source = anihostsPlayable(
            url = "https://strm9.example/hls2/master.m3u8?t=1",
            referer = "https://uqload.vc/embed-x.html",
            captions = emptyList(),
            dub = false,
            userAgent = AniHostsServer.USER_AGENT,
        )
        assertEquals("https://uqload.vc/embed-x.html", source?.headers?.get("Referer"))
        assertEquals("https://uqload.vc", source?.headers?.get("Origin"))
        assertEquals("SUB", source?.label)
        assertEquals("ja", source?.audioLanguage)
        assertEquals(true, source?.hls)
        assertEquals(
            "DUB",
            anihostsPlayable(
                url = "https://strm9.example/hls2/master.m3u8",
                referer = "https://uqload.vc/embed-x.html",
                captions = emptyList(),
                dub = true,
                userAgent = AniHostsServer.USER_AGENT,
            )?.label,
        )
        assertNull(
            anihostsPlayable(
                url = "https://strm9.example/master.m3u8",
                referer = "http://uqload.vc/embed-x.html",
                captions = emptyList(),
                dub = false,
                userAgent = AniHostsServer.USER_AGENT,
            ),
        )
    }

    @Test
    fun challengePageIsAMiss() {
        assertTrue(anihostsChallenge(403, ""))
        assertTrue(anihostsChallenge(200, "<title>Just a moment...</title> challenge-platform"))
        assertFalse(anihostsChallenge(404, """{"status":"no_episode"}"""))
        assertFalse(AniHostsServer.MISSING.contains("403"))
        assertFalse(AniHostsServer.BLOCKED.contains("404"))
    }

    /**
     * Packs a config the way the host page does: every dictionary word is
     * swapped for its base-36 index, longest first, then wrapped in the eval
     * blob. Index 0 stays the empty string the real pages use.
     */
    private fun pack(config: String): String {
        val words = listOf(
            "",
            "file",
            "sources",
            "tracks",
            "kind",
            "label",
            "captions",
            "thumbnails",
            "English",
            "Upload captions",
            "image",
            "default",
            "setup",
            "jwplayer",
            "https://strm.example/hls2/master.m3u8?t=TOKEN&s=EXP",
            "https://strm.example/hls2/master.m3u8",
            "https://strm.example/poster.jpg",
            "https://strm.example/vtt/ep_eng.vtt",
            "https://strm.example/vtt/ep_sli.vtt",
            "/srt/empty.srt",
        )
        var payload = config
        val order = (1 until words.size).sortedByDescending { words[it].length }
        for (index in order) {
            payload = payload.replace(words[index], index.toString(36))
        }
        return "eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\\\b'+c.toString(a)+'\\\\b','g'),k[c]);return p}('$payload',36,${words.size},'${words.joinToString("|")}'.split('|'),0,{}))"
    }
}
