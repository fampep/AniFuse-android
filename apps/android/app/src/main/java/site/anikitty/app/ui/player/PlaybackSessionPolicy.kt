// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

/** Movie audio so focus loss pauses us and a notification ding ducks instead of mixing flat. */
internal fun movieAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

/**
 * Foreground media notification while the phone is actually playing.
 * Cast, pause, and idle drop it so the Cast notification is the only one.
 */
internal fun shouldHoldPlaybackForeground(
    playWhenReady: Boolean,
    playbackState: Int,
    suppressed: Boolean,
): Boolean {
    if (suppressed || !playWhenReady) return false
    return playbackState == PLAYBACK_STATE_BUFFERING || playbackState == PLAYBACK_STATE_READY
}

/** Mirrors [androidx.media3.common.Player.STATE_BUFFERING] and STATE_READY. */
internal const val PLAYBACK_STATE_BUFFERING = 2
internal const val PLAYBACK_STATE_READY = 3

/**
 * An episode change composes the next player before the previous one leaves.
 * Only the screen that still owns the session may detach it.
 */
internal fun shouldDetachSession(held: Any?, leaving: Any?): Boolean {
    if (leaving != null && held != null && held !== leaving) return false
    return true
}

/**
 * Lock-screen and Bluetooth controls may bind. Other apps must not read the CDN URL.
 */
internal fun sessionTrusted(
    packageName: String,
    ownPackage: String,
    notificationController: Boolean,
): Boolean {
    if (notificationController) return true
    return packageName == ownPackage ||
        packageName == "com.android.systemui" ||
        packageName == "android" ||
        packageName == "com.android.bluetooth"
}
