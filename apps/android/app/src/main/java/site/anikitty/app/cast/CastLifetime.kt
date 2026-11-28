// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.cast

import android.content.Context

/**
 * One Chromecast session for the process. The player screen borrows it and
 * gives the listeners back. Leaving the player, or opening the next episode,
 * must not release it — that is what used to stop the TV.
 */
internal object CastLifetime {
    private val gate = Any()
    private var helper: CastSessionHelper? = null

    fun obtain(context: Context): CastSessionHelper? = synchronized(gate) {
        val existing = helper
        if (existing != null && !existing.isClosed) return existing
        val created = CastSessionHelper.create(context.applicationContext) ?: return null
        helper = created
        created
    }

    fun current(): CastSessionHelper? = synchronized(gate) {
        helper?.takeIf { !it.isClosed }
    }
}

/**
 * The foreground service stays up while the TV is playing or a load is still
 * handing the stream to the receiver. Which screen is open is not an input.
 */
internal fun castForegroundShouldRun(casting: Boolean, loadInFlight: Boolean): Boolean =
    casting || loadInFlight

/**
 * The last screen leaving after the receiver finished should stop the relay.
 * Another open screen, or a load that has not settled, keeps the session.
 */
internal fun castShouldStopWhenScreenDetaches(
    screensLeft: Int,
    episodeEnded: Boolean,
    loadInFlight: Boolean,
): Boolean = screensLeft == 0 && episodeEnded && !loadInFlight

/**
 * The screen that is leaving must not clear a listener the next screen just set.
 */
internal class CastListenerSlot<T> {
    var current: T? = null
        private set

    fun set(listener: T?) {
        current = listener
    }

    fun clear(listener: T): Boolean {
        if (current !== listener) return false
        current = null
        return true
    }
}

/** Identity of the item the receiver is already playing. */
internal data class CastItemKey(
    val anilistId: Int,
    val episode: Int,
    val url: String,
    val audio: String,
    /** Picture height ceiling in pixels. 0 = Auto (receiver ABR). */
    val height: Int = 0,
)

internal fun castAudioKey(audio: String?): String = audio?.trim()?.lowercase().orEmpty()

/** Normalize a Quality chip into the Cast item key. 0 = Auto. */
internal fun castHeightKey(height: Int?): Int = height?.takeIf { it > 0 } ?: 0

/**
 * Height match for Cast identity. Exact match always wins. [adoptAuto] lets a
 * phone that opened on Auto (height 0) join a TV already locked to 720p/1080p
 * without forcing a reload that would drop the theater ceiling.
 */
internal fun castHeightMatches(
    loadedHeight: Int,
    screenHeight: Int,
    adoptAuto: Boolean = false,
): Boolean {
    val loaded = castHeightKey(loadedHeight)
    val screen = castHeightKey(screenHeight)
    if (loaded == screen) return true
    return adoptAuto && screen == 0 && loaded > 0
}

internal fun castSameItem(
    loaded: CastItemKey?,
    screen: CastItemKey,
    adoptAutoHeight: Boolean = false,
): Boolean {
    if (loaded == null || loaded.url.isBlank() || screen.url.isBlank()) return false
    if (loaded.url != screen.url || loaded.episode != screen.episode) return false
    if (castAudioKey(loaded.audio) != castAudioKey(screen.audio)) return false
    if (!castHeightMatches(loaded.height, screen.height, adoptAuto = adoptAutoHeight)) return false
    if (loaded.anilistId > 0 && screen.anilistId > 0 && loaded.anilistId != screen.anilistId) return false
    return true
}

/** Quality chip id for a settled Cast height lock. */
internal fun castVideoIdForHeight(height: Int): String {
    val h = castHeightKey(height)
    return if (h > 0) "${h}p" else "auto"
}

internal enum class CastJoin { Load, Adopt }

/** A screen that opens onto the item already on the TV joins it. It does not reload. */
internal fun castJoin(casting: Boolean, loadInFlight: Boolean, sameItem: Boolean): CastJoin =
    if (sameItem && (casting || loadInFlight)) CastJoin.Adopt else CastJoin.Load

/**
 * An aborted follow-up load must not wipe the settled episode still on the TV.
 * Only the ticket that actually started playback may release yield / playingItem.
 * [mediaCommitted] means CastPlayer already took the new item — previous episode
 * is gone, so keep is false.
 */
internal fun castKeepPlayingAfterDrop(
    settledTicket: Int,
    abortedTicket: Int,
    playingItem: CastItemKey?,
    mediaCommitted: Boolean = false,
): Boolean = !mediaCommitted &&
    playingItem != null &&
    settledTicket != 0 &&
    settledTicket != abortedTicket

/** Join/adopt keys off loadedItem — restore the settled episode after a keep. */
internal fun castLoadedAfterKeep(
    keep: Boolean,
    playingItem: CastItemKey?,
): CastItemKey? = if (keep) playingItem else null

/** Late keep-path publish must not overwrite a newer load's relay allowlist. */
internal fun castRelayRestoreMayPublish(
    restoreEpoch: Int,
    currentEpoch: Int,
    expectedSettled: Int,
    settledTicket: Int,
    activeLoadTicket: Int,
    hasPlayingItem: Boolean,
): Boolean = restoreEpoch == currentEpoch &&
    expectedSettled != 0 &&
    expectedSettled == settledTicket &&
    activeLoadTicket == 0 &&
    hasPlayingItem

/** Check before prepare — cancelled epochs must not mutate the live relay. */
internal fun castRelayRestoreMayPrepare(
    restoreEpoch: Int,
    currentEpoch: Int,
    expectedSettled: Int,
    settledTicket: Int,
    activeLoadTicket: Int,
    closed: Boolean,
): Boolean = !closed &&
    castRelayRestoreMayPublish(
        restoreEpoch = restoreEpoch,
        currentEpoch = currentEpoch,
        expectedSettled = expectedSettled,
        settledTicket = settledTicket,
        activeLoadTicket = activeLoadTicket,
        hasPlayingItem = true,
    )

/** prepareClaim must still match after I/O — stopCasting bumps the epoch without waiting. */
internal fun castRelayPrepareStillOwns(claim: Int, prepareEpoch: Int, closed: Boolean): Boolean =
    !closed && claim == prepareEpoch

/** Warm cache hits only when the cast load URL is unchanged. */
internal fun castWarmMatches(warmUrl: String?, nextUrl: String): Boolean {
    if (warmUrl.isNullOrBlank() || nextUrl.isBlank()) return false
    return warmUrl == nextUrl
}

/**
 * Warm may [CastRelay.bindTransport] only when it cannot stomp a live settled
 * episode's unwrap factories. A pending switch after a failed follow-up must
 * wait for [CastSessionHelper.load] → prepare under [prepareGate].
 *
 * [settledUrl] is authoritative even when [casting] still reads false (IO thread
 * vs [settleReady] / stop teardown ordering). [casting] with a blank settled
 * URL covers the [settleReady] window that flips casting before writing settled.
 */
internal fun castWarmMayBind(
    casting: Boolean,
    settledUrl: String?,
    nextUrl: String,
    loadInFlight: Boolean,
): Boolean {
    if (nextUrl.isBlank()) return false
    if (loadInFlight) return false
    if (!settledUrl.isNullOrBlank()) {
        return castWarmMatches(settledUrl, nextUrl)
    }
    // settleReady sets casting before settledSource — refuse until settled lands.
    if (casting) return false
    return true
}

/**
 * Theater status while Cast is connecting — surface warm work so the gap
 * before the TV starts is not a frozen "Connecting…" spinner.
 */
internal fun castTheaterWarmHint(
    pending: Boolean,
    connecting: Boolean,
    warmReady: Boolean,
): String? = when {
    !pending -> null
    warmReady -> "Starting on your TV…"
    connecting -> "Warming stream…"
    else -> "Connecting to your TV"
}

/**
 * A discarded prepare may have reopened the socket after [CastRelay.stop].
 * Tear down only when closed, or when our claim is stale and no newer load
 * owns the relay. A follow-up [CastSessionHelper.load] bumps prepareEpoch and
 * sets activeLoadTicket — stopping then kills its live server (publish does
 * not restart it).
 */
internal fun castRelayDiscardShouldStop(
    claim: Int,
    prepareEpoch: Int,
    closed: Boolean,
    activeLoadTicket: Int,
): Boolean {
    if (closed) return true
    if (claim == prepareEpoch) return false
    return activeLoadTicket == 0
}

/** The playback service, not the player, hears session end and item end. */
internal interface CastServiceWatch {
    fun onSessionEnded()
    fun onItemFinished()
}

/** Screen callbacks for one load. Clearing the token drops them after the screen is gone. */
internal class CastLoadHooks(
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
) {
    fun ready() = onReady()
    fun failed(message: String) = onError(message)
}
