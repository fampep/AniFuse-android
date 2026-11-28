// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsSegmentLaneTest {
    @After
    fun reset() {
        SegmentShadow.reset()
    }

    @Test
    fun aFailedSegmentMovesHostAndAPlaylistDoesNot() {
        val segment = "https://user:token@cdn-a.example:443/show/seg-12.ts?sig=1"
        assertTrue(isHlsMediaSegment(segment))
        assertFalse(isHlsMediaSegment("https://cdn-a.example/master.m3u8"))
        assertEquals(
            "https://cdn-b.example/show/seg-12.ts?sig=1",
            segmentRetargetUrl(403, segment, "cdn-a.example", "cdn-b.example"),
        )
        assertNull(segmentRetargetUrl(403, "https://cdn-a.example/master.m3u8", "cdn-a.example", "cdn-b.example"))
        assertNull(segmentRetargetUrl(200, segment, "cdn-a.example", "cdn-b.example"))
        assertNull(segmentRetargetUrl(403, segment, "cdn-c.example", "cdn-b.example"))
        assertEquals(
            "show/seg-12.ts",
            segmentCacheIdentity(segment),
        )
        assertEquals(
            segmentCacheIdentity(segment),
            segmentCacheIdentity("https://cdn-b.example/show/seg-12.ts?sig=9"),
        )
    }

    @Test
    fun aLeavingPlayerDoesNotClearTheNextEpisodeStandby() {
        val first = Any()
        val next = Any()
        SegmentShadow.arm("cdn-a.example", "cdn-b.example", mapOf("Referer" to "https://a"), first)
        SegmentShadow.arm("cdn-c.example", "cdn-d.example", emptyMap(), next)
        SegmentShadow.clear(first)
        assertEquals("cdn-d.example", SegmentShadow.current()?.toHost)
        SegmentShadow.clear(next)
        assertNull(SegmentShadow.current())
    }

    @Test
    fun tryArmNeverStealsPlaybackStandby() {
        val playback = Any()
        val download = Any()
        assertTrue(SegmentShadow.tryArm("cdn-a.example", "cdn-b.example", emptyMap(), download))
        assertEquals("cdn-b.example", SegmentShadow.current()?.toHost)
        // Playback may replace (arm), but a second tryArm must not steal.
        SegmentShadow.arm("cdn-c.example", "cdn-d.example", emptyMap(), playback)
        assertFalse(SegmentShadow.tryArm("cdn-e.example", "cdn-f.example", emptyMap(), download))
        assertEquals("cdn-d.example", SegmentShadow.current()?.toHost)
        SegmentShadow.clear(download)
        assertEquals("cdn-d.example", SegmentShadow.current()?.toHost)
        SegmentShadow.clear(playback)
        assertNull(SegmentShadow.current())
    }
}
