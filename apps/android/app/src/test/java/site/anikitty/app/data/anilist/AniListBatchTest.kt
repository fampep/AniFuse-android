// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.anilist

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListPacerTest {
    @Test
    fun startsStay700msApartWhileTheCallItselfIsNotInsideTheLock() = runBlocking {
        var now = 0L
        val pacer = AniListPacer(
            clock = { now },
            sleep = { wait -> now += wait },
        )
        val starts = mutableListOf<Long>()
        pacer.run(background = false) { starts += now }
        pacer.run(background = false) { starts += now }
        assertEquals(listOf(0L, 700L), starts)
    }

    @Test
    fun aPenaltyPushesTheNextStart() = runBlocking {
        var now = 0L
        val pacer = AniListPacer(
            clock = { now },
            sleep = { wait -> now += wait },
        )
        pacer.run(background = false) { }
        pacer.penalize(5_000L)
        var started = -1L
        pacer.run(background = false) { started = now }
        assertEquals(5_000L, started)
    }
}

class AniListBatchTest {
    @Test
    fun titlesChunkBySixAndAShortListStaysOneQuery() {
        assertEquals(0, chunkedList(emptyList(), ANILIST_SEARCH_CHUNK).size)
        assertEquals(1, chunkedList(listOf("a"), ANILIST_SEARCH_CHUNK).size)
        assertEquals(listOf(6, 1), chunkedList((1..7).map { "t$it" }, ANILIST_SEARCH_CHUNK).map { it.size })
    }

    @Test
    fun oneAliasErrorDoesNotDropTheAliasThatReturnedMedia() {
        val titles = listOf("Frieren", "Missing")
        val body = """
            {
              "data": {
                "s0": { "media": [ { "id": 1 } ] },
                "s1": null
              },
              "errors": [ { "message": "bad" } ]
            }
        """.trimIndent()
        val parsed = parseSearchAliases(body, titles)
        assertEquals(1, parsed.hits.getValue("Frieren").length())
        assertEquals(listOf("Missing"), parsed.missing)
        val (query, variables) = searchManyQuery(titles)
        assertTrue(query.contains("s0:"))
        assertTrue(query.contains("s1:"))
        assertEquals("Frieren", variables.getString("s0"))
    }

    @Test
    fun briefCountsUseTheNextAiringEpisodeWhenTheTotalIsStillNull() {
        val body = """
            { "data": { "Page": { "media": [
              { "id": 7, "episodes": null, "nextAiringEpisode": { "episode": 12 } },
              { "id": 8, "episodes": 24 }
            ] } } }
        """.trimIndent()
        assertEquals(mapOf(7 to 11, 8 to 24), parseBriefCounts(body))
    }

    @Test
    fun publicCacheDropsListStatus() {
        val body = """{"data":{"Media":{"id":1,"isFavourite":true,"mediaListEntry":{"status":"CURRENT"}}}}"""
        val stripped = publicCatalogBody(body)
        assertTrue(!stripped.contains("mediaListEntry"))
        assertTrue(!stripped.contains("isFavourite"))
        assertTrue(stripped.contains("\"id\":1"))
    }
}
