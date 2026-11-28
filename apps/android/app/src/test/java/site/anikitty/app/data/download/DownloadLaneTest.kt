// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.anikitty.app.data.servers.PlayableSource

class DownloadLaneTest {
    private fun row(id: Int, episode: Int, host: String, at: Long) = LaneCandidate(
        anilistId = id,
        episode = episode,
        url = "https://$host/ep.m3u8",
        updatedAtEpochMs = at,
    )

    @Test
    fun theLaneSkipsTheCdnThatIsPlaying() {
        val rows = listOf(
            row(1, 1, "cdn-a.example", 1L),
            row(1, 2, "cdn-b.example", 2L),
            row(2, 1, "cdn-a.example", 3L),
        )
        val pick = nextLaneDownload(rows, playingHost = "cdn-a.example")
        assertEquals(LaneChoice.Run(1, 2), pick)
        assertEquals(LaneChoice.Wait, nextLaneDownload(listOf(rows[0]), "CDN-A.example"))
        assertEquals(LaneChoice.Idle, nextLaneDownload(emptyList(), "cdn-a.example"))
        assertEquals(LaneChoice.Run(1, 1), nextLaneDownload(rows, playingHost = null))
    }

    @Test
    fun aDeferredRowWaitsAndDoesNotBlockAFreeHost() {
        val rows = listOf(row(1, 1, "cdn-b.example", 1L), row(1, 2, "cdn-c.example", 2L))
        assertEquals(
            LaneChoice.Run(1, 2),
            nextLaneDownload(rows, playingHost = null, deferred = setOf(1 to 1)),
        )
        assertEquals(
            LaneChoice.Wait,
            nextLaneDownload(rows, playingHost = null, deferred = setOf(1 to 1, 1 to 2)),
        )
    }

    @Test
    fun aRefusalSwapsHostAndKeepsAPartialOnlyOnTheSameHost() {
        assertEquals(DownloadTransfer.Resume, downloadTransfer(206, partBytes = 40L))
        assertEquals(DownloadTransfer.Replace, downloadTransfer(200, partBytes = 40L))
        assertEquals(DownloadTransfer.Restart, downloadTransfer(416, partBytes = 40L))
        assertEquals(DownloadTransfer.Swap, downloadTransfer(403, partBytes = 40L))
        assertEquals(DownloadTransfer.Swap, downloadTransfer(503, partBytes = 0L))
        assertEquals(DownloadTransfer.Fail, downloadTransfer(302, partBytes = 0L))
        assertTrue(keepPartialForNextUrl("https://cdn-a.example/a.mp4", "https://cdn-a.example/a.mp4?sig=2"))
        assertFalse(keepPartialForNextUrl("https://cdn-a.example/a.mp4", "https://cdn-b.example/a.mp4"))
    }

    @Test
    fun theShadowHostIsInheritedBeforeAColdResolve() {
        val current = "https://cdn-a.example/play.m3u8"
        val shadow = PlayableSource(url = "https://cdn-b.example/warm.m3u8")
        val resolved = PlayableSource(url = "https://cdn-c.example/fresh.m3u8")
        assertEquals(shadow.url, inheritDownloadSource(current, listOf(shadow), listOf(resolved))?.url)
        assertEquals(resolved.url, inheritDownloadSource(current, emptyList(), listOf(resolved))?.url)
        assertNull(
            inheritDownloadSource(
                current,
                listOf(PlayableSource(url = current)),
                listOf(PlayableSource(url = "https://user@cdn-a.example/other.m3u8")),
            ),
        )
    }

    @Test
    fun theShelfSaysWaitingWhilePlaybackOwnsTheHost() {
        val line = downloadShelfSubtitle(
            episode = 4,
            phase = "QUEUED",
            percent = 0,
            error = null,
            url = "https://cdn-a.example/a.mp4",
            playingHost = "cdn-a.example",
            ahead = 0,
        )
        assertEquals("EP 4 · Waiting", line)
        assertEquals(
            "EP 4 · In line",
            downloadShelfSubtitle(4, "QUEUED", 0, null, "https://cdn-b.example/a.mp4", "cdn-a.example", ahead = 2),
        )
        assertEquals(
            "EP 4 · Saving 40%",
            downloadShelfSubtitle(4, "RUNNING", 40, null, "https://cdn-b.example/a.mp4", null, ahead = 0),
        )
        val waiting = downloadStatus(
            PlayableSource(url = "https://cdn-a.example/a.mp4"),
            saved = null,
        )
        assertEquals("Download", downloadButtonLabel(waiting))
    }

    @Test
    fun queuedCopyNamesTheWaitOnTheButton() {
        val remote = PlayableSource(url = "https://cdn-a.example/a.mp4")
        val queued = SavedEpisode(
            anilistId = 1,
            episode = 1,
            sourceKey = sourceKey(remote),
            url = remote.url,
            headers = emptyMap(),
            hls = false,
            phase = DownloadPhase.QUEUED,
            progressPercent = 0,
            localPath = null,
            error = null,
        )
        val status = downloadStatus(remote, queued, playingHost = "cdn-a.example")
        assertEquals("Waiting", status.label)
        assertEquals("Waiting", downloadButtonLabel(status))
        assertFalse(status.canStart)
        val moved = queued.copy(sourceKey = "shadow", url = "https://cdn-b.example/a.mp4")
        assertEquals("Queued", downloadStatus(remote, moved, playingHost = "cdn-a.example").label)
        assertEquals("In line", downloadStatus(remote, queued, ahead = 2).label)
        assertEquals(
            "dub",
            downloadAudioHint("label\u0001\u0001English dub\u0001en\u0001https://cdn/a"),
        )
        assertEquals("sub", downloadAudioHint("label"))
        assertEquals(403, httpCodeIn(IllegalStateException("Response code: 403")))
        assertTrue(downloadIoShouldResume(1))
        assertTrue(downloadIoShouldResume(2))
        assertTrue(downloadIoShouldResume(3))
        assertTrue(downloadIoShouldResume(4))
        assertFalse(downloadIoShouldResume(5))
    }
}
