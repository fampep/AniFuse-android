// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SubtitleLibraryTest {
    @Test
    fun libraryTracksReplaceScrapedCaptionsAndStayOnTheLibrary() {
        val key = "a".repeat(64)
        val parsed = subtitleTracksFromLibrary(
            """
            {"tracks":[
              {"language":"English","flag":"🇬🇧","site":"AniGato","sdh":false,"download":"/api/file?id=$key"},
              {"language":"English","flag":"🇬🇧","site":"Anikoto","sdh":false,"download":"/api/file?id=${"b".repeat(64)}"},
              {"language":"Albanian","flag":"🇦🇱","site":"AniGato","download":"https://evil.example/a.srt"},
              {"language":"Subtitle","download":"/api/file?id=${"c".repeat(64)}"}
            ]}
            """.trimIndent(),
        )
        assertEquals(3, parsed.size)
        assertEquals("http://143.246.203.212/api/file?id=$key", parsed[0].url)
        assertEquals("application/x-subrip", parsed[0].mimeType)
        assertEquals("en", parsed[0].language)
        assertEquals("🇬🇧 English · AniGato", parsed[0].label)
        assertEquals("🇬🇧 English · Anikoto", parsed[1].label)
        assertEquals("en", parsed[1].language)
        assertEquals("und", parsed[2].language)
        val scraped = PlayableSource(
            url = "https://cdn.example/master.m3u8",
            subtitles = listOf(SubtitleTrack(url = "https://cdn.example/en.vtt", label = "English")),
        )
        val replaced = withLibrarySubtitles(listOf(scraped), parsed)
        assertEquals(parsed, replaced.single().subtitles)
        assertTrue(replaced.single().subtitles.none { it.url.contains("cdn.example") })
        val started = withLibrarySubtitles(listOf(scraped), emptyList())
        assertTrue(started.single().subtitles.isEmpty())
    }

    @Test
    fun aCancelledCaptionLoadAbortsAndAFailureDoesNot() {
        assertThrows(CancellationException::class.java) {
            valueOrEmptyUnlessCancelled<List<SubtitleTrack>>(Result.failure(CancellationException("stop")))
        }
        assertNull(valueOrEmptyUnlessCancelled<List<SubtitleTrack>>(Result.failure(IOException("down"))))
    }
}
