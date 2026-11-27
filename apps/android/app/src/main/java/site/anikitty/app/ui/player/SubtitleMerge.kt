// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import site.anikitty.app.data.servers.SubtitleTrack

/**
 * Provider sidecars win; library tracks fill gaps by URL so a warm
 * SubtitleLibrary.peek can paint captions before the CDN listing arrives.
 */
internal fun mergeSubtitleTracks(
    provider: List<SubtitleTrack>,
    library: List<SubtitleTrack>,
): List<SubtitleTrack> {
    if (library.isEmpty()) return provider
    if (provider.isEmpty()) return library
    val seen = provider.mapNotNull { it.url.trim().takeIf { url -> url.isNotEmpty() } }.toHashSet()
    val extras = library.filter { track ->
        val url = track.url.trim()
        url.isNotEmpty() && seen.add(url)
    }
    return if (extras.isEmpty()) provider else provider + extras
}
