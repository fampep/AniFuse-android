// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnekoPlaybackTest {
    @Test
    fun backendsSkipExampleHostsAndKeepMainFirst() {
        val backends = onekoBackends(
            """{"backends":{"main":["https://s3.0neko.cfd/"],"backup":["https://example.com","https://s8.0neko.cc"]},"neko":"https://sh.0neko.cc"}""",
        )
        assertEquals(listOf("https://s3.0neko.cfd", "https://s8.0neko.cc"), backends)
    }

    @Test
    fun episodesAndStreamsSkipEmbeds() {
        val episodes = onekoEpisodes(
            """{"kaa":{"episodes":{"dub":[{"id":"watch/kaa/1535/dub/kaa-1","number":1},{"id":"watch/kaa/1535/dub/kaa-2","number":2}],"sub":[]}}}""",
            "kaa",
            "dub",
        )
        assertEquals("watch/kaa/1535/dub/kaa-1", onekoPickEpisode(episodes, 1)?.path)
        assertNull(onekoPickEpisode(episodes, 9))
        val streams = onekoStreams(
            """{"streams":[{"url":"https://cdn.example/a.m3u8","type":"hls","server":"VidStreaming","isActive":true,"headers":{"Referer":"https://krussdomi.com/"}},{"url":"https://cdn.example/embed","type":"embed","server":"X"}]}""",
        )
        assertEquals(listOf("VidStreaming"), streams.map { it.server })
        val playable = onekoPlayable(streams.single(), "dub")
        assertEquals("VidStreaming", playable.label)
        assertEquals("en", playable.audioLanguage)
        assertTrue(playable.hls)
        assertEquals("kaa", OnekoServer.PROVIDERS.first())
        assertTrue(OnekoServer.MISSING.contains("0neko"))
        assertTrue(OnekoServer.BLOCKED.contains("could not be reached"))
        assertTrue(isHardProviderFailure(OnekoServer.BLOCKED))
        assertFalse(isHardProviderFailure(OnekoServer.MISSING))
        assertTrue(OnekoReply(null, reachable = false).let { !it.reachable })
        assertTrue(OnekoReply("{}", reachable = true).reachable)
    }
}
