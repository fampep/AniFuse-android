// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

import kotlinx.coroutines.CancellationException

/**
 * On-device AniVault.
 *
 * anivault.tv is a player over live sites this phone already resolves.
 * Selecting AniVault races those sites here and plays the winning file
 * on the phone. It is not in the race when another site is selected, so
 * the same episode is not resolved twice.
 */
class AniVaultServer(
    private val peers: () -> List<StreamResolver>,
) : StreamResolver {
    override val server = StreamServer(
        id = ID,
        name = "AniVault",
        description = "On-device AniVault. Races the same live sites as anivault.tv. The file plays on this phone.",
        iconUrl = "https://www.google.com/s2/favicons?domain=anivault.tv&sz=128",
        siteUrl = ORIGIN,
    )

    override suspend fun resolve(
        anilistId: Int,
        episode: Int,
        audio: String,
        titleHint: String?,
    ): List<PlayableSource> {
        require(anilistId > 0) { "anilistId required" }
        require(episode > 0) { "episode required" }
        val order = anivaultPeers(peers())
        if (order.isEmpty()) error("AniVault has no sources on this phone")
        val raced = raceHeadStart(
            count = order.size,
            parallel = 4,
            headStartMs = 400L,
            timeoutMs = 12_000L,
        ) { index ->
            val peer = order[index]
            try {
                peer.resolve(anilistId, episode, audio, titleHint).takeIf { it.isNotEmpty() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } ?: error("AniVault could not find this episode")
        return namedByServer(raced.second, server.name)
    }

    companion object {
        const val ID = "anivault"
        const val ORIGIN = "https://anivault.tv/"
    }
}

/** Sites anivault.tv asks first, then any other resolver on the phone. */
internal val ANIVAULT_PEER_IDS = listOf(
    "anikoto",
    "reanime",
    "aniwaves",
    "anizone",
    "anigato",
    "gogoanime",
    "animegg",
)

internal fun anivaultPeers(available: List<StreamResolver>): List<StreamResolver> {
    val byId = available.filter { it.server.id != AniVaultServer.ID }.associateBy { it.server.id }
    val preferred = ANIVAULT_PEER_IDS.mapNotNull { byId[it] }
    val rest = available.filter { it.server.id != AniVaultServer.ID && it.server.id !in ANIVAULT_PEER_IDS }
    return preferred + rest
}

/**
 * AniVault is the whole race when it is selected, because it already races
 * the other sites. Otherwise it stays out so those sites are not asked twice.
 */
internal fun playbackResolvers(selectedId: String, resolvers: List<StreamResolver>): List<StreamResolver> {
    if (selectedId != AniVaultServer.ID) return resolvers.filter { it.server.id != AniVaultServer.ID }
    val vault = resolvers.filter { it.server.id == AniVaultServer.ID }
    return if (vault.isEmpty()) resolvers else vault
}
