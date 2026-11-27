// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
