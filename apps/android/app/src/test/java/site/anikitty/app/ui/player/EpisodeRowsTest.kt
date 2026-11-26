// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.ui.components.EpisodeSlide

class EpisodeRowsTest {
    private fun season(total: Int): List<EpisodeSlide> =
        (1..total).map { EpisodeSlide(it, "Episode $it", null) }

    @Test
    fun blocksCloseOnTheLastEpisode() {
        assertEquals(emptyList<IntRange>(), episodeBlocks(0))
        assertEquals(listOf(1..100), episodeBlocks(100))
        assertEquals(listOf(1..100, 101..101), episodeBlocks(101))
        assertEquals(listOf(1..100, 101..200, 201..300, 301..366), episodeBlocks(366))
    }

    @Test
    fun blockIndexFollowsTheEpisode() {
        assertEquals(0, blockIndexForEpisode(1))
        assertEquals(0, blockIndexForEpisode(100))
        assertEquals(1, blockIndexForEpisode(101))
        assertEquals(3, blockIndexForEpisode(366))
        assertEquals(0, blockIndexForEpisode(0))
    }

    @Test
    fun anEmptyQueryListsTheSelectedBlock() {
        val slides = season(366)
        val first = episodeRows(slides, "", 0)
        assertEquals(100, first.size)
        assertEquals(1, first.first().number)
        assertEquals(100, first.last().number)

        val last = episodeRows(slides, "", 3)
        assertEquals(66, last.size)
        assertEquals(301, last.first().number)
        assertEquals(366, last.last().number)
    }

    @Test
    fun aSearchLooksAcrossTheWholeSeason() {
        val slides = season(366).map { slide ->
            if (slide.number == 7) slide.copy(title = "Greetings from a Stuffed Lion") else slide
        }
        val hit = episodeRows(slides, "stuffed", 0)
        assertEquals(listOf(7), hit.map { it.number })

        assertEquals(listOf(7), episodeRows(slides, "stuff", 2).map { it.number })
        // A number still finds its episode even when the title does not match.
        assertTrue(episodeRows(slides, "7", 3).any { it.number == 7 })
        assertTrue(episodeRows(slides, "episode 7", 3).any { it.number == 7 })
    }

    @Test
    fun aSearchCapsAtOneBlockAndToleratesSpace() {
        val slides = season(366)
        val rows = episodeRows(slides, "  episode 1 ", 0)
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.size <= EpisodeBlockSize)
        assertEquals(1, rows.first().number)
        assertEquals(emptyList<EpisodeSlide>(), episodeRows(slides, "zzz", 0))
    }
}
