// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniRexPlaybackTest {
    @Test
    fun watchApiListsFlixCloudEmbeds() {
        val players = anirexPlayers(
            """
            {"anime_id":210482,"episode":1,"audio":"dual","servers":[
              {"id":1,"player_url":"https://flixcloud.cc/e/vievo22zsv4w?v=2","audio":"dual"}
            ],"duration_seconds":1516}
            """.trimIndent(),
        )
        assertEquals(1, players.size)
        assertEquals("https://flixcloud.cc/e/vievo22zsv4w?v=2", players.single().url)
        assertEquals("dual", players.single().audio)
        assertEquals(
            listOf("https://flixcloud.cc/e/vievo22zsv4w?v=2"),
            anirexPlayerUrls(
                """{"servers":[{"player_url":"https://flixcloud.cc/e/vievo22zsv4w?v=2"}]}""",
            ),
        )
        assertTrue(anirexPlayerUrls("""{"servers":[]}""").isEmpty())
        assertTrue(anirexPlayerUrls("not-json").isEmpty())
        assertTrue(AniRexServer.MISSING.contains("AniRex"))
        assertTrue(AniRexServer.MISSING.contains("does not have"))
        assertTrue(AniRexServer.BLOCKED.contains("could not be reached"))
        assertEquals(AniRexServer.BLOCKED, anirexHttpFailure(403, "Just a moment..."))
        assertEquals(AniRexServer.MISSING, anirexHttpFailure(404, """{"message":"No stream available for this episode"}"""))
        assertEquals(AniRexServer.BLOCKED, anirexHttpFailure(0, ""))
        assertTrue(anirexHttpFailure(500, "<html>Server Error</html>").contains("HTTP 500"))
    }
}
