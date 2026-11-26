// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import site.anikitty.app.data.airingLabel
import site.anikitty.app.data.anilist.AnimeSummary
import site.anikitty.app.data.episodeCountLabel
import site.anikitty.app.data.friendlyFailure
import site.anikitty.app.data.mediaFormatLabel
import site.anikitty.app.data.plainSynopsis

class AppCopyTest {
    @Test
    fun synopsisDropsSpoilersAndEntities() {
        val raw = "A knight&#39;s tale.&nbsp;~!The king dies.!~ Source: AniList"
        val plain = plainSynopsis(raw)
        assertEquals("A knight's tale.", plain)
        assertFalse(plain.contains("~!"))
        assertFalse(plain.contains("dies"))
        assertEquals(
            "She doesn\u2019t \u2014 wait.",
            plainSynopsis("She doesn&#8217;t &#8212; wait."),
        )
        assertEquals("A", plainSynopsis("&#x41;"))
        assertEquals("\uD83D\uDE00", plainSynopsis("&#128512;"))
        assertEquals("ok", plainSynopsis("ok&#9999999;"))
    }

    @Test
    fun aLongProviderDumpBecomesOneSentence() {
        val dump = "All installed providers failed — " + "x".repeat(200)
        assertEquals(
            "None of the sources have this episode. Try again in a moment.",
            friendlyFailure(dump),
        )
        val short = "All installed providers failed — reanime: This source is blocked by the site. Try another source."
        assertEquals(short, friendlyFailure(short))
    }

    @Test
    fun airingWithinSixHoursCountsDown() {
        val now = 1_700_000_000L
        assertEquals("in 2h 15m", airingLabel(now + 2 * 3600 + 15 * 60, now, "19:15"))
        assertEquals("in 2h", airingLabel(now + 2 * 3600, now, "19:00"))
        assertEquals("in 40 min", airingLabel(now + 40 * 60, now, "18:40"))
        assertEquals("19:00", airingLabel(now - 21 * 60, now, "19:00"))
        assertEquals("Now", airingLabel(now, now, "18:00"))
    }

    @Test
    fun formatsAndEpisodeCountsReadAsWords() {
        assertEquals("TV Short", mediaFormatLabel("TV_SHORT"))
        assertEquals("Movie", mediaFormatLabel("movie"))
        assertEquals("Music", mediaFormatLabel("MUSIC"))
        assertEquals(null, mediaFormatLabel("  "))
        assertEquals("1 ep", episodeCountLabel(1))
        assertEquals("12 eps", episodeCountLabel(12))
        val anime = AnimeSummary(
            id = 1,
            title = "Short",
            coverImageUrl = null,
            episodes = 1,
            format = "TV_SHORT",
            averageScore = 80,
            seasonYear = 2024,
        )
        assertEquals("TV Short  ·  2024  ·  1 ep  ·  8.0", homeRailMeta(anime))
    }

    @Test
    fun talkBackLabelsStayOnePhrase() {
        assertEquals(
            "Frieren. Episode 4. Sub. 2h ago",
            latestEpisodeDescription("Frieren", 4, dub = false, ago = "2h ago"),
        )
        assertEquals(
            "Frieren. Episode 4. Dub",
            latestEpisodeDescription("Frieren", 4, dub = true, ago = ""),
        )
        assertEquals(
            "Frieren. 2023. TV. Releasing. In your list",
            searchResultDescription("Frieren", "2023", "TV", releasing = true, badge = "In your list"),
        )
        assertEquals("Frieren", searchResultDescription("Frieren", null, null, releasing = false, badge = null))
        assertEquals(
            "Frieren. Episode 4 · 12 min left. 2h ago",
            continueCardDescription("Frieren", "Episode 4 · 12 min left", "2h ago"),
        )
        assertEquals("Anikoto, bundled, selected", sourceRowDescription("Anikoto", extension = false, selected = true))
        assertEquals("Extra, extension", sourceRowDescription("Extra", extension = true, selected = false))
    }
}
