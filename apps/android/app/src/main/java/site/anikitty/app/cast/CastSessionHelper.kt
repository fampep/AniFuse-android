// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.HlsSegmentFormat
import com.google.android.gms.cast.HlsVideoSegmentFormat
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import site.anikitty.app.data.download.CastDownloadYieldOwner
import site.anikitty.app.data.servers.PlayableSource
import site.anikitty.app.data.servers.PlaybackHostHealth
import site.anikitty.app.data.servers.SubtitleTrack
import site.anikitty.app.data.servers.playableHost
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Plays through a phone-side relay so the Chromecast receives a clean stream.
 * Returns null when Play Services / Cast is unavailable.
 */
@OptIn(UnstableApi::class)
class CastSessionHelper private constructor(
    private val appContext: Context,
    private val castContext: CastContext,
    val castPlayer: CastPlayer,
    private val relay: CastRelay,
    private val multicast: WifiManager.MulticastLock?,
    private val captionsEnabled: AtomicBoolean,
    private val captionLanguage: java.util.concurrent.atomic.AtomicReference<String?>,
    private val captionLabel: java.util.concurrent.atomic.AtomicReference<String?>,
) {
    var casting: Boolean = false
        private set

    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    /** Applied again inside each Cast load, before playback starts. */
    @Volatile var heldVolume: Float = 1f
    /** Last TV speaker level, 0–1. Separate from the muted stream flag. */
    @Volatile var heldDeviceVolume: Float = 1f
    /** Ignore a stale session read while a swipe is still landing. */
    private var lastDeviceVolumeWriteMs: Long = 0L
    /** Captions preference mirrored onto the remote [MediaTrack] after load. */
    var heldSubtitlesOn: Boolean
        get() = captionsEnabled.get()
        set(value) { captionsEnabled.set(value) }
    var heldSubtitleLanguage: String?
        get() = captionLanguage.get()
        set(value) { captionLanguage.set(value) }
    var heldSubtitleLabel: String?
        get() = captionLabel.get()
        set(value) { captionLabel.set(value) }

    /** Last caption studio look pushed (or held) for the Default Media Receiver. */
    @Volatile private var heldTextTrackStyle: TextTrackStyle? = null

    private val closed = AtomicBoolean(false)
    private val loadSettled = AtomicBoolean(false)
    private val endedNotified = AtomicBoolean(false)
    private val activeLoadTicket = AtomicInteger(0)
    private val screens = AtomicInteger(0)
    private var statusCallback: RemoteMediaClient.Callback? = null
    private val playbackEndedListener = CastListenerSlot<() -> Unit>()
    private val availabilityListener = CastListenerSlot<SessionAvailabilityListener>()
    private val loadHooks = CastListenerSlot<CastLoadHooks>()
    private var sessionWatchInstalled = false

    /** Item the receiver was given, including a load that has not settled yet. */
    @Volatile internal var loadedItem: CastItemKey? = null
        private set

    /** Item the receiver is actually playing. A newer load does not replace this until it starts. */
    @Volatile internal var playingItem: CastItemKey? = null
        private set

    /** Ticket that last reached [settleReady]. Aborted follow-ups keep this. */
    private val settledTicket = AtomicInteger(0)

    /** Source last confirmed on the receiver — restore the relay after a keep. */
    @Volatile private var settledSource: PlayableSource? = null

    /** Ticket whose MediaItem was handed to CastPlayer — errors before this are uncommitted. */
    private val mediaCommittedTicket = AtomicInteger(0)

    /** Live phone playhead read when [start] finally commits media after a deferred wait. */
    @Volatile private var loadLivePosition: (() -> Long)? = null

    /** Cancels an in-flight keep-path relay restore when a newer load starts. */
    private val restoreEpoch = AtomicInteger(0)

    /** Last URL [warmForCast] primed — reused when [load] hits the same stream. */
    @Volatile private var warmUrl: String? = null

    /** True after a successful upstream peek while Cast was still Connecting. */
    @Volatile var upstreamWarmed: Boolean = false
        private set

    /**
     * Invalidates in-flight [CastRelay.prepare] without waiting on [prepareGate].
     * Bumped by load / stop / release; a stale claim must [CastRelay.stop] again
     * because prepare can reopen the socket after a concurrent stop.
     */
    private val prepareEpoch = AtomicInteger(0)

    /**
     * Serializes [CastRelay.publish]/[CastRelay.stop] with restore publish checks.
     * Never hold across [CastRelay.prepare] — that does network I/O and would freeze
     * main-thread [stopCasting]/[release].
     */
    private val relayLock = Any()

    /** Background-only: one prepare at a time. [stopCasting] must not wait on this. */
    private val prepareGate = Any()

    private val ticketItem = AtomicReference<Pair<Int, CastItemKey>?>(null)
    private val ticketSource = AtomicReference<Pair<Int, PlayableSource>?>(null)
    private var screenProgress: CastProgressTarget? = null

    /** History target that outlives the player screen. */
    @Volatile internal var progressTarget: CastProgressTarget? = null
        private set

    /** Last episode already sent to AniList from the cast service. */
    @Volatile internal var reportedThrough: Int = -1

    /** Set by [CastPlaybackService]. Session end reaches this even after the player detaches. */
    @Volatile internal var serviceWatch: CastServiceWatch? = null

    val isClosed: Boolean get() = closed.get()

    fun hasScreen(): Boolean = screens.get() > 0

    fun attachScreen() {
        screens.incrementAndGet()
    }

    fun detachScreen() {
        val left = screens.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        // The item already finished while this screen was open. Leaving now
        // has to stop the relay; the finish callback skipped that on purpose.
        if (castShouldStopWhenScreenDetaches(left, endedNotified.get(), isLoadInFlight())) {
            stopCasting()
        }
    }

    internal fun noteProgress(target: CastProgressTarget?) {
        screenProgress = target
        commitProgress()
    }

    /** Keep the saved row on the item the TV is playing, not the episode merely open on screen. */
    private fun commitProgress() {
        val committed = commitCastProgress(
            screen = screenProgress,
            playing = playingItem,
            current = progressTarget,
            reportedThrough = reportedThrough,
        )
        progressTarget = committed.target
        reportedThrough = committed.reportedThrough
    }

    private fun clearProgress() {
        progressTarget = null
        reportedThrough = -1
    }

    fun receiverPositionMs(): Long = runCatching {
        castPlayer.currentPosition.coerceAtLeast(0L)
    }.getOrDefault(0L)

    fun receiverDurationMs(): Long = runCatching {
        castPlayer.duration.takeIf { it > 0L } ?: 0L
    }.getOrDefault(0L)

    internal fun replaceLoadHooks(onReady: () -> Unit, onError: (String) -> Unit): CastLoadHooks {
        val hooks = CastLoadHooks(onReady, onError)
        loadHooks.set(hooks)
        return hooks
    }

    internal fun clearLoadHooks(hooks: CastLoadHooks) {
        loadHooks.clear(hooks)
    }

    /**
     * Session end and shade-stop have to land here. The player clears its own
     * listener when it leaves, and that must not orphan the relay.
     */
    fun ensureSessionWatch() {
        if (sessionWatchInstalled) return
        sessionWatchInstalled = true
        castContext.sessionManager.addSessionManagerListener(
            object : SessionManagerListener<CastSession> {
                override fun onSessionEnded(session: CastSession, error: Int) = onReceiverGone()
                override fun onSessionEnding(session: CastSession) = onReceiverGone()
                override fun onSessionResumeFailed(session: CastSession, error: Int) = onReceiverGone()
                override fun onSessionStartFailed(session: CastSession, error: Int) = onReceiverGone()
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = Unit
                override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
                override fun onSessionStarted(session: CastSession, sessionId: String) = Unit
                override fun onSessionStarting(session: CastSession) = Unit
                override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
            },
            CastSession::class.java,
        )
    }

    private fun onReceiverGone() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { onReceiverGone() }
            return
        }
        if (!casting && !isLoadInFlight()) return
        stopCasting()
        serviceWatch?.onSessionEnded()
    }

    /** True from the moment a load is requested until that load settles or a newer one replaces it. */
    fun isLoadInFlight(): Boolean = activeLoadTicket.get() != 0

    /** True after [start] handed a MediaItem to CastPlayer for the active ticket. */
    fun hasMediaCommitted(): Boolean = mediaCommittedTicket.get() != 0

    /** Fires once per load when the receiver finishes the current item. */
    fun setPlaybackEndedListener(listener: (() -> Unit)?) {
        playbackEndedListener.set(listener)
    }

    /** Drops [listener] only while it is still the one the player installed. */
    fun clearPlaybackEndedListener(listener: () -> Unit) {
        playbackEndedListener.clear(listener)
    }

    fun setAvailabilityListener(listener: SessionAvailabilityListener?) {
        availabilityListener.set(listener)
        castPlayer.setSessionAvailabilityListener(listener)
    }

    /** Drops [listener] only while it is still the one the player installed. */
    fun clearAvailabilityListener(listener: SessionAvailabilityListener) {
        if (!availabilityListener.clear(listener)) return
        castPlayer.setSessionAvailabilityListener(null)
    }

    private fun readiness(): CastSessionReadiness = runCatching {
        val session = castContext.sessionManager.currentCastSession
        castSessionReadiness(
            connected = session?.isConnected == true,
            hasRemoteClient = session?.remoteMediaClient != null,
            playerSessionAvailable = castPlayer.isCastSessionAvailable,
        )
    }.getOrDefault(CastSessionReadiness.Down)

    /**
     * True only when the receiver media client exists. CastPlayer's session
     * flag and [com.google.android.gms.cast.framework.CastSession.isConnected]
     * can both be true before that client is ready to load.
     */
    fun isSessionAvailable(): Boolean = readiness() == CastSessionReadiness.Ready

    /** Session is up, or CastPlayer says so, but the receiver client is not ready yet. */
    fun isSessionConnecting(): Boolean = readiness() == CastSessionReadiness.Connecting

    /**
     * Move the Chromecast speaker volume by one nudge step. Returns the new
     * level, 0–1. Hardware keys use this path.
     */
    fun adjustDeviceVolume(delta: Float): Float {
        val current = readDeviceVolume()
        return setDeviceVolume(castVolumeAfterNudge(current, delta), unmuteIfRaising = delta > 0.012f)
    }

    /**
     * Absolute Chromecast speaker level for a continuous finger gesture.
     * [startLevel] is captured when the drag begins; [fingerDelta] is relative
     * to that start (not a per-frame nudge).
     */
    fun gestureDeviceVolume(startLevel: Float, fingerDelta: Float): Float =
        setDeviceVolume(
            castVolumeFromGesture(startLevel, fingerDelta),
            unmuteIfRaising = fingerDelta > 0.012f,
        )

    /** Current TV speaker level — session when fresh, else the last write we remember. */
    fun readDeviceVolume(): Float {
        val session = castContext.sessionManager.currentCastSession
        val now = android.os.SystemClock.uptimeMillis()
        val sessionLevel = session?.let { runCatching { it.volume.toFloat() }.getOrNull() }
        val snap = castDeviceVolumeSnapshot(
            sessionLevel = sessionLevel,
            heldLevel = heldDeviceVolume,
            nowMs = now,
            lastWriteMs = lastDeviceVolumeWriteMs,
        )
        heldDeviceVolume = castHeldAfterRead(heldDeviceVolume, snap)
        return snap.level
    }

    private fun setDeviceVolume(level: Float, unmuteIfRaising: Boolean): Float {
        val session = castContext.sessionManager.currentCastSession
        // Compare against the live session (or fresh hold), not a stale default hold.
        val previous = readDeviceVolume()
        val next = level.coerceIn(0f, 1f)
        heldDeviceVolume = next
        if (session != null && castVolumeNeedsWrite(previous, next)) {
            lastDeviceVolumeWriteMs = android.os.SystemClock.uptimeMillis()
            // setVolume throws IOException, so it is not a Kotlin property setter.
            runCatching { session.setVolume(next.toDouble()) }
            if (unmuteIfRaising && runCatching { session.isMute }.getOrDefault(false)) {
                runCatching { session.setMute(false) }
                heldVolume = 1f
                castPlayer.volume = 1f
            }
        }
        return next
    }

    /** Mute the TV stream without changing the remembered speaker level. */
    fun setTvMuted(muted: Boolean) {
        val session = castContext.sessionManager.currentCastSession
        runCatching { session?.setMute(muted) }
        heldVolume = if (muted) 0f else 1f
        castPlayer.volume = heldVolume
    }

    /**
     * Turn captions on/off on the Chromecast, or pick a language/label.
     * Safe to call before the first load — preference is held for the next one.
     */
    fun applySubtitles(
        enabled: Boolean,
        language: String? = null,
        label: String? = null,
    ) {
        heldSubtitlesOn = enabled
        heldSubtitleLanguage = language
        heldSubtitleLabel = label
        if (!casting) return
        pushActiveTextTracks()
    }

    /**
     * Push phone caption studio look to the Chromecast. Safe before the first
     * load — the style is held and applied when playback settles.
     */
    fun applyTextTrackStyle(style: TextTrackStyle) {
        heldTextTrackStyle = style
        if (!casting) return
        pushTextTrackStyle()
    }

    /**
     * The library answered after the receiver was already playing. Add the
     * sidecar files without tearing down the phone relay.
     * False when this session cannot take a new media item yet.
     */
    fun pushLibrarySubtitles(tracks: List<SubtitleTrack>, positionMs: Long): Boolean {
        if (!casting || tracks.isEmpty()) return false
        val current = castPlayer.currentMediaItem ?: return false
        val relayed = relay.attachSubtitles(tracks) ?: return false
        val item = current.buildUpon().setSubtitleConfigurations(subtitleConfigurations(relayed)).build()
        val index = castPlayer.currentMediaItemIndex
        val seekMs = castLateCaptionSeekMs(positionMs)
        // Soft replace avoids a full idle→prepare blackout, but CastPlayer's
        // queue insert/remove drops the playhead — stamp it back immediately.
        if (castCanReplaceMediaItem(castPlayer.playbackState, index, castPlayer.mediaItemCount)) {
            castPlayer.replaceMediaItem(index, item)
            castPlayer.seekTo(seekMs)
        } else {
            castPlayer.setMediaItem(item, seekMs)
            castPlayer.prepare()
        }
        castPlayer.volume = heldVolume
        castPlayer.playWhenReady = true
        // Replace attaches sidecar files but does not select them — without
        // this the TV stays caption-dark until the user toggles captions.
        pushActiveTextTracks()
        // Track IDs often appear one status tick after replace; re-select then.
        main.post {
            if (!closed.get() && casting) pushActiveTextTracks()
        }
        return true
    }

    /**
     * Bind transport + peek the upstream playlist while Cast is Connecting so
     * [load] → [CastRelay.prepare] does not pay cold DNS/TLS/CDN on handoff.
     * Safe before the media client exists; does not open the LAN relay.
     */
    fun warmForCast(source: PlayableSource): Boolean {
        if (closed.get()) return false
        if (castWarmMatches(warmUrl, source.url) && upstreamWarmed) return true
        val ok = runCatching { relay.warmUpstream(source) }.getOrDefault(false)
        warmUrl = source.url
        upstreamWarmed = ok
        return ok
    }

    internal fun load(
        source: PlayableSource,
        title: String,
        episode: Int,
        positionMs: Long,
        anilistId: Int = 0,
        maxVideoHeight: Int? = null,
        /** Fresh phone playhead at commit time — phone keeps playing during deferred start. */
        livePositionMs: (() -> Long)? = null,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ): CastLoadHooks {
        // Cancel any keep-path restore before mutating the relay for this load.
        restoreEpoch.incrementAndGet()
        val prepareClaim = prepareEpoch.incrementAndGet()
        val ticket = generation.incrementAndGet()
        activeLoadTicket.set(ticket)
        loadLivePosition = livePositionMs
        val ceiling = castHeightKey(maxVideoHeight)
        // Headers already bound when warmForCast ran for this URL.
        if (!castWarmMatches(warmUrl, source.url)) {
            runCatching { relay.bindTransport(source, ceiling.takeIf { it > 0 }) }
        }
        val item = CastItemKey(
            anilistId = anilistId,
            episode = episode,
            url = source.url,
            audio = castAudioKey(source.audioLanguage),
            height = ceiling,
        )
        loadedItem = item
        ticketItem.set(ticket to item)
        ticketSource.set(ticket to source)
        val hooks = replaceLoadHooks(onReady, onError)
        // The relay has to outlive the screen that requested this episode.
        CastPlaybackService.promote(appContext, title, episode)
        thread(name = "anikitty-cast-load", isDaemon = true) {
            // prepareGate only — never relayLock. stopCasting bumps prepareEpoch and
            // closes the socket without waiting; stale claims stop again after I/O.
            val prepared = runCatching {
                prepareClaimed(prepareClaim, source, maxHeight = ceiling.takeIf { it > 0 })
            }
            main.post {
                if (closed.get() || ticket != generation.get()) {
                    abortLoad(ticket, mediaCommitted = false)
                    return@post
                }
                prepared.fold(
                    onSuccess = { playback ->
                        if (playback == null) {
                            abortLoad(ticket, mediaCommitted = false)
                            return@fold
                        }
                        runCatching { start(playback, title, episode, positionMs, ticket) }
                            .onFailure {
                                Log.w(TAG, "Cast prepare failed", it)
                                val stillMine = ticket == generation.get()
                                abortLoad(ticket, mediaCommitted = false)
                                if (stillMine) fireLoadError(it.message ?: "Cast failed")
                            }
                    },
                    onFailure = {
                        val stillMine = ticket == generation.get()
                        abortLoad(ticket, mediaCommitted = false)
                        if (stillMine) fireLoadError(it.message ?: "Cast failed")
                    },
                )
            }
        }
        return hooks
    }

    private fun fireLoadReady() {
        loadHooks.current?.ready()
    }

    private fun fireLoadError(message: String) {
        loadHooks.current?.failed(message)
    }

    /**
     * Abort an in-flight ticket. When the receiver never took the new item,
     * keep the settled episode (relay + yield + loadedItem). Otherwise tear down.
     */
    private fun abortLoad(ticket: Int, mediaCommitted: Boolean): Boolean {
        if (!activeLoadTicket.compareAndSet(ticket, 0)) return false
        // Drop the follow-up callback. loadSettled=true makes any late settle* no-op.
        disarmWatchLoad()
        val keep = castKeepPlayingAfterDrop(
            settledTicket = settledTicket.get(),
            abortedTicket = ticket,
            playingItem = playingItem,
            mediaCommitted = mediaCommitted,
        )
        if (keep) {
            casting = true
            loadedItem = castLoadedAfterKeep(keep = true, playingItem = playingItem)
            mediaCommittedTicket.set(0)
            val settled = settledTicket.get()
            val epoch = restoreEpoch.incrementAndGet()
            thread(name = "anikitty-cast-restore", isDaemon = true) {
                restoreSettledRelay(epoch = epoch, expectedSettled = settled)
            }
            // Re-arm ended detection for the episode still on the TV.
            rearmSettledWatch(settled)
            val host = playableHost(playingItem!!.url).ifBlank { null }
            if (host != null) {
                PlaybackHostHealth.notePlaying(host, CastDownloadYieldOwner)
            }
            CastPlaybackService.demoteIfIdle()
            return true
        }
        restoreEpoch.incrementAndGet()
        prepareEpoch.incrementAndGet()
        casting = false
        playingItem = null
        loadedItem = null
        settledTicket.set(0)
        settledSource = null
        mediaCommittedTicket.set(0)
        ticketItem.set(null)
        ticketSource.set(null)
        synchronized(relayLock) { relay.stop() }
        PlaybackHostHealth.clearPlaying(CastDownloadYieldOwner)
        CastPlaybackService.demoteIfIdle()
        return false
    }

    /** Drop the in-flight RemoteMediaClient callback so a keep cannot settleReady later. */
    private fun disarmWatchLoad() {
        loadSettled.set(true)
        val client = castContext.sessionManager.currentCastSession?.remoteMediaClient
        statusCallback?.let { previous ->
            runCatching { client?.unregisterCallback(previous) }
        }
        statusCallback = null
    }

    /**
     * After a keep, the follow-up watchLoad is gone. Listen again for the
     * settled ticket finishing so autoplay / cast-service end still fire.
     */
    private fun rearmSettledWatch(ticket: Int) {
        if (ticket == 0 || closed.get()) return
        val client = castContext.sessionManager.currentCastSession?.remoteMediaClient ?: return
        loadSettled.set(true)
        endedNotified.set(false)
        statusCallback?.let { previous -> runCatching { client.unregisterCallback(previous) } }
        val callback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                if (closed.get() || settledTicket.get() != ticket) return
                if (activeLoadTicket.get() != 0) return
                val status = client.mediaStatus ?: return
                if (castPlaybackFinished(status.playerState, status.idleReason)) {
                    notifySettledPlaybackEnded(ticket)
                }
            }
        }
        statusCallback = callback
        client.registerCallback(callback)
    }

    private fun notifySettledPlaybackEnded(ticket: Int) {
        if (!endedNotified.compareAndSet(false, true)) return
        Log.i(TAG, "Cast settled item finished")
        main.post {
            if (closed.get() || settledTicket.get() != ticket) return@post
            if (activeLoadTicket.get() != 0) return@post
            playbackEndedListener.current?.invoke()
            serviceWatch?.onItemFinished()
        }
    }

    /**
     * Run [CastRelay.prepare] under [prepareGate] only. Returns null when the claim
     * was cancelled; may call [CastRelay.stop] if prepare revived the socket after
     * a concurrent stop. Never holds [relayLock] across network I/O.
     */
    private fun prepareClaimed(
        claim: Int,
        source: PlayableSource,
        maxHeight: Int? = null,
    ): RelayPlayback? {
        synchronized(prepareGate) {
            if (!castRelayPrepareStillOwns(claim, prepareEpoch.get(), closed.get())) {
                return null
            }
            val playback = relay.prepare(source, maxHeight = maxHeight)
            if (!castRelayPrepareStillOwns(claim, prepareEpoch.get(), closed.get())) {
                synchronized(relayLock) { relay.stop() }
                return null
            }
            return playback
        }
    }

    /** Prepare already pointed the relay at the failed follow-up — put the live episode back. */
    private fun restoreSettledRelay(epoch: Int, expectedSettled: Int) {
        val prev = settledSource ?: return
        val claim = prepareEpoch.get()
        // Re-check mayPrepare inside prepareGate so a newer load cannot be overwritten.
        val playback = runCatching {
            synchronized(prepareGate) {
                if (!castRelayPrepareStillOwns(claim, prepareEpoch.get(), closed.get())) {
                    return@runCatching null
                }
                if (!castRelayRestoreMayPrepare(
                        restoreEpoch = epoch,
                        currentEpoch = restoreEpoch.get(),
                        expectedSettled = expectedSettled,
                        settledTicket = settledTicket.get(),
                        activeLoadTicket = activeLoadTicket.get(),
                        closed = closed.get(),
                    )
                ) {
                    return@runCatching null
                }
                val result = relay.prepare(prev)
                if (!castRelayPrepareStillOwns(claim, prepareEpoch.get(), closed.get())) {
                    synchronized(relayLock) { relay.stop() }
                    return@runCatching null
                }
                result
            }
        }.getOrElse {
            Log.w(TAG, "Cast relay restore failed", it)
            return
        } ?: return
        synchronized(relayLock) {
            // Cancelled during prepare — do not publish; undo a revive after stop.
            if (!castRelayRestoreMayPublish(
                    restoreEpoch = epoch,
                    currentEpoch = restoreEpoch.get(),
                    expectedSettled = expectedSettled,
                    settledTicket = settledTicket.get(),
                    activeLoadTicket = activeLoadTicket.get(),
                    hasPlayingItem = playingItem != null,
                ) || closed.get()
            ) {
                if (castRelayDiscardShouldStop(
                        claim = claim,
                        prepareEpoch = prepareEpoch.get(),
                        closed = closed.get(),
                        activeLoadTicket = activeLoadTicket.get(),
                    )
                ) {
                    relay.stop()
                }
                return
            }
            relay.publish(playback)
        }
    }

    private fun subtitleConfigurations(tracks: List<RelaySubtitle>): List<MediaItem.SubtitleConfiguration> {
        val preferLang = heldSubtitleLanguage
        val preferLabel = heldSubtitleLabel
        val built = tracks.mapIndexed { index, track ->
            val default = when {
                preferLang != null &&
                    track.language?.trim()?.lowercase()?.substringBefore('-') ==
                    preferLang.trim().lowercase().substringBefore('-') -> true
                preferLabel != null && track.label.equals(preferLabel, ignoreCase = true) -> true
                preferLang == null && preferLabel == null && index == 0 -> true
                else -> false
            }
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track.url))
                .setMimeType(MimeTypes.TEXT_VTT)
                .apply {
                    track.language?.trim()?.takeIf { it.isNotEmpty() }?.let { setLanguage(it) }
                }
                .setLabel(track.label)
                .setSelectionFlags(if (default) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        if (!heldSubtitlesOn || built.isEmpty() || built.any {
                (it.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0
            }
        ) {
            return built
        }
        return built.mapIndexed { index, config ->
            if (index != 0) {
                config
            } else {
                MediaItem.SubtitleConfiguration.Builder(config.uri)
                    .setMimeType(config.mimeType ?: MimeTypes.TEXT_VTT)
                    .setLanguage(config.language)
                    .setLabel(config.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
        }
    }

    private fun start(
        playback: RelayPlayback,
        title: String,
        episode: Int,
        positionMs: Long,
        ticket: Int,
        waitAttempt: Int = 0,
    ) {
        val client = castContext.sessionManager.currentCastSession?.remoteMediaClient
        if (client == null) {
            // Session often reports connected a beat before remoteMediaClient
            // exists. ~2s here; PlayerScreen retries while still connecting.
            // Do not publish yet — keep the settled allowlist on the relay.
            if (waitAttempt >= 16 || closed.get() || ticket != generation.get()) {
                error("Chromecast is not connected")
            }
            Log.i(TAG, "Cast start deferred; waiting for remoteMediaClient ($waitAttempt)")
            main.postDelayed({
                if (closed.get() || ticket != generation.get()) return@postDelayed
                runCatching {
                    start(playback, title, episode, positionMs, ticket, waitAttempt + 1)
                }.onFailure {
                    val stillMine = ticket == generation.get()
                    abortLoad(ticket, mediaCommitted = false)
                    if (stillMine) {
                        fireLoadError(it.message ?: "Cast failed")
                    }
                }
            }, 120L)
            return
        }
        synchronized(relayLock) { relay.publish(playback) }
        val subs = subtitleConfigurations(playback.subtitles)
        val item = MediaItem.Builder()
            .setUri(playback.url)
            .setMimeType(
                if (playback.mimeType.contains("mpegurl", ignoreCase = true)) {
                    MimeTypes.APPLICATION_M3U8
                } else {
                    MimeTypes.VIDEO_MP4
                },
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("Episode $episode")
                    .build(),
            )
            .setSubtitleConfigurations(subs)
            .build()
        // Hand the item to CastPlayer before arming watchLoad so leftover
        // IDLE/ERROR from the previous item cannot settle this ticket.
        // Prefer a live phone clock — the frozen load timestamp rewinds after
        // a deferred remoteMediaClient wait while the phone kept playing.
        // A throw from the live lambda must not abort this ticket (start's
        // deferred path treats any failure as a cast load error).
        val seekMs = runCatching { loadLivePosition?.invoke() }
            .getOrNull()
            ?.coerceAtLeast(0L)
            ?: positionMs.coerceAtLeast(0L)
        castPlayer.setMediaItem(item, seekMs)
        castPlayer.prepare()
        castPlayer.volume = heldVolume
        castPlayer.playWhenReady = true
        mediaCommittedTicket.set(ticket)
        watchLoad(client, ticket)
        Log.i(
            TAG,
            "Cast load requested ${playback.url} subs=${subs.size} captions=${heldSubtitlesOn}",
        )
    }

    private fun pushActiveTextTracks() {
        val client = castContext.sessionManager.currentCastSession?.remoteMediaClient ?: return
        val status = client.mediaStatus ?: return
        val info = status.mediaInfo ?: return
        val all = info.mediaTracks?.toList().orEmpty()
        val text = all.filter { it.type == MediaTrack.TYPE_TEXT }
        if (text.isEmpty()) return
        val chosen = castPickTextTrackId(
            tracks = text,
            enabled = heldSubtitlesOn,
            preferLanguage = heldSubtitleLanguage,
            preferLabel = heldSubtitleLabel,
        )
        // Keep any non-text active ids (audio) the receiver may already have.
        val keep = status.activeTrackIds
            ?.filter { id -> text.none { it.id == id } }
            .orEmpty()
        val next = (keep + chosen.toList()).distinct().toLongArray()
        runCatching { client.setActiveMediaTracks(next) }
            .onFailure { Log.w(TAG, "Cast subtitle select failed", it) }
        Log.i(TAG, "Cast captions active=${next.toList()} on=${heldSubtitlesOn}")
        pushTextTrackStyle(client)
    }

    private fun pushTextTrackStyle(
        client: RemoteMediaClient? =
            castContext.sessionManager.currentCastSession?.remoteMediaClient,
    ) {
        val style = heldTextTrackStyle ?: return
        val remote = client ?: return
        runCatching { remote.setTextTrackStyle(style) }
            .onFailure { Log.w(TAG, "Cast text track style failed", it) }
    }

    /**
     * Media3 1.5 [CastPlayer.getPlayerError] is always null, so a receiver that
     * rejects the load never reaches [androidx.media3.common.Player.Listener].
     * The first idle status is the session before this load and is ignored.
     */
    private fun watchLoad(
        client: RemoteMediaClient,
        ticket: Int,
    ) {
        loadSettled.set(false)
        endedNotified.set(false)
        statusCallback?.let { previous -> runCatching { client.unregisterCallback(previous) } }
        var baselineIdle = true
        var sawPlayback = false
        val callback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                if (closed.get() || ticket != generation.get()) return
                val status = client.mediaStatus ?: return
                val started = status.playerState == MediaStatus.PLAYER_STATE_PLAYING ||
                    status.playerState == MediaStatus.PLAYER_STATE_BUFFERING ||
                    status.playerState == MediaStatus.PLAYER_STATE_PAUSED
                if (started) sawPlayback = true
                if (loadSettled.get()) {
                    // A leftover IDLE_REASON_FINISHED from the previous item is
                    // still the current status until this load leaves idle.
                    if (sawPlayback && castPlaybackFinished(status.playerState, status.idleReason)) {
                        notifyPlaybackEnded(ticket)
                    }
                    return
                }
                if (baselineIdle && status.playerState == MediaStatus.PLAYER_STATE_IDLE) {
                    baselineIdle = false
                    return
                }
                baselineIdle = false
                when (status.playerState) {
                    MediaStatus.PLAYER_STATE_PLAYING,
                    MediaStatus.PLAYER_STATE_BUFFERING,
                    MediaStatus.PLAYER_STATE_PAUSED,
                    -> settleReady(ticket)
                    MediaStatus.PLAYER_STATE_IDLE -> {
                        if (status.idleReason == MediaStatus.IDLE_REASON_ERROR) {
                            settleError("Chromecast could not play this stream", ticket)
                        }
                    }
                }
            }

            override fun onMediaError(error: MediaError) {
                if (closed.get() || ticket != generation.get()) return
                val codeText = "${error.detailedErrorCode}"
                val base = listOfNotNull(error.type, error.reason).joinToString(" ").ifBlank {
                    "Chromecast could not play this stream"
                }
                // Show the receiver's code on the phone too, so a screenshot of
                // the toast names the exact failure without needing logcat.
                val shown = if (codeText != "null" && codeText != "0") "$base · code $codeText" else base
                Log.w(
                    TAG,
                    "Cast media error type=${error.type} reason=${error.reason} code=$codeText " +
                        "url=${ticketItem.get()?.second?.url}",
                )
                settleError(shown, ticket)
            }
        }
        statusCallback = callback
        client.registerCallback(callback)
    }

    private fun notifyPlaybackEnded(ticket: Int) {
        if (!endedNotified.compareAndSet(false, true)) return
        Log.i(TAG, "Cast item finished")
        main.post {
            // A newer load owns the session. Do not end that episode.
            if (ticket != generation.get() || closed.get()) return@post
            playbackEndedListener.current?.invoke()
            serviceWatch?.onItemFinished()
        }
    }

    private fun settleReady(ticket: Int) {
        if (closed.get() || ticket != generation.get()) return
        if (!loadSettled.compareAndSet(false, true)) return
        casting = true
        activeLoadTicket.compareAndSet(ticket, 0)
        ticketItem.get()?.takeIf { it.first == ticket }?.second?.let { playingItem = it }
        settledSource = ticketSource.get()?.takeIf { it.first == ticket }?.second
        settledTicket.set(ticket)
        loadedItem = playingItem
        commitProgress()
        Log.i(TAG, "Cast playback started")
        // Receiver may ignore queue-item activeTrackIds on the first load.
        // A newer load may have replaced the hooks before this runs.
        main.post {
            if (closed.get() || ticket != generation.get()) return@post
            pushActiveTextTracks()
            pushTextTrackStyle()
            fireLoadReady()
        }
    }

    private fun settleError(message: String, ticket: Int) {
        if (!loadSettled.compareAndSet(false, true)) return
        Log.w(TAG, "Cast load failed: $message")
        val committed = mediaCommittedTicket.get() == ticket
        val stillMine = ticket == generation.get()
        abortLoad(ticket, mediaCommitted = committed)
        main.post { if (stillMine) fireLoadError(message) }
    }

    fun stopCasting() {
        generation.incrementAndGet()
        restoreEpoch.incrementAndGet()
        prepareEpoch.incrementAndGet()
        activeLoadTicket.set(0)
        loadHooks.set(null)
        disarmWatchLoad()
        runCatching { castPlayer.stop() }
        synchronized(relayLock) { relay.stop() }
        casting = false
        playingItem = null
        loadedItem = null
        settledTicket.set(0)
        settledSource = null
        mediaCommittedTicket.set(0)
        ticketItem.set(null)
        ticketSource.set(null)
        warmUrl = null
        upstreamWarmed = false
        clearProgress()
        PlaybackHostHealth.clearPlaying(CastDownloadYieldOwner)
        CastPlaybackService.demoteIfIdle()
    }

    /** Stop the relay and tell the receiver to leave, including from the shade. */
    fun endSession() {
        stopCasting()
        runCatching { castContext.sessionManager.endCurrentSession(true) }
    }

    fun release() {
        closed.set(true)
        generation.incrementAndGet()
        restoreEpoch.incrementAndGet()
        prepareEpoch.incrementAndGet()
        activeLoadTicket.set(0)
        setAvailabilityListener(null)
        setPlaybackEndedListener(null)
        disarmWatchLoad()
        runCatching { castPlayer.release() }
        synchronized(relayLock) { relay.stop() }
        runCatching { multicast?.release() }
        casting = false
        playingItem = null
        loadedItem = null
        settledTicket.set(0)
        settledSource = null
        mediaCommittedTicket.set(0)
        loadLivePosition = null
        ticketItem.set(null)
        ticketSource.set(null)
        PlaybackHostHealth.clearPlaying(CastDownloadYieldOwner)
        clearProgress()
        CastPlaybackService.demoteIfIdle()
    }

    companion object {
        fun create(context: Context): CastSessionHelper? {
            val blocked = CastStartup.reason
            if (blocked != null) {
                Log.w(TAG, "Cast helper skipped: $blocked")
                return null
            }
            val castContext = CastStartup.context
            if (castContext == null) {
                Log.w(TAG, "Cast helper skipped: context not ready")
                return null
            }
            return runCatching {
                val app = context.applicationContext
                val captionsEnabled = AtomicBoolean(true)
                val captionLanguage = java.util.concurrent.atomic.AtomicReference<String?>(null)
                val captionLabel = java.util.concurrent.atomic.AtomicReference<String?>(null)
                val castPlayer = CastPlayer(
                    castContext,
                    RelayMediaItemConverter(captionsEnabled, captionLanguage, captionLabel),
                )
                val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val multicast = wifi?.createMulticastLock("anikitty-cast-discover")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
                CastSessionHelper(
                    app,
                    castContext,
                    castPlayer,
                    CastRelay(app),
                    multicast,
                    captionsEnabled,
                    captionLanguage,
                    captionLabel,
                )
            }.onFailure { Log.w(TAG, "Cast helper failed", it) }.getOrNull()
        }
    }
}

@OptIn(UnstableApi::class)
private class RelayMediaItemConverter(
    private val captionsEnabled: AtomicBoolean,
    private val captionLanguage: java.util.concurrent.atomic.AtomicReference<String?>,
    private val captionLabel: java.util.concurrent.atomic.AtomicReference<String?>,
) : MediaItemConverter {
    private val fallback = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val local = mediaItem.localConfiguration ?: return fallback.toMediaQueueItem(mediaItem)
        val raw = local.uri.toString()
        val hls = local.mimeType?.contains("mpegurl", ignoreCase = true) == true ||
            raw.contains(".m3u8", ignoreCase = true)
        val tracks = castTextTracks(local.subtitleConfigurations)
        val active = castActiveTextTrackIds(
            configs = local.subtitleConfigurations,
            tracks = tracks,
            enabled = captionsEnabled.get() && tracks.isNotEmpty(),
            preferLanguage = captionLanguage.get(),
            preferLabel = captionLabel.get(),
        )
        // Progressive mp4 still goes through the stock converter — but we must
        // attach WebVTT MediaTracks ourselves for HLS, because the custom
        // MediaInfo path used to drop every SubtitleConfiguration.
        if (!hls) {
            val stock = fallback.toMediaQueueItem(mediaItem)
            val stockInfo = stock.media ?: return stock
            if (tracks.isEmpty()) return stock
            val info = MediaInfo.Builder(stockInfo.contentId)
                .setContentUrl(stockInfo.contentUrl ?: stockInfo.contentId)
                .setStreamType(stockInfo.streamType)
                .setContentType(stockInfo.contentType)
                .setMetadata(stockInfo.metadata)
                .setStreamDuration(stockInfo.streamDuration)
                .setMediaTracks(tracks)
                .build()
            return castQueueItem(info, active)
        }
        val content = castRelayContent(raw)
        val metadata = com.google.android.gms.cast.MediaMetadata(
            com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE,
        ).apply {
            putString(
                com.google.android.gms.cast.MediaMetadata.KEY_TITLE,
                mediaItem.mediaMetadata.title?.toString().orEmpty(),
            )
            putString(
                com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE,
                mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            )
        }
        val info = MediaInfo.Builder(content.url)
            .setContentUrl(content.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(CAST_HLS_CONTENT_TYPE)
            .setMetadata(metadata)
            .setMediaTracks(tracks)
            .apply {
                when (content.pack) {
                    "fmp4" -> {
                        setHlsSegmentFormat(HlsSegmentFormat.FMP4)
                        setHlsVideoSegmentFormat(HlsVideoSegmentFormat.FMP4)
                    }
                    "ts" -> {
                        setHlsSegmentFormat(HlsSegmentFormat.TS)
                        setHlsVideoSegmentFormat(HlsVideoSegmentFormat.MPEG2_TS)
                    }
                }
            }
            .build()
        return castQueueItem(info, active)
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = fallback.toMediaItem(mediaQueueItem)
}

/**
 * While casting, the activity sends volume keys here instead of the phone stream.
 * Direction is 1 for up, -1 for down, and 0 for the mute key.
 */
internal object CastHardwareVolume {
    @Volatile
    var onStep: ((direction: Int) -> Boolean)? = null
}

private const val TAG = "AniKittyCast"

/** Same type the relay writes on the playlist, with no charset parameter. */
internal const val CAST_HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl"

private fun castQueueItem(info: MediaInfo, textTrackIds: LongArray): MediaQueueItem {
    val builder = MediaQueueItem.Builder(info)
    castLoadActiveTrackIds(textTrackIds)?.let { builder.setActiveTrackIds(it) }
    return builder.build()
}
