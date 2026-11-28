// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.cast

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaTrack
import site.anikitty.app.data.servers.SubtitleTrack

/**
 * Sidecar WebVTT tracks for the Default Media Receiver. Media3's
 * [MediaItem.SubtitleConfiguration] list is the source of truth; Cast needs
 * [MediaTrack] entries on [com.google.android.gms.cast.MediaInfo] or they never
 * reach the TV.
 */
internal fun castTextTracks(
    configs: List<MediaItem.SubtitleConfiguration>,
): List<MediaTrack> {
    if (configs.isEmpty()) return emptyList()
    return configs.mapIndexed { index, config ->
        val uri = config.uri?.toString().orEmpty()
        MediaTrack.Builder((index + 1L), MediaTrack.TYPE_TEXT)
            .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
            .setContentId(uri)
            .setContentType(config.mimeType?.takeIf { it.isNotBlank() } ?: "text/vtt")
            .setName(config.label?.toString()?.ifBlank { null } ?: "Subtitles")
            .apply {
                val lang = config.language?.trim()?.takeIf { it.isNotEmpty() }
                if (lang != null) setLanguage(lang)
            }
            .build()
    }
}

/**
 * Which text track IDs should start active. Empty when captions are off or
 * there are no tracks. Prefers [SELECTION_FLAG_DEFAULT], then language/label
 * hints, then the first track.
 */
internal fun castActiveTextTrackIds(
    configs: List<MediaItem.SubtitleConfiguration>,
    tracks: List<MediaTrack>,
    enabled: Boolean,
    preferLanguage: String? = null,
    preferLabel: String? = null,
): LongArray {
    if (!enabled || tracks.isEmpty()) return longArrayOf()
    val wantLang = preferLanguage?.trim()?.lowercase()?.substringBefore('-')?.takeIf { it.isNotEmpty() }
    val wantLabel = preferLabel?.trim()?.takeIf { it.isNotEmpty() }
    val defaultIndex = configs.indexOfFirst {
        (it.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0
    }.takeIf { it >= 0 }
    val byLang = if (wantLang == null) -1 else tracks.indexOfFirst { track ->
        track.language?.trim()?.lowercase()?.substringBefore('-') == wantLang
    }
    val byLabel = if (wantLabel == null) -1 else tracks.indexOfFirst { track ->
        track.name.equals(wantLabel, ignoreCase = true)
    }
    val index = when {
        // The name the user picked wins over a shared language code.
        byLabel >= 0 -> byLabel
        byLang >= 0 -> byLang
        defaultIndex != null && defaultIndex in tracks.indices -> defaultIndex
        else -> 0
    }
    return longArrayOf(tracks[index].id)
}

/**
 * Track ids to put on the load request.
 *
 * An empty array tells Chromecast to activate nothing, so the TV stays black.
 * A text-only array does the same: the picture track is not in the list, so
 * the receiver turns it off. Leave this null and the receiver keeps its
 * default audio and video. Captions are selected after playback has started
 * and those picture tracks already have ids.
 */
@Suppress("UNUSED_PARAMETER")
internal fun castLoadActiveTrackIds(textTrackIds: LongArray): LongArray? {
    // [textTrackIds] names captions only. The receiver has not assigned the
    // video track yet, and a load that lists captions alone — or lists
    // nothing — turns that picture off. Captions are selected after playback
    // starts, once the picture track has an id.
    return null
}

/**
 * Chromecast reports a finished item as idle, and Media3 [androidx.media3.cast.CastPlayer]
 * maps that to [androidx.media3.common.Player.STATE_IDLE] — never STATE_ENDED.
 * [MediaStatus.PLAYER_STATE_IDLE] is 1 and [MediaStatus.IDLE_REASON_FINISHED] is 1.
 */
internal fun castPlaybackFinished(playerState: Int, idleReason: Int): Boolean =
    playerState == 1 && idleReason == 1

/**
 * Leftover [MediaStatus.IDLE_REASON_FINISHED] from the previous item must not
 * end the load that has not started yet.
 */
internal fun castItemEndedAfterStart(sawPlayback: Boolean, playerState: Int, idleReason: Int): Boolean =
    sawPlayback && castPlaybackFinished(playerState, idleReason)

/** One hardware / nudge step is 6% of the Chromecast speaker volume. */
internal const val CAST_VOLUME_STEP = 0.06f

internal fun castVolumeAfterNudge(current: Float, delta: Float): Float {
    val steps = when {
        delta > 0.012f -> 1
        delta < -0.012f -> -1
        else -> 0
    }
    return (current + steps * CAST_VOLUME_STEP).coerceIn(0f, 1f)
}

/**
 * Continuous finger volume must map from the level at gesture start.
 * Feeding the same delta into [castVolumeAfterNudge] every frame stair-steps
 * the TV by 6% per event and overshoots.
 */
internal fun castVolumeFromGesture(startLevel: Float, fingerDelta: Float): Float =
    (startLevel.coerceIn(0f, 1f) + fingerDelta).coerceIn(0f, 1f)

/** Stable identity for the sidecar list last given to the receiver. */
internal fun castCaptionKey(tracks: List<SubtitleTrack>): String =
    tracks.joinToString("\n") { it.url }

/** True when a cast session is up and these captions are not on the receiver yet. */
internal fun castShouldPushCaptions(
    casting: Boolean,
    sentKey: String,
    tracks: List<SubtitleTrack>,
): Boolean {
    if (!casting || tracks.isEmpty()) return false
    return castCaptionKey(tracks) != sentKey
}

/**
 * Late library captions used to wait ~3s (400ms × 8) before giving up.
 * Mirror phone subtitle attach: a tight 2s ceiling so the TV catches the
 * sidecar as soon as the relay can take a new MediaItem.
 */
internal const val CAST_CAPTION_PUSH_POLL_MS = 50L
internal const val CAST_CAPTION_PUSH_MAX_POLLS = 40

internal fun castCaptionPushBudgetMs(
    pollMs: Long = CAST_CAPTION_PUSH_POLL_MS,
    maxPolls: Int = CAST_CAPTION_PUSH_MAX_POLLS,
): Long = pollMs.coerceAtLeast(1L) * maxPolls.coerceAtLeast(1)

/** Pick an active text-track id from the live cast session tracks. */
internal fun castPickTextTrackId(
    tracks: List<MediaTrack>,
    enabled: Boolean,
    preferLanguage: String? = null,
    preferLabel: String? = null,
): LongArray {
    val text = tracks.filter { it.type == MediaTrack.TYPE_TEXT }
    if (!enabled || text.isEmpty()) return longArrayOf()
    val wantLang = preferLanguage?.trim()?.lowercase()?.substringBefore('-')?.takeIf { it.isNotEmpty() }
    val wantLabel = preferLabel?.trim()?.takeIf { it.isNotEmpty() }
    val pick = text.firstOrNull { track ->
        wantLabel != null && track.name.equals(wantLabel, ignoreCase = true)
    } ?: text.firstOrNull { track ->
        wantLang != null && track.language?.trim()?.lowercase()?.substringBefore('-') == wantLang
    } ?: text.first()
    return longArrayOf(pick.id)
}
