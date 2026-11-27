// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.anilist.AnimeSummary
import java.io.File

class HomeShelfMemoryTest {
    @Test
    fun shelvesSurviveAProcessRestart() {
        val dir = File.createTempFile("shelves", "dir")
        dir.delete()
        dir.mkdirs()
        val show = AnimeSummary(
            id = 42,
            title = "Tab\t\"Name\"",
            coverImageUrl = "https://img/cover",
            bannerImageUrl = "https://img/banner",
            episodes = 12,
            format = "TV",
            averageScore = 86,
            status = "RELEASING",
        )
        HomeShelfMemory.bind(dir)
        HomeShelfMemory.save(listOf(show), emptyList(), listOf(show), emptyList(), now = 5_000L)
        HomeShelfMemory.forgetMemory()
        assertTrue(HomeShelfMemory.trending.isEmpty())
        HomeShelfMemory.hydrate(dir)
        assertEquals("Tab\t\"Name\"", HomeShelfMemory.trending.single().title)
        assertEquals("https://img/banner", HomeShelfMemory.trending.single().bannerImageUrl)
        assertEquals(86, HomeShelfMemory.trending.single().averageScore)
        assertEquals(42, HomeShelfMemory.popular.single().id)
        assertTrue(HomeShelfMemory.fresh(now = 5_000L + 60_000L))
        HomeShelfMemory.forgetMemory()
        dir.deleteRecursively()
    }
}
