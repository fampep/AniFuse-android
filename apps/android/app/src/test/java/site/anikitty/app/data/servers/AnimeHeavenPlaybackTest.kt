// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeHeavenPlaybackTest {
    @Test
    fun prefersTheExactTitleAndFindsEpisodeOne() {
        val search = """
            <a class='ac' href='/anime.php?hgj3i'><div class='fastname'>Frieren: Beyond Journey&#039;s End Season 2</div></a>
            <a class='ac' href='/anime.php?ak2gr'><div class='fastname'>Frieren: Beyond Journey&#039;s End</div></a>
        """.trimIndent()
        val pick = animeHeavenPick(animeHeavenSearch(search), "Frieren: Beyond Journey's End")
        assertEquals("ak2gr", pick?.id)
        assertEquals(100, pick?.score)
        val page = """
            <a class='c' onclick='gatea( "cd9a4996ea3e27887d22a26e6ce3113f")' href='gate.php'>
            <div class='watch2 bc'>1</div></a>
            <a class='c' onclick='gatea( "b9e57993a80d8ed82019db23e8e7764c")' href='gate.php'>
            <div class='watch2 bc'>2</div></a>
        """.trimIndent()
        assertEquals("cd9a4996ea3e27887d22a26e6ce3113f", animeHeavenEpisodeKey(page, 1))
        val gate = """
            <source src='https://ax.animeheaven.me/video.mp4?token' type='video/mp4'>
            <source src='https://ct.animeheaven.me/video.mp4?token&error' type='video/mp4'>
            <source src='https://cz.animeheaven.me/video.mp4?token' type='video/mp4'>
        """.trimIndent()
        assertEquals(
            listOf(
                "https://ax.animeheaven.me/video.mp4?token",
                "https://cz.animeheaven.me/video.mp4?token",
            ),
            animeHeavenMp4s(gate),
        )
        assertEquals(
            listOf("Frieren", "Sousou no Frieren"),
            animeHeavenTitles("Frieren || Sousou no Frieren || mal:52991"),
        )
        assertTrue(AnimeHeavenServer.MISSING.contains("AnimeHeaven"))
        assertTrue(AnimeHeavenServer.DUB.contains("subbed"))
        assertEquals(
            "${AnimeHeavenServer.SITE}/fastsearch.php?xhr=1&s=Frieren",
            animeHeavenSearchUrl("Frieren"),
        )
    }
}
