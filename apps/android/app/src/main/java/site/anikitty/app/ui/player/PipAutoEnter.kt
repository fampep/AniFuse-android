// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational

/**
 * Home and Recents shrink local playback into a picture-in-picture window.
 * A finished episode stays full screen so the next-episode card is usable.
 * Chromecast, including a receiver this screen just joined, keeps the phone
 * out of that window.
 *
 * [playWhenReady] covers the opening buffer: the viewer already asked it to
 * play, so Home should not drop them back to the catalog while it buffers.
 */
fun shouldAutoEnterPip(
    localPlaying: Boolean,
    playbackEnded: Boolean,
    castBlocks: Boolean,
    playWhenReady: Boolean = false,
): Boolean = (localPlaying || playWhenReady) && !playbackEnded && !castBlocks

/** Width/height clamped to the range Android accepts for a PiP window. */
internal fun pipAspectRatio(width: Int, height: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 16 to 9
    val ratio = width.toFloat() / height.toFloat()
    if (ratio < 1f / 2.39f || ratio > 2.39f) return 16 to 9
    return width to height
}

internal fun pipAspect(width: Int, height: Int): Rational {
    val (w, h) = pipAspectRatio(width, height)
    return Rational(w, h)
}

/**
 * True while the TV owns this episode or a handoff is in progress.
 * [handoffToCast] is the gap after a load starts and before [casting] flips on,
 * which is also how a live receiver is adopted.
 */
fun castBlocksPictureInPicture(
    casting: Boolean,
    castPending: Boolean,
    handoffToCast: Boolean,
): Boolean = casting || castPending || handoffToCast

/**
 * Remembers whether the open player may enter PiP, and tells the system.
 * Android 12+ enters on Home and Recents by itself. Older versions use
 * [onUserLeaveHint].
 */
object PipAutoEnter {
    @Volatile
    var allowed: Boolean = false
        private set

    @Volatile
    private var lastWidth: Int = 0

    @Volatile
    private var lastHeight: Int = 0

    @Volatile
    private var lastSourceRect: Rect? = null

    @Volatile
    private var lastPlaying: Boolean = false

    fun update(
        activity: Activity,
        allowed: Boolean,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        sourceRect: Rect? = null,
        playing: Boolean = lastPlaying,
    ) {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        this.allowed = allowed && supported
        lastPlaying = playing
        if (videoWidth > 0 && videoHeight > 0) {
            lastWidth = videoWidth
            lastHeight = videoHeight
        }
        if (sourceRect != null && !sourceRect.isEmpty) {
            lastSourceRect = Rect(sourceRect)
        }
        if (!supported) return
        runCatching {
            activity.setPictureInPictureParams(
                params(
                    activity = activity,
                    autoEnter = this.allowed,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    sourceRect = sourceRect,
                    playing = playing,
                ),
            )
        }
    }

    fun params(
        activity: Activity,
        autoEnter: Boolean,
        videoWidth: Int,
        videoHeight: Int,
        sourceRect: Rect?,
        playing: Boolean = lastPlaying,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(pipAspect(videoWidth, videoHeight))
        if (sourceRect != null && !sourceRect.isEmpty) {
            builder.setSourceRectHint(sourceRect)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setActions(pipActions(activity, playing))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    fun onUserLeaveHint(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!allowed || activity.isInPictureInPictureMode) return
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(pipAspect(lastWidth, lastHeight))
            .setActions(pipActions(activity, lastPlaying))
        val rect = lastSourceRect
        if (rect != null && !rect.isEmpty) {
            builder.setSourceRectHint(rect)
        }
        runCatching { activity.enterPictureInPictureMode(builder.build()) }
    }
}

/** The player screen installs these while it is open. The PiP window calls them. */
internal object PipControls {
    var onPlayPause: (() -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null
}

internal class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE -> PipControls.onPlayPause?.invoke()
            ACTION_REWIND -> PipControls.onSeek?.invoke(-PIP_SEEK_STEP_MS)
            ACTION_FORWARD -> PipControls.onSeek?.invoke(PIP_SEEK_STEP_MS)
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "site.anikitty.app.pip.PLAY_PAUSE"
        const val ACTION_REWIND = "site.anikitty.app.pip.REWIND"
        const val ACTION_FORWARD = "site.anikitty.app.pip.FORWARD"
    }
}

/** PiP seek step — matches double-tap / chrome skip. */
internal const val PIP_SEEK_STEP_MS = 10_000L

/**
 * Play/pause glyph for the PiP remote action strip.
 * While the episode is playing the strip must show Pause, not a stuck Play.
 */
internal fun pipPlayPauseIconRes(playing: Boolean): Int =
    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

internal fun pipPlayPauseTitle(playing: Boolean): String =
    if (playing) "Pause" else "Play"

/**
 * Android 13+ only refreshes a PiP remote-action icon when the PendingIntent
 * request code changes. Play and pause therefore need distinct codes.
 */
internal fun pipPlayPauseRequestCode(playing: Boolean): Int =
    if (playing) 22 else 21

private fun pipActions(activity: Activity, playing: Boolean): List<RemoteAction> {
    fun pending(action: String, request: Int): PendingIntent {
        val intent = Intent(activity, PipActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            activity,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    val toggleTitle = pipPlayPauseTitle(playing)
    return listOf(
        RemoteAction(
            Icon.createWithResource(activity, android.R.drawable.ic_media_rew),
            "Back 10 seconds",
            "Back 10 seconds",
            pending(PipActionReceiver.ACTION_REWIND, 1),
        ),
        RemoteAction(
            Icon.createWithResource(activity, pipPlayPauseIconRes(playing)),
            toggleTitle,
            toggleTitle,
            pending(PipActionReceiver.ACTION_PLAY_PAUSE, pipPlayPauseRequestCode(playing)),
        ),
        RemoteAction(
            Icon.createWithResource(activity, android.R.drawable.ic_media_ff),
            "Forward 10 seconds",
            "Forward 10 seconds",
            pending(PipActionReceiver.ACTION_FORWARD, 3),
        ),
    )
}
