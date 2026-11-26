// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniPmPlaybackTest {
    @Test
    fun liveHostIsAnipmPkAndLabelIsAniPm() {
        assertEquals("anipm", AniPmServer.ID)
        assertEquals("https://anipm.pk", AniPmServer.ORIGIN)
        assertEquals("anipm", AniPmServer.OWN_SESSION)
        assertEquals("ani.pm", AniPmServer(cache = null).server.name)
        assertEquals("https://anipm.pk", AniPmServer(cache = null).server.siteUrl)
        assertFalse(AniPmServer.MISSING.contains("404"))
        assertFalse(AniPmServer.BLOCKED.contains("403"))
    }

    @Test
    fun searchPayloadPicksAnilistMatch() {
        val payload = """
            {"items":[
              {"id":8384,"source":"settlar","title":"Frieren Season 2","anilistId":"182255"},
              {"id":6351,"source":"settlar","title":"Frieren: Beyond Journey’s End","anilistId":"154587"}
            ]}
        """.trimIndent()
        val hit = aninzPickShow(payload, 154587, "Frieren")
        assertEquals("6351", hit?.id)
        assertEquals("settlar", hit?.source)
    }

    @Test
    fun sessionEmbedUsesSettlarHost() {
        val embed = anipmEmbedUrl(
            """{"embedUrl":"https://embed.settlar.io/embed/v1?t=abc%2Bdef&x=1","provider":"anipm"}""",
        )
        assertEquals("https://embed.settlar.io/embed/v1?t=abc%2Bdef&x=1", embed)
        assertEquals("abc+def", anipmEmbedToken(embed!!))
        assertTrue(aninzOwnMedia("https://media.settlar.io/v1/object/master.m3u8"))
        assertNotNull(embed)
    }

    @Test
    fun onlyOneAniPmProviderId() {
        assertEquals("anipm", AniPmServer.ID)
        assertEquals("ani.pm", AniPmServer(cache = null).server.name)
    }
}
