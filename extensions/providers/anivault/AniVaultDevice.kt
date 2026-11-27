// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

/**
 * AniVault buttons whose origin sites already ship as on-device scrapers.
 *
 * When Hoshika never returns an id, these buttons still play: the phone asks
 * the matching bundled scraper by title and keeps AniVault's label and lead key.
 * AnimeHeaven stays in [anivaultHeavenFiles]. Hindi / DesiDub stay remote-only.
 */
internal fun anivaultDevicePeerId(endpoint: String): String? = when {
    endpoint.startsWith("anikoto_") -> "anikoto"
    endpoint.startsWith("reanime_") -> "reanime"
    endpoint.startsWith("aniwaves_") -> "aniwaves"
    endpoint.startsWith("anizone_") -> "anizone"
    else -> null
}

/**
 * First button per origin peer, in AniVault's draw order.
 * A warm next episode keeps only the server that just played.
 */
internal fun anivaultDeviceJobs(
    buttons: List<AniVaultButton>,
    lead: String?,
    warm: Boolean,
): List<Triple<String, Int, AniVaultButton>> {
    if (warm && (lead.isNullOrBlank() || lead.startsWith("animeheaven_stream.php"))) {
        return emptyList()
    }
    val jobs = LinkedHashMap<String, Triple<String, Int, AniVaultButton>>()
    buttons.forEachIndexed { index, button ->
        if (warm && anivaultAskKey(button) != lead) return@forEachIndexed
        val peerId = anivaultDevicePeerId(button.endpoint) ?: return@forEachIndexed
        jobs.putIfAbsent(peerId, Triple(peerId, index, button))
    }
    return jobs.values.toList()
}

/** Later AniVault buttons that share the same origin peer. */
internal fun anivaultDeviceExtras(
    buttons: List<AniVaultButton>,
    peerId: String,
    skipIndex: Int,
): List<Pair<Int, AniVaultButton>> =
    buttons.mapIndexedNotNull { index, button ->
        if (index == skipIndex) return@mapIndexedNotNull null
        if (anivaultDevicePeerId(button.endpoint) != peerId) return@mapIndexedNotNull null
        index to button
    }

/** Stamp a peer file as the AniVault button that asked for it. */
internal fun anivaultDeviceSource(raw: PlayableSource, button: AniVaultButton): PlayableSource =
    raw.copy(
        label = button.label,
        serverName = null,
        leadKey = anivaultAskKey(button),
        audioLanguage = raw.audioLanguage ?: requestedAudioLanguage(button.audio),
    )
