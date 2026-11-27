// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniVaultPlaybackTest {
    @Test
    fun peersFollowTheSiteOrderAndSkipAniVaultItself() {
        val available = listOf(
            stub("gogoanime"),
            stub("anivault"),
            stub("anizone"),
            stub("anikoto"),
            stub("extra"),
        )
        assertEquals(
            listOf("anikoto", "anizone", "gogoanime", "extra"),
            anivaultPeers(available).map { it.server.id },
        )
    }

    @Test
    fun selectingAniVaultRacesOnlyAniVault() {
        val all = listOf(stub("anikoto"), stub("anivault"), stub("reanime"))
        assertEquals(listOf("anivault"), playbackResolvers("anivault", all).map { it.server.id })
        assertEquals(listOf("anikoto", "reanime"), playbackResolvers("anikoto", all).map { it.server.id })
    }

    @Test
    fun aWinningPeerIsLabeledAniVault() = runBlocking {
        val peer = object : StreamResolver {
            override val server = StreamServer(id = "anikoto", name = "Anikoto", description = "")
            override suspend fun resolve(
                anilistId: Int,
                episode: Int,
                audio: String,
                titleHint: String?,
            ) = listOf(PlayableSource(url = "https://cdn.example/a.m3u8", label = "Anikoto"))
        }
        val vault = AniVaultServer { listOf(peer) }
        val sources = vault.resolve(21, 1, "sub", "One Piece")
        assertEquals("AniVault", sources.single().serverName)
        assertEquals("https://cdn.example/a.m3u8", sources.single().url)
    }

    @Test
    fun everyPeerStartsInsideTheOuterCap() = runBlocking {
        assertTrue(AniVaultServer.PEER_TIMEOUT_MS < RESOLVE_TIMEOUT_MS)
        val peers = (0 until 6).map { index ->
            object : StreamResolver {
                override val server = StreamServer(id = "peer$index", name = "peer$index", description = "")
                override suspend fun resolve(
                    anilistId: Int,
                    episode: Int,
                    audio: String,
                    titleHint: String?,
                ): List<PlayableSource> {
                    if (index < 5) delay(30_000)
                    return listOf(PlayableSource(url = "https://cdn.example/late.m3u8", label = "late"))
                }
            }
        }
        val vault = AniVaultServer { peers }
        val sources = withTimeout(2_000) { vault.resolve(21, 1, "sub", "One Piece") }
        assertEquals("https://cdn.example/late.m3u8", sources.single().url)
    }

    @Test
    fun avoidHostKeepsRacingForAnotherCdn() = runBlocking {
        val vault = AniVaultServer {
            listOf(
                source("same", "https://dead.cdn/a.m3u8"),
                source("other", "https://live.cdn/b.m3u8"),
            )
        }
        val sources = withContext(AvoidPlaybackHost("dead.cdn")) {
            vault.resolve(21, 1, "sub", "One Piece")
        }
        assertEquals("https://live.cdn/b.m3u8", sources.single().url)
    }

    @Test
    fun aSameHostOnlyHitIsLeftForTheOuterFilter() = runBlocking {
        val vault = AniVaultServer { listOf(source("same", "https://dead.cdn/a.m3u8")) }
        val sources = withContext(AvoidPlaybackHost("dead.cdn")) {
            vault.resolve(21, 1, "sub", "One Piece")
        }
        assertEquals("https://dead.cdn/a.m3u8", sources.single().url)
    }

    private fun source(id: String, url: String) = object : StreamResolver {
        override val server = StreamServer(id = id, name = id, description = "")
        override suspend fun resolve(
            anilistId: Int,
            episode: Int,
            audio: String,
            titleHint: String?,
        ) = listOf(PlayableSource(url = url, label = id))
    }

    private fun stub(id: String) = object : StreamResolver {
        override val server = StreamServer(id = id, name = id, description = "")
        override suspend fun resolve(
            anilistId: Int,
            episode: Int,
            audio: String,
            titleHint: String?,
        ): List<PlayableSource> = emptyList()
    }
}
