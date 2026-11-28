// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.cast

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle
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

/** True when the TV speaker still needs a setVolume for this target. */
internal fun castVolumeNeedsWrite(previous: Float, next: Float): Boolean =
    kotlin.math.abs(previous.coerceIn(0f, 1f) - next.coerceIn(0f, 1f)) > 0.0005f

/** Hold the last write briefly so a round-trip echo does not fight the finger. */
internal const val CAST_DEVICE_VOLUME_HOLD_MS = 400L

internal data class CastDeviceVolumeSnapshot(
    val level: Float,
    val fromSession: Boolean,
)

/**
 * Prefer the live session volume once the post-write hold expires.
 * A stale default hold must not mask the TV speaker forever.
 */
internal fun castDeviceVolumeSnapshot(
    sessionLevel: Float?,
    heldLevel: Float,
    nowMs: Long,
    lastWriteMs: Long,
    holdMs: Long = CAST_DEVICE_VOLUME_HOLD_MS,
): CastDeviceVolumeSnapshot {
    val live = sessionLevel?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    return if (live != null && nowMs - lastWriteMs > holdMs) {
        CastDeviceVolumeSnapshot(level = live, fromSession = true)
    } else {
        CastDeviceVolumeSnapshot(level = heldLevel.coerceIn(0f, 1f), fromSession = false)
    }
}

/** Sync the hold when the snapshot came from the live session. */
internal fun castHeldAfterRead(held: Float, snap: CastDeviceVolumeSnapshot): Float =
    if (snap.fromSession) snap.level else held

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

/**
 * Late library captions should [androidx.media3.common.Player.replaceMediaItem]
 * when the CastPlayer already has a live item — [setMediaItem]+[prepare] blacks
 * the TV for a beat. Callers must [seekTo] afterward: CastPlayer implements
 * replace as queue insert/remove and drops the playhead.
 */
internal fun castCanReplaceMediaItem(
    playbackState: Int,
    itemIndex: Int,
    mediaItemCount: Int,
): Boolean = playbackState != 1 /* Player.STATE_IDLE */ &&
    itemIndex >= 0 &&
    mediaItemCount > itemIndex

/** Playhead to stamp after a Cast caption replace (never negative). */
internal fun castLateCaptionSeekMs(positionMs: Long): Long = positionMs.coerceAtLeast(0L)

/**
 * Replace/setMediaItem attaches sidecar files but does not select them.
 * Callers must re-activate text tracks after a successful late push.
 */
internal fun castLateCaptionShouldActivateTracks(attached: Boolean): Boolean = attached

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

/**
 * Map phone caption studio values onto Chromecast [TextTrackStyle].
 * Edge constants match Media3 [androidx.media3.ui.CaptionStyleCompat].
 */
internal fun castTextTrackStyle(
    foregroundColor: Int,
    backgroundColor: Int,
    windowColor: Int,
    edgeType: Int,
    sizeSp: Float,
    bold: Boolean,
    italic: Boolean,
    typeface: String,
    baselineSp: Float = 18f,
): TextTrackStyle {
    val style = TextTrackStyle()
    style.setForegroundColor(foregroundColor)
    style.setBackgroundColor(backgroundColor)
    style.setWindowColor(windowColor)
    // Avoid android.graphics.Color so JVM unit tests stay mock-free.
    val windowAlpha = (windowColor ushr 24) and 0xff
    style.setWindowType(
        if (windowAlpha == 0) {
            TextTrackStyle.WINDOW_TYPE_NONE
        } else {
            TextTrackStyle.WINDOW_TYPE_NORMAL
        },
    )
    style.setEdgeColor(0xFF000000.toInt())
    style.setEdgeType(castMapEdgeType(edgeType))
    style.setFontScale(castFontScaleFromSp(sizeSp, baselineSp))
    style.setFontGenericFamily(castMapFontFamily(typeface))
    style.setFontStyle(
        when {
            bold && italic -> TextTrackStyle.FONT_STYLE_BOLD_ITALIC
            bold -> TextTrackStyle.FONT_STYLE_BOLD
            italic -> TextTrackStyle.FONT_STYLE_ITALIC
            else -> TextTrackStyle.FONT_STYLE_NORMAL
        },
    )
    return style
}

internal fun castMapEdgeType(captionEdge: Int): Int = when (captionEdge) {
    0 -> TextTrackStyle.EDGE_TYPE_NONE
    1 -> TextTrackStyle.EDGE_TYPE_OUTLINE
    2 -> TextTrackStyle.EDGE_TYPE_DROP_SHADOW
    3 -> TextTrackStyle.EDGE_TYPE_RAISED
    4 -> TextTrackStyle.EDGE_TYPE_DEPRESSED
    else -> TextTrackStyle.EDGE_TYPE_OUTLINE
}

internal fun castMapFontFamily(typeface: String): Int = when (typeface.trim().lowercase()) {
    "serif" -> TextTrackStyle.FONT_FAMILY_SERIF
    "mono", "monospace" -> TextTrackStyle.FONT_FAMILY_MONOSPACED_SANS_SERIF
    else -> TextTrackStyle.FONT_FAMILY_SANS_SERIF
}

/** Chromecast fontScale is relative to the receiver default (~1.0). */
internal fun castFontScaleFromSp(sizeSp: Float, baselineSp: Float = 18f): Float {
    val base = baselineSp.coerceAtLeast(1f)
    return (sizeSp / base).coerceIn(0.5f, 2.5f)
}
