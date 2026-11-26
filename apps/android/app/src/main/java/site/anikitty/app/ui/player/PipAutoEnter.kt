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

    fun update(
        activity: Activity,
        allowed: Boolean,
        videoWidth: Int = 0,
        videoHeight: Int = 0,
        sourceRect: Rect? = null,
    ) {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        this.allowed = allowed && supported
        if (!supported) return
        runCatching {
            activity.setPictureInPictureParams(
                params(activity, this.allowed, videoWidth, videoHeight, sourceRect),
            )
        }
    }

    fun params(
        activity: Activity,
        autoEnter: Boolean,
        videoWidth: Int,
        videoHeight: Int,
        sourceRect: Rect?,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(pipAspect(videoWidth, videoHeight))
        if (sourceRect != null && !sourceRect.isEmpty) {
            builder.setSourceRectHint(sourceRect)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setActions(pipActions(activity))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        return builder.build()
    }

    fun onUserLeaveHint(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!allowed || activity.isInPictureInPictureMode) return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(pipActions(activity))
            .build()
        runCatching { activity.enterPictureInPictureMode(params) }
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
            ACTION_REWIND -> PipControls.onSeek?.invoke(-10_000L)
            ACTION_FORWARD -> PipControls.onSeek?.invoke(10_000L)
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "site.anikitty.app.pip.PLAY_PAUSE"
        const val ACTION_REWIND = "site.anikitty.app.pip.REWIND"
        const val ACTION_FORWARD = "site.anikitty.app.pip.FORWARD"
    }
}

private fun pipActions(activity: Activity): List<RemoteAction> {
    fun pending(action: String, request: Int): PendingIntent {
        val intent = Intent(activity, PipActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            activity,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    return listOf(
        RemoteAction(
            Icon.createWithResource(activity, android.R.drawable.ic_media_rew),
            "Back 10 seconds",
            "Back 10 seconds",
            pending(PipActionReceiver.ACTION_REWIND, 1),
        ),
        RemoteAction(
            Icon.createWithResource(activity, android.R.drawable.ic_media_play),
            "Play or pause",
            "Play or pause",
            pending(PipActionReceiver.ACTION_PLAY_PAUSE, 2),
        ),
        RemoteAction(
            Icon.createWithResource(activity, android.R.drawable.ic_media_ff),
            "Forward 10 seconds",
            "Forward 10 seconds",
            pending(PipActionReceiver.ACTION_FORWARD, 3),
        ),
    )
}
