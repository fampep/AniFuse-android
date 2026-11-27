// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

@file:Suppress("UnsafeOptInUsageError")
@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package site.anikitty.app.ui.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import site.anikitty.app.R

/**
 * Foreground playback for the phone player. Audio focus and headset unplug
 * are handled on the player itself; this service keeps the session alive for
 * the lock screen, PiP, and the switch to another app.
 */
class PlaybackSessionService : MediaSessionService() {
    private var session: MediaSession? = null
    private val callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val trusted = sessionTrusted(
                packageName = controller.packageName,
                ownPackage = packageName,
                notificationController = session.isMediaNotificationController(controller),
            )
            if (!trusted) return MediaSession.ConnectionResult.reject()
            return super.onConnect(session, controller)
        }
    }

    override fun onCreate() {
        val provider = DefaultMediaNotificationProvider(this)
        provider.setSmallIcon(R.drawable.ic_stat_kitty)
        setMediaNotificationProvider(provider)
        super.onCreate()
        PlaybackSessions.onServiceCreated(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val current = session
        val playing = current != null && shouldHoldPlaybackForeground(
            playWhenReady = current.player.playWhenReady,
            playbackState = current.player.playbackState,
            suppressed = PlaybackSessions.suppressed,
        )
        if (!playing) {
            ServiceCompat.startForeground(
                this,
                PLACEHOLDER_ID,
                placeholder(),
                mediaPlaybackType(),
            )
        }
        return result
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val player = session.player
        val hold = shouldHoldPlaybackForeground(
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            suppressed = PlaybackSessions.suppressed,
        )
        if (!hold) {
            if (startInForegroundRequired) {
                ServiceCompat.startForeground(this, PLACEHOLDER_ID, placeholder(), mediaPlaybackType())
            } else {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            return
        }
        notificationManager().cancel(PLACEHOLDER_ID)
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    fun attach(player: Player) {
        val existing = session
        if (existing != null && existing.player.unwrap() === player) {
            refreshForeground()
            return
        }
        detach()
        val forwarding = object : ForwardingPlayer(player) {
            override fun seekToNext() {
                PlaybackSessions.onSkipNext?.invoke() ?: super.seekToNext()
            }

            override fun seekToPrevious() {
                PlaybackSessions.onSkipPrevious?.invoke() ?: super.seekToPrevious()
            }

            override fun getAvailableCommands(): Player.Commands {
                val commands = super.getAvailableCommands().buildUpon()
                if (PlaybackSessions.canSkipNext) {
                    commands.add(Player.COMMAND_SEEK_TO_NEXT)
                } else {
                    commands.remove(Player.COMMAND_SEEK_TO_NEXT)
                }
                if (PlaybackSessions.canSkipPrevious) {
                    commands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                } else {
                    commands.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                }
                return commands.build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                if (command == Player.COMMAND_SEEK_TO_NEXT) return PlaybackSessions.canSkipNext
                if (command == Player.COMMAND_SEEK_TO_PREVIOUS) return PlaybackSessions.canSkipPrevious
                return super.isCommandAvailable(command)
            }
        }
        val created = MediaSession.Builder(this, forwarding)
            .setId(SESSION_ID)
            .setCallback(callback)
            .build()
        session = created
        addSession(created)
        refreshForeground()
    }

    fun detach() {
        val current = session ?: return
        session = null
        removeSession(current)
        current.release()
    }

    fun refreshForeground() {
        val current = session ?: return
        onUpdateNotification(
            current,
            /* startInForegroundRequired = */ shouldHoldPlaybackForeground(
                playWhenReady = current.player.playWhenReady,
                playbackState = current.player.playbackState,
                suppressed = PlaybackSessions.suppressed,
            ),
        )
    }

    override fun onDestroy() {
        detach()
        PlaybackSessions.onServiceDestroyed(this)
        super.onDestroy()
    }

    private fun placeholder(): Notification {
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.playback_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    description = "Episode playback"
                },
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kitty)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Playing")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun mediaPlaybackType(): Int =
        if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    private fun Player.unwrap(): Player {
        var current = this
        while (current is ForwardingPlayer) {
            val inner = current.wrappedPlayer
            if (inner === current) break
            current = inner
        }
        return current
    }

    private companion object {
        const val SESSION_ID = "anifuse-playback"
        const val CHANNEL_ID = "anifuse_playback"
        const val PLACEHOLDER_ID = 0xA71F
    }
}
