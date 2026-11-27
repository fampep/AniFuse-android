// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

@file:Suppress("UnsafeOptInUsageError")
@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package site.anikitty.app.ui.player

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player

/**
 * The activity owns the player. The service owns the session and the
 * foreground notification. A generation counter drops a stop that would
 * otherwise kill a session reattached for the next episode. [release] also
 * ignores a player that no longer owns the session, so the outgoing episode
 * cannot detach the incoming one.
 */
internal object PlaybackSessions {
    @Volatile var suppressed: Boolean = false
    @Volatile var canSkipNext: Boolean = false
    @Volatile var canSkipPrevious: Boolean = false
    var onSkipNext: (() -> Unit)? = null
    var onSkipPrevious: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var player: Player? = null
    private var service: PlaybackSessionService? = null
    private var appContext: Context? = null
    private var generation = 0

    fun hold(context: Context, next: Player) {
        generation += 1
        player = next
        appContext = context.applicationContext
        service?.attach(next)
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaybackSessionService::class.java),
            )
        }
    }

    fun release(context: Context, leaving: Player? = null) {
        if (!shouldDetachSession(player, leaving)) return
        generation += 1
        val gen = generation
        player = null
        onSkipNext = null
        onSkipPrevious = null
        canSkipNext = false
        canSkipPrevious = false
        suppressed = false
        service?.detach()
        val app = context.applicationContext
        appContext = app
        main.post {
            if (generation != gen) return@post
            runCatching { app.stopService(Intent(app, PlaybackSessionService::class.java)) }
        }
    }

    fun onServiceCreated(created: PlaybackSessionService) {
        service = created
        player?.let { created.attach(it) }
    }

    fun onServiceDestroyed(dead: PlaybackSessionService) {
        if (service === dead) service = null
    }

    fun refresh() {
        service?.refreshForeground()
    }
}
