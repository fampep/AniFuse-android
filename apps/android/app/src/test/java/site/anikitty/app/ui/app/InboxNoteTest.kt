// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import org.junit.Assert.assertEquals
import org.junit.Test
import site.anikitty.app.data.download.EpisodeDownloadEntity

class InboxNoteTest {
    @Test
    fun downloadNoteIdIgnoresProgressTicks() {
        val first = downloadInboxNotes(listOf(row(updatedAt = 10L, percent = 20)))
        val later = downloadInboxNotes(listOf(row(updatedAt = 99L, percent = 80)))
        assertEquals(first.single().id, later.single().id)
        assertEquals("dl-7-3", first.single().id)
    }

    private fun row(updatedAt: Long, percent: Int) = EpisodeDownloadEntity(
        anilistId = 7,
        episode = 3,
        title = "Show",
        sourceKey = "source",
        url = "https://cdn.example/a",
        headersJson = "{}",
        hls = false,
        stripBytes = 0,
        phase = "RUNNING",
        progressPercent = percent,
        localPath = null,
        error = null,
        updatedAtEpochMs = updatedAt,
    )
}
