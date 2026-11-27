// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package site.anikitty.app.ui.player

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.util.Rational
import android.widget.Toast
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Settings
import androidx.media3.cast.SessionAvailabilityListener
import site.anikitty.app.cast.CastHardwareVolume
import site.anikitty.app.cast.CastItemKey
import site.anikitty.app.cast.CastJoin
import site.anikitty.app.cast.CastLifetime
import site.anikitty.app.cast.CastLoadHooks
import site.anikitty.app.cast.CastPlaybackService
import site.anikitty.app.cast.CastProgressTarget
import site.anikitty.app.cast.CastSessionHelper
import site.anikitty.app.cast.CastStartup
import site.anikitty.app.cast.CAST_UNAVAILABLE
import site.anikitty.app.cast.castAudioKey
import site.anikitty.app.cast.castCaptionKey
import site.anikitty.app.cast.castShouldPushCaptions
import site.anikitty.app.cast.castJoin
import site.anikitty.app.cast.castSameItem
import site.anikitty.app.cast.castAttachPositionMs
import site.anikitty.app.cast.castDisconnectResumeMs
import site.anikitty.app.cast.castMayCorrectReceiver
import site.anikitty.app.cast.castPhoneKnownPosition
import site.anikitty.app.cast.castPhoneResumeTarget
import site.anikitty.app.cast.castPhoneShouldStayQuiet
import site.anikitty.app.cast.releaseCastHandoff
import site.anikitty.app.cast.castClientRetryDelayMs
import site.anikitty.app.cast.reduceCastAttach
import site.anikitty.app.cast.reduceCastGiveUp
import site.anikitty.app.cast.safeSeekTarget
import site.anikitty.app.ui.components.EpisodeSlide
import site.anikitty.app.ui.theme.KittyDarkPalette
import site.anikitty.app.ui.theme.KittyHaptics
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.theme.LookState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import site.anikitty.app.AnikittyApp
import site.anikitty.app.data.download.DOWNLOAD_USER_AGENT
import site.anikitty.app.data.download.EpisodeDownloadEntity
import site.anikitty.app.data.download.EpisodeDownloads
import site.anikitty.app.data.download.cacheDataSourceFactory
import site.anikitty.app.data.download.DownloadPhase
import site.anikitty.app.data.download.downloadStatus
import site.anikitty.app.data.download.encodeHeaders
import site.anikitty.app.data.download.hlsStreamKeys
import site.anikitty.app.data.download.playbackSavedEpisode
import site.anikitty.app.data.download.resolvedStripBytes
import site.anikitty.app.data.download.savedLocalFile
import site.anikitty.app.data.download.savedMatches
import site.anikitty.app.data.download.shouldRefreshSavedUrl
import site.anikitty.app.data.download.sourceForPlayback
import site.anikitty.app.data.download.sourceKey
import site.anikitty.app.data.download.toSaved
import site.anikitty.app.data.download.usesHlsCache
import site.anikitty.app.data.friendlyFailure
import site.anikitty.app.data.servers.GotakuObfDataSource
import site.anikitty.app.data.servers.FlixUnwrapDataSource
import site.anikitty.app.data.servers.PlaybackHttp
import site.anikitty.app.data.servers.PlayableSource
import site.anikitty.app.data.servers.SegmentStripDataSource
import site.anikitty.app.data.servers.audioSwitchLabel
import site.anikitty.app.data.servers.requestedAudioLanguage
import site.anikitty.app.data.servers.SubtitleTrack
import site.anikitty.app.data.servers.SubtitleLibrary
import site.anikitty.app.data.skip.AniSkipStamp
import site.anikitty.app.data.skip.SkipTimes
import site.anikitty.app.data.skip.chooseSkip
import site.anikitty.app.data.skip.malIdFromTitleHint
import site.anikitty.app.ui.theme.KittyInk
import site.anikitty.app.ui.app.spacePress
import site.anikitty.app.ui.tv.awaitTvFocus
import site.anikitty.app.ui.tv.isTelevision
import site.anikitty.app.ui.theme.PlayerChrome
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Soft “next episode?” offer when this much remains. */
internal const val NEAR_END_THRESHOLD_MS = 90_000L
/** Ignore tiny clips / trailers for the near-end offer. */
private const val NEAR_END_MIN_DURATION_MS = 120_000L
internal const val AUTOPLAY_COUNTDOWN_SEC = 10

/** Caption backdrop: none, soft tint, solid box, or frosted/blurred glass look. */
private enum class SubBgMode { None, Soft, Solid, Blur }

/**
 * Fully user-controlled caption look.
 * [sizeSp] is the real text size in sp — no forced “large” floor.
 * [bottomPad] is Media3 bottom padding fraction — higher lifts captions up.
 */
private data class SubStyle(
    val sizeSp: Float = DEFAULT_SUB_SIZE_SP,
    val bottomPad: Float = DEFAULT_SUB_BOTTOM_PAD,
    val color: Int = AndroidColor.rgb(242, 242, 242),
    val bgMode: SubBgMode = SubBgMode.None,
    val edgeType: Int = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
    val bold: Boolean = false,
    val italic: Boolean = true,
    val bgAlpha: Float = 0.55f,
)

private data class TextOption(
    val id: String,
    val label: String,
    /** null = off/auto (no hard override) */
    val mediaGroup: TrackGroup? = null,
    val trackIndex: Int = 0,
)

/** One audio track inside the current file. */
internal data class AudioOption(
    val id: String,
    val label: String,
    val mediaGroup: TrackGroup? = null,
    val trackIndex: Int = 0,
    val selectedByPlayer: Boolean = false,
)

/** HLS / progressive video variants the user can lock (1080p, 720p, …). */
private data class VideoOption(
    val id: String,
    val label: String,
    val height: Int = 0,
    val width: Int = 0,
    val mediaGroup: TrackGroup? = null,
    val trackIndex: Int = 0,
)

/** One prompt per process. A denial must not ask again on every episode. */
private var askedCastNotificationPermission = false

/**
 * Full-bleed anime player with custom chrome:
 * immersive fullscreen, quality + subtitle pickers, caption style, skip chips.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    title: String,
    episode: Int,
    sources: List<PlayableSource>,
    onRefreshSources: (suspend () -> List<PlayableSource>)? = null,
    anilistId: Int = 0,
    titleHint: String? = null,
    loadAniSkip: suspend (malId: Int, episode: Int) -> List<AniSkipStamp> =
        { _, _ -> emptyList() },
    startPositionMs: Long = 0L,
    onClose: () -> Unit,
    onDismiss: () -> Unit = onClose,
    onToggleWatched: ((Int) -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    /** Fire once past 72% so the next episode's CDN race is already warm. */
    onWarmNextEpisode: (suspend () -> Unit)? = null,
    onProgress: ((positionMs: Long, durationMs: Long, skip: SkipTimes) -> Unit)? = null,
    totalEpisodes: Int = 1,
    watchedThrough: Int = 0,
    audio: String = "sub",
    isMovie: Boolean = false,
    onPickEpisode: ((Int) -> Unit)? = null,
    onPickAudio: ((String) -> Unit)? = null,
    episodeSlides: List<EpisodeSlide> = emptyList(),
    fallbackArt: String? = null,
    stageModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val displaySlides = site.anikitty.app.ui.components.rememberEnrichedEpisodeSlides(
        anilistId = anilistId,
        title = title,
        isMovie = isMovie,
        totalEpisodes = totalEpisodes,
        slides = episodeSlides,
        seriesCoverUrl = fallbackArt,
        titleHint = titleHint,
    )
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var chromeFocused by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    var playAttempt by remember { mutableIntStateOf(0) }
    var playlist by remember(sources) { mutableStateOf(sources) }
    var sourceIndex by remember(playlist, playAttempt) { mutableIntStateOf(0) }
    /** Hosts that already failed this episode. The next failover skips them. */
    var deadHosts by remember(anilistId, episode) { mutableStateOf(emptySet<String>()) }
    var retriedSource by remember(sourceIndex, playlist) { mutableStateOf(false) }
    var skipTimes by remember { mutableStateOf(SkipTimes()) }
    var didSeekStart by remember { mutableStateOf(false) }
    var resumeAtMs by remember { mutableLongStateOf(startPositionMs) }
    /** True until the first successful resume seek or a user quality pick. */
    var allowHistoryFallback by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(PlayerMenuPage.None) }
    /** When true, Quality sheet leads with Sources (opened from the Source chip). */
    var sourcePanelFocus by remember { mutableStateOf(false) }
    LaunchedEffect(panel) {
        if (panel != PlayerMenuPage.Quality) sourcePanelFocus = false
    }
    var episodeJumpFocus by remember { mutableStateOf(false) }
    var immersive by remember { mutableStateOf(true) }
    var resizeMode by remember {
        mutableIntStateOf(
            context.getSharedPreferences("anikitty_player", 0)
                .getInt("resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FIT),
        )
    }
    var subStyle by remember {
        mutableStateOf(
            loadSubStyle(context.getSharedPreferences("anikitty_player", 0)),
        )
    }
    var subsEnabled by remember { mutableStateOf(true) }
    var selectedTextId by remember { mutableStateOf("auto") }
    var textOptions by remember { mutableStateOf<List<TextOption>>(emptyList()) }
    var selectedVideoId by remember { mutableStateOf("auto") }
    var renderedHeight by remember { mutableIntStateOf(0) }
    var videoOptions by remember { mutableStateOf<List<VideoOption>>(emptyList()) }
    var selectedAudioId by remember { mutableStateOf("auto") }
    var audioOptions by remember { mutableStateOf<List<AudioOption>>(emptyList()) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val playerPrefs = remember { context.getSharedPreferences("anikitty_player", 0) }
    /** Skip intros, outros, and previews automatically once their marker is active. */
    var autoSkip by remember { mutableStateOf(playerPrefs.getBoolean("auto_skip", false)) }
    /** One-time hint explaining the seek/volume/brightness touch gestures. */
    val gestureHintEligible = !isTelevision()
    var showGestureHint by remember {
        mutableStateOf(gestureHintEligible && !playerPrefs.getBoolean("gesture_hint_seen", false))
    }
    var playbackSpeed by remember {
        mutableFloatStateOf(playerPrefs.getFloat("playback_speed", 1f).coerceIn(0.5f, 2f))
    }
    var volumeBoost by remember {
        mutableIntStateOf(playerPrefs.getInt("volume_boost", 0).coerceIn(0, 3))
    }
    val loudnessHold = remember { LoudnessHold() }
    var autoNext by remember { mutableStateOf(playerPrefs.getBoolean("auto_next", true)) }
    var holdBoost by remember { mutableStateOf(false) }
    /** A center hold already used this gesture, so the play disc must not toggle on release. */
    var swallowCenterClick by remember { mutableStateOf(false) }
    var gestureHud by remember { mutableStateOf<GestureHud?>(null) }
    var gestureDragging by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    /** Signed seek HUD total — stacks on rapid double-taps (e.g. +20s). */
    var seekFlashMs by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(false) }
    var isCasting by remember { mutableStateOf(false) }
    var castHint by remember { mutableStateOf<String?>(null) }
    /** A Chromecast is up, but the episode stream was not ready to load yet. */
    var castPending by remember { mutableStateOf(false) }
    /** The current session already loaded this source, so a later callback does not restart it. */
    var castAttachStarted by remember { mutableStateOf(false) }
    /** Identity of the load already sent. A quality, audio, or failover change replaces it. */
    var castLoadedKey by remember { mutableStateOf<String?>(null) }
    /** Playhead passed to the last cast load. A saved resume that arrives later can move past it. */
    var castSentPositionMs by remember { mutableLongStateOf(-1L) }
    var castPlayheadRequested by remember { mutableStateOf(false) }
    /** The receiver clock still belongs to the previous episode. */
    var castClockStale by remember { mutableStateOf(false) }
    /** Bumped when a load starts before the receiver client exists, so attach retries. */
    var castClientRetry by remember { mutableIntStateOf(0) }
    /** One automatic re-load per episode: the first cast attempt often races the receiver. */
    var castLoadRetry by remember { mutableIntStateOf(0) }
    /** The receiver client never appeared. Further retries stay off and the phone plays. */
    var castClientGaveUp by remember { mutableStateOf(false) }
    /** This screen joined a receiver that was already playing this episode. */
    var castJoinedLive by remember { mutableStateOf(false) }
    val castAlive = remember { AtomicBoolean(true) }
    val castLoadHooks = remember { AtomicReference<CastLoadHooks?>(null) }
    // Next-episode UX: soft offer near the end, then 10s autoplay once ended.
    var nearEndDismissed by remember(episode) { mutableStateOf(false) }
    var playbackEnded by remember(episode) { mutableStateOf(false) }
    var autoplayCancelled by remember(episode) { mutableStateOf(false) }
    var autoplaySecLeft by remember(episode) { mutableIntStateOf(AUTOPLAY_COUNTDOWN_SEC) }
    var nextTriggered by remember(episode) { mutableStateOf(false) }
    /** 0 off, positive minutes, -1 pause when this episode ends. */
    var sleepChoice by remember { mutableIntStateOf(0) }
    var sleepArmedAt by remember { mutableLongStateOf(0L) }
    var sleepTick by remember { mutableIntStateOf(0) }
    var sleepNote by remember { mutableStateOf<String?>(null) }

    val source = playlist.getOrNull(sourceIndex)
    // The source's own captions are the captions: whatever the provider
    // reports is what plays. The subtitle library API is no longer used.
    var librarySubtitles by remember(anilistId, episode) {
        mutableStateOf(source?.subtitles.orEmpty())
    }
    var captionsLoading by remember(anilistId, episode) { mutableStateOf(false) }
    val downloadDao = remember {
        (context.applicationContext as AnikittyApp).database.episodeDownloadDao()
    }
    var savedRow by remember(anilistId, episode) { mutableStateOf<EpisodeDownloadEntity?>(null) }
    var downloadSeen by remember(anilistId, episode) { mutableStateOf(false) }
    LaunchedEffect(anilistId, episode) {
        downloadDao.observe(anilistId, episode).collect { row ->
            savedRow = row
            downloadSeen = true
        }
    }
    val sourceKeyNow = source?.let(::sourceKey)
    var sessionStartedOffline by remember(anilistId, episode, sourceKeyNow) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(downloadSeen, anilistId, episode, sourceKeyNow) {
        if (!downloadSeen || source == null || sessionStartedOffline != null) return@LaunchedEffect
        val saved = savedRow?.toSaved()
        sessionStartedOffline = saved != null &&
            savedMatches(saved, source) &&
            saved.phase == DownloadPhase.READY &&
            (saved.hls || savedLocalFile(source, saved) != null)
    }
    LaunchedEffect(savedRow?.url, savedRow?.phase, source?.url) {
        val row = savedRow ?: return@LaunchedEffect
        val current = source ?: return@LaunchedEffect
        val saved = row.toSaved()
        if (!shouldRefreshSavedUrl(current, saved)) return@LaunchedEffect
        downloadDao.refreshReadyFileUrl(
            anilistId = row.anilistId,
            episode = row.episode,
            sourceKey = row.sourceKey,
            url = current.url,
            headersJson = encodeHeaders(current.headers),
        )
    }
    val playbackSaved = if (downloadSeen && sessionStartedOffline != null && source != null) {
        playbackSavedEpisode(source, savedRow?.toSaved(), sessionStartedOffline == true)
    } else {
        null
    }
    val playbackReady = sessionStartedOffline != null
    val downloadScope = rememberCoroutineScope()
    val download = if (anilistId > 0) source?.let { downloadStatus(it, savedRow?.toSaved()) } else null
    var castUnavailable by remember { mutableStateOf(CastStartup.reason) }
    var castHelper by remember { mutableStateOf<CastSessionHelper?>(null) }
    val castRouteReason = castUnavailable ?: if (castHelper == null) CAST_UNAVAILABLE else null
    DisposableEffect(Unit) {
        val stop = CastStartup.listen { next ->
            castUnavailable = next
            if (next == null && castHelper == null) {
                castHelper = CastLifetime.obtain(context.applicationContext)
            }
        }
        onDispose(stop)
    }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            Log.i("AniKittyCast", "Notification permission granted")
        } else {
            Log.w("AniKittyCast", "Notification permission denied")
            castHint = "Notifications are off, so the Cast notice may stay hidden"
        }
    }
    // The permission prompt fires on the first Cast press, not when the
    // player opens: a dialog over playback right at open used to interrupt
    // the first cast attempt.
    fun ensureCastNotifications() {
        if (Build.VERSION.SDK_INT < 33 || askedCastNotificationPermission) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        askedCastNotificationPermission = true
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val television = isTelevision()
    var phoneTheater by remember { mutableStateOf(playerPrefs.getBoolean("start_theater", false)) }
    val theater = playerOpensInTheater(television, phoneTheater)
    fun leaveTheater() {
        if (leavingTheaterClosesPlayer(television)) onClose() else phoneTheater = false
    }
    fun enterTheater() {
        phoneTheater = true
    }
    val menuFocusGate = remember(panel) {
        if (television && panel != PlayerMenuPage.None) MenuFocusGate() else null
    }
    /** Inline video chrome. A tap reveals it; playing hides it again. */
    var stageChrome by remember(episode) { mutableStateOf(false) }
    var showAudioSwitch by remember { mutableStateOf(false) }
    LaunchedEffect(audio, source?.audioLanguage) {
        if (audioSwitchLabel(audio, source?.audioLanguage) == null) {
            showAudioSwitch = false
            return@LaunchedEffect
        }
        showAudioSwitch = true
        delay(12_000)
        showAudioSwitch = false
    }
    LaunchedEffect(stageChrome, playing, theater) {
        if (!stageChrome || !playing || theater) return@LaunchedEffect
        delay(2_800)
        stageChrome = false
    }
    var muted by remember { mutableStateOf(false) }

    fun closePanelOrExit() {
        when (panel) {
            PlayerMenuPage.None -> if (theater) leaveTheater() else onClose()
            PlayerMenuPage.Root -> panel = PlayerMenuPage.None
            else -> panel = PlayerMenuPage.Root
        }
    }

    BackHandler(onBack = { closePanelOrExit() })

    DisposableEffect(theater) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = playerRequestedOrientation(television, theater)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(activity) {
        val previous = activity?.window?.attributes?.screenBrightness ?: -1f
        onDispose {
            val host = activity ?: return@onDispose
            host.window.attributes = host.window.attributes.apply { screenBrightness = previous }
        }
    }

    DisposableEffect(immersive, activity, view) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, view)
            if (immersive && theater) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                // Keep edge-to-edge (MainActivity.enableEdgeToEdge); only restore bars.
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val playbackSource = source?.let { sourceForPlayback(it, playbackSaved) }
    val playbackLocal = playbackSource?.url?.let { url ->
        url.startsWith("file:") || url.startsWith("android.resource:") || url.startsWith("content:")
    } == true
    val playbackHlsOffline = source != null && usesHlsCache(source, playbackSaved)
    // The player lives for the episode. A dead CDN swaps the media item in
    // this instance so the surface, audio session, and playhead stay put.
    val playerBundle = remember(
        anilistId,
        episode,
        playAttempt,
        playbackReady,
        playbackLocal,
        playbackHlsOffline,
        if (playbackHlsOffline) playbackSaved?.hlsStreams else null,
    ) {
        if (!playbackReady) return@remember null
        val pipeline = PlaybackPipeline()
        playbackSource?.let(pipeline::apply)
        val topFactory = when {
            playbackLocal -> DefaultDataSource.Factory(context)
            playbackHlsOffline -> cacheDataSourceFactory(context, anilistId, episode, pipeline.strip)
            else -> pipeline.strip
        }
        // Media3 requires minBufferMs >= bufferForPlaybackAfterRebufferMs
        // and minBufferMs >= bufferForPlaybackMs. Violating that crashes on open.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 3_200,
                /* maxBufferMs = */ 55_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 2_200,
            )
            .setBackBuffer(36_000, true)
            .build()
        val metrics = context.resources.displayMetrics
        val seedBitrate = context.getSharedPreferences("anikitty_player", 0)
            .getInt("last_video_bitrate", 1_500_000)
            .coerceIn(400_000, 8_000_000)
        val bandwidth = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(seedBitrate.toLong())
            .build()
        // Flagship ABR: climb on healthy pipe, dump rungs the second stalls hit.
        val adaptive = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 5_500,
            /* maxDurationForQualityDecreaseMs = */ 2_800,
            /* minDurationToRetainAfterDiscardMs = */ 14_000,
            /* bandwidthFraction = */ 0.78f,
        )
        val selector = DefaultTrackSelector(context, adaptive).apply {
            // Device locale is the default preference. A Danish phone then keeps
            // the Japanese DEFAULT rendition on a shared sub/dub master.
            // The viewport keeps a phone from requesting a 4K rung it cannot show.
            parameters = buildUponParameters()
                .setViewportSize(metrics.widthPixels, metrics.heightPixels, true)
                .setPreferredAudioLanguage(requestedAudioLanguage(audio))
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(topFactory)
                    .setLoadErrorHandlingPolicy(FastFailLoadPolicy()),
            )
            .setLoadControl(loadControl)
            .setTrackSelector(selector)
            .setBandwidthMeter(bandwidth)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                playbackParameters = PlaybackParameters(playbackSpeed)
            }
        PlayerBundle(exo, selector, pipeline)
    }
    val player = playerBundle?.player
    val trackSelector = playerBundle?.trackSelector
    // The video is already playing. Captions arrive on their own and are added
    // without building a new player.
    LaunchedEffect(anilistId, episode, source?.url) {
        librarySubtitles = source?.subtitles.orEmpty()
        captionsLoading = false
    }
    LaunchedEffect(player, librarySubtitles, source?.url) {
        val exo = player ?: return@LaunchedEffect
        val tracks = librarySubtitles
        if (tracks.isEmpty()) return@LaunchedEffect
        val current = exo.currentMediaItem ?: return@LaunchedEffect
        val configs = tracks.mapIndexedNotNull { index, track ->
            subtitleConfig(track, default = index == 0)
        }
        if (configs.isEmpty()) return@LaunchedEffect
        val already = current.localConfiguration?.subtitleConfigurations.orEmpty()
        if (already.map { it.uri } == configs.map { it.uri }) return@LaunchedEffect
        // Replacing the item drops the buffered segments. While the stream is
        // still coming up, that would restart the wait the viewer is already in.
        var guard = 0
        while (guard < 40 && exo.playbackState == Player.STATE_BUFFERING && !exo.isPlaying) {
            delay(250)
            guard++
        }
        val resume = exo.playWhenReady
        val playing = source?.let { sourceForPlayback(it, playbackSaved) }
        if (playing != null) playerBundle?.pipeline?.apply(playing)
        exo.setMediaItem(current.buildUpon().setSubtitleConfigurations(configs).build(), false)
        exo.prepare()
        exo.playWhenReady = resume
    }
    // Kaa uses one master for Sub and Dub, so the player is not rebuilt.
    // The preference still has to move onto the English or Japanese rendition.
    // A leftover in-file override outranks that preference, so Auto clears it.
    LaunchedEffect(trackSelector, audio, selectedAudioId) {
        val selector = trackSelector ?: return@LaunchedEffect
        val next = selector.buildUponParameters()
            .setPreferredAudioLanguage(requestedAudioLanguage(audio))
        selector.parameters = if (selectedAudioId == "auto") {
            next.clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        } else {
            next.build()
        }
    }
    val activePlayer: Player? = if (isCasting) castHelper?.castPlayer else player
    val latestPlayer by rememberUpdatedState(player)
    val phoneQuietNow = castPhoneShouldStayQuiet(
        casting = isCasting,
        handoff = castAttachStarted && !isCasting && !castClientGaveUp,
        pending = castPending && !castClientGaveUp,
    )
    // Keep the phone paused for the whole handoff, including the gap before
    // the receiver reports that it is playing.
    LaunchedEffect(player, phoneQuietNow) {
        if (!phoneQuietNow) return@LaunchedEffect
        runCatching {
            player?.pause()
            player?.playWhenReady = false
        }
        if (!isCasting) controlsVisible = true
    }
    val latestActive by rememberUpdatedState(activePlayer)
    val latestSources by rememberUpdatedState(playlist)
    val latestSourceIndex by rememberUpdatedState(sourceIndex)
    val latestTitle by rememberUpdatedState(title)
    val latestEpisode by rememberUpdatedState(episode)
    val latestStartPositionMs by rememberUpdatedState(startPositionMs)
    val latestAllowHistory by rememberUpdatedState(allowHistoryFallback)
    val playhead = remember { Playhead() }
    // Same player, next URL. setMediaItem(item, position) opens at the playhead
    // that existed when the previous CDN died, instead of preparing at zero
    // and hoping a later seek still has a duration.
    LaunchedEffect(
        player,
        playbackSource?.url,
        playbackSource?.headers,
        playbackSource?.gotakuObf,
        playbackSource?.flix,
        playbackSource?.hls,
        playbackSource?.subtitles,
        playbackHlsOffline,
        playbackSaved?.hlsStreams,
    ) {
        val exo = player ?: return@LaunchedEffect
        val playing = playbackSource ?: return@LaunchedEffect
        val bundle = playerBundle ?: return@LaunchedEffect
        bundle.pipeline.apply(playing)
        val start = resumeAtMs.coerceAtLeast(0L)
        val itemBuilder = MediaItem.Builder().setUri(playing.url)
        hlsStreamKeys(if (playbackHlsOffline) playbackSaved?.hlsStreams else null)
            .takeIf { it.isNotEmpty() }
            ?.let(itemBuilder::setStreamKeys)
        if (playing.hls || playing.url.contains(".m3u8", ignoreCase = true) || playing.gotakuObf != null) {
            itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        val configs = playing.subtitles.mapIndexedNotNull { index, track ->
            subtitleConfig(track, default = index == 0)
        }
        if (configs.isNotEmpty()) itemBuilder.setSubtitleConfigurations(configs)
        val quiet = castPhoneShouldStayQuiet(
            casting = isCasting,
            handoff = castAttachStarted && !isCasting && !castClientGaveUp,
            pending = castPending && !castClientGaveUp,
        )
        exo.setMediaItem(itemBuilder.build(), start)
        exo.prepare()
        exo.playWhenReady = !quiet
        if (start > 1_500L) {
            positionMs = start
            playhead.ms = start
        }
    }
    val playingOffline = source?.let { savedLocalFile(it, playbackSaved) } != null ||
        (source != null && usesHlsCache(source, playbackSaved))
    var wasOffline by remember(anilistId, episode) { mutableStateOf(false) }
    LaunchedEffect(playingOffline, playbackReady) {
        if (wasOffline && !playingOffline && playbackReady) {
            val live = maxOf(positionMs, playhead.ms)
            if (live > 1_500L) {
                resumeAtMs = live
                didSeekStart = false
            }
        }
        wasOffline = playingOffline
    }
    val latestPosition = playhead
    val latestOnNextEpisode by rememberUpdatedState(onNextEpisode)
    val latestOnProgress by rememberUpdatedState(onProgress)
    val latestSkip by rememberUpdatedState(skipTimes)
    val latestLibrarySubtitles by rememberUpdatedState(librarySubtitles)
    val latestSubsEnabled by rememberUpdatedState(subsEnabled)
    val latestSelectedTextId by rememberUpdatedState(selectedTextId)
    val latestTextOptions by rememberUpdatedState(textOptions)
    val mutedNow = rememberUpdatedState(muted)

    fun syncCastSubtitles() {
        val helper = castHelper ?: return
        val enabled = latestSubsEnabled && latestSelectedTextId != "off"
        if (!enabled) {
            helper.applySubtitles(enabled = false)
            return
        }
        val pickId = latestSelectedTextId
        val auto = pickId == "auto"
        val option = when {
            auto -> latestTextOptions.firstOrNull { it.id != "off" && it.id != "auto" }
            else -> latestTextOptions.firstOrNull { it.id == pickId }
        }
        val matched = latestLibrarySubtitles.firstOrNull { track ->
            option != null && track.label.equals(option.label, ignoreCase = true)
        }
        // Auto means English first, matching the on-device track selector. A
        // label-only row (Signs, Forced) still stays label-only.
        val englishTrack = latestLibrarySubtitles.firstOrNull { track ->
            track.language?.trim()?.substringBefore('-')?.equals("en", ignoreCase = true) == true ||
                track.label.contains("english", ignoreCase = true)
        }
        val language = when {
            auto && englishTrack != null -> "en"
            else -> matched?.language?.trim()?.takeIf { it.isNotEmpty() }
                ?: languageFromTextOptionId(option?.id ?: pickId)
        }
        val label = if (auto && englishTrack != null) englishTrack.label else option?.label ?: matched?.label
        helper.applySubtitles(enabled = true, language = language, label = label)
    }

    fun pushMute() {
        val level = if (mutedNow.value) 0f else 1f
        castHelper?.heldVolume = level
        player?.volume = level
        castHelper?.castPlayer?.volume = level
        if (isCasting) castHelper?.setTvMuted(mutedNow.value)
    }

    fun goNextEpisode() {
        if (nextTriggered) return
        val next = latestOnNextEpisode ?: return
        nextTriggered = true
        next()
    }

    fun goPreviousEpisode() {
        if (nextTriggered || episode <= 1) return
        val pick = onPickEpisode ?: return
        nextTriggered = true
        autoplayCancelled = true
        pick(episode - 1)
    }

    var nearEndOffer by remember { mutableStateOf(false) }
    var shownSkip by remember { mutableStateOf<Pair<String, site.anikitty.app.data.skip.SkipInterval>?>(null) }
    var shelfMinutes by remember { mutableIntStateOf(-1) }
    var warmedNext by remember(anilistId, episode) { mutableStateOf(false) }
    val latestWarmNext by rememberUpdatedState(onWarmNextEpisode)
    LaunchedEffect(positionMs, durationMs, warmedNext, onNextEpisode) {
        if (!shouldWarmNextEpisode(positionMs, durationMs, onNextEpisode != null, warmedNext)) {
            return@LaunchedEffect
        }
        warmedNext = true
        val warm = latestWarmNext ?: return@LaunchedEffect
        runCatching { warm() }
    }
    val showEndAutoplay = autoNext &&
        onNextEpisode != null &&
        playbackEnded &&
        !autoplayCancelled &&
        !nextTriggered &&
        sleepChoice != -1

    fun castSourceKey(): String? {
        val src = latestSources.getOrNull(latestSourceIndex) ?: return null
        return "${latestSourceIndex}\u0000${src.url}\u0000${src.audioLanguage.orEmpty()}\u0000$latestEpisode"
    }

    fun castPlayhead(): Long {
        val onScreen = latestPlayer?.currentPosition?.takeIf { it > 0L } ?: 0L
        // A joined receiver clock lives in the playhead while the phone is still
        // at the start. A later load has to see that clock, not only the phone.
        val known = maxOf(onScreen, latestPosition.ms)
        return castAttachPositionMs(
            localPositionMs = known,
            resumeAtMs = resumeAtMs,
            startPositionMs = latestStartPositionMs,
            allowHistoryFallback = latestAllowHistory,
            // An attach that started at 0 is still waiting for history. Playback
            // that ran from the start during connect must not hide that resume.
            pendingResume = !didSeekStart && latestAllowHistory && castSentPositionMs <= 1_500L,
        )
    }

    fun abandonCastToPhone(local: Player?) {
        castClientGaveUp = true
        castPending = false
        castAttachStarted = false
        castLoadedKey = null
        castHint = "Chromecast is not connected"
        isCasting = false
        pushMute()
        if (castAlive.get()) runCatching { local?.play() }
    }

    fun scheduleCastClientRetry(local: Player?) {
        val next = castClientRetry + 1
        castAttachStarted = false
        castLoadedKey = null
        if (castClientGaveUp || castClientRetryDelayMs(next) == null) {
            abandonCastToPhone(local)
        } else {
            castHint = "Connecting to your TV"
            castPending = true
            castClientRetry = next
        }
    }

    fun catchCastPlayhead(helper: CastSessionHelper, wantedMs: Long, requested: Boolean) {
        if (!castMayCorrectReceiver(requested, wantedMs, helper.receiverPositionMs())) return
        val landed = safeSeekTarget(wantedMs, helper.castPlayer.duration, durationMs)
        runCatching { helper.castPlayer.seekTo(landed) }
    }

    fun handEpisodeBackToPhone(hint: String?, receiverStillPlaying: Boolean) {
        if (!castAlive.get()) return
        val release = releaseCastHandoff(receiverStillPlaying)
        if (!release.clearHandoff) {
            castClockStale = false
            if (hint != null) castHint = hint
            return
        }
        val requested = castPlayheadRequested
        val sent = castSentPositionMs
        isCasting = false
        castAttachStarted = false
        castPending = false
        castLoadedKey = null
        castPlayheadRequested = false
        castClockStale = false
        castJoinedLive = false
        if (hint != null) castHint = hint
        pushMute()
        if (!release.playOnPhone) return
        val local = latestPlayer
        val localMs = runCatching { local?.currentPosition ?: 0L }.getOrDefault(0L)
        val known = castPhoneKnownPosition(requested, sent, positionMs, playhead.ms)
        if (requested) {
            positionMs = known
            playhead.ms = known
        }
        val resume = castPhoneResumeTarget(localMs, known)
        if (resume != null) {
            positionMs = resume
            playhead.ms = resume
            resumeAtMs = resume
            didSeekStart = false
            allowHistoryFallback = false
            if (castSentPositionMs < resume) castSentPositionMs = resume
            runCatching { local?.seekTo(resume) }
        }
        runCatching { local?.play() }
    }

    fun castReady() {
        if (!castAlive.get()) return
        castClientRetry = 0
        castLoadRetry = 0
        castClientGaveUp = false
        runCatching {
            latestPlayer?.pause()
            latestPlayer?.playWhenReady = false
        }
        pushMute()
        isCasting = true
        castHint = null
        val helper = castHelper
        val requested = castPlayheadRequested
        castPlayheadRequested = false
        if (helper != null) catchCastPlayhead(helper, castSentPositionMs, requested)
    }

    val castFailUrl = remember { arrayOfNulls<String>(1) }
    val freshResolve = remember(anilistId, episode, playAttempt) { java.util.concurrent.atomic.AtomicBoolean(false) }
    var refreshingSources by remember(anilistId, episode) { mutableStateOf(false) }

    fun recoverExhausted(message: String, resumeMs: Long, fromCast: Boolean) {
        val refresh = onRefreshSources
        if (refresh != null && freshResolve.compareAndSet(false, true)) {
            refreshingSources = true
            error = null
            resumeAtMs = resumeMs
            positionMs = resumeMs
            playhead.ms = resumeMs
            didSeekStart = false
            allowHistoryFallback = false
            downloadScope.launch {
                val fresh = kotlinx.coroutines.withTimeoutOrNull(45_000) {
                    runCatching { refresh() }.getOrNull().orEmpty()
                }.orEmpty()
                refreshingSources = false
                if (hasNewPlayableUrls(playlist.map { it.url }, fresh.map { it.url })) {
                    playlist = fresh
                } else {
                    error = message
                    if (fromCast) handEpisodeBackToPhone(message, receiverStillPlaying = false)
                }
            }
            return
        }
        error = message
        if (fromCast) handEpisodeBackToPhone(message, receiverStillPlaying = false)
    }

    fun nextIndexSkippingDead(fromCast: Boolean): Int? {
        val urls = if (fromCast) latestSources.map { it.url } else playlist.map { it.url }
        val index = if (fromCast) latestSourceIndex else sourceIndex
        val host = urls.getOrNull(index)?.let(::playbackHost).orEmpty()
        val blocked = if (host.isEmpty()) deadHosts else deadHosts + host
        if (host.isNotEmpty() && blocked != deadHosts) deadHosts = blocked
        return nextHealthySource(urls, index, blocked)
    }

    fun failCastSource(message: String, liveMs: Long) {
        val currentUrl = latestSources.getOrNull(latestSourceIndex)?.url
        if (currentUrl != null && castFailUrl[0] == currentUrl) return
        castFailUrl[0] = currentUrl
        val decision = sourceFailover(
            index = latestSourceIndex,
            count = latestSources.size,
            livePositionMs = liveMs,
            heldResumeMs = resumeAtMs,
            historyMs = latestStartPositionMs,
            allowHistory = latestAllowHistory,
        )
        val next = nextIndexSkippingDead(fromCast = true)
        if (next != null) {
            resumeAtMs = decision.resumeMs
            positionMs = decision.resumeMs
            playhead.ms = decision.resumeMs
            didSeekStart = false
            allowHistoryFallback = false
            sourceIndex = next
            return
        }
        recoverExhausted(message, decision.resumeMs, fromCast = true)
    }

    fun applyPlaybackError(target: Player?, e: PlaybackException, liveMs: Long, fromCast: Boolean) {
        val decision = sourceFailover(
            index = if (fromCast) latestSourceIndex else sourceIndex,
            count = if (fromCast) latestSources.size else playlist.size,
            livePositionMs = liveMs,
            heldResumeMs = resumeAtMs,
            historyMs = if (fromCast) latestStartPositionMs else startPositionMs,
            allowHistory = if (fromCast) latestAllowHistory else allowHistoryFallback,
        )
        val cause = e.cause?.message?.takeIf { it.isNotBlank() }
        val message = friendlyFailure(
            listOfNotNull(e.message, cause).joinToString(" — ").ifBlank { "Playback failed" },
        )
        when (sourceRecovery(e.errorCode, httpStatusOf(e), liveMs)) {
            SourceRecovery.SeekLive -> {
                runCatching {
                    target?.seekToDefaultPosition()
                    target?.prepare()
                    target?.playWhenReady = true
                }
            }
            SourceRecovery.RetrySame -> {
                if (retriedSource) {
                    error = message
                    if (fromCast) handEpisodeBackToPhone(message, receiverStillPlaying = false)
                    return
                }
                retriedSource = true
                runCatching {
                    target?.seekTo(decision.resumeMs.coerceAtLeast(0L))
                    target?.prepare()
                    target?.playWhenReady = true
                }
            }
            SourceRecovery.NextSource -> {
                if (fromCast) {
                    failCastSource(message, liveMs)
                } else if (decision.nextIndex != null) {
                    val next = nextIndexSkippingDead(fromCast = false) ?: decision.nextIndex
                    resumeAtMs = decision.resumeMs
                    positionMs = decision.resumeMs
                    playhead.ms = decision.resumeMs
                    didSeekStart = false
                    allowHistoryFallback = false
                    sourceIndex = next
                } else {
                    recoverExhausted(message, decision.resumeMs, fromCast = false)
                }
            }
            SourceRecovery.GiveUp -> {
                error = message
                if (fromCast) handEpisodeBackToPhone(message, receiverStillPlaying = false)
            }
        }
    }

    fun castFailed(message: String) {
        if (!castAlive.get()) return
        if (message == "Chromecast is not connected") {
            isCasting = false
            pushMute()
            scheduleCastClientRetry(latestPlayer)
            return
        }
        // The first cast attempt often races the receiver or the phone relay:
        // the same episode loads when it is tapped again. Retry once here so a
        // second tap is not needed, then give the episode back to the phone.
        if (castLoadRetry < 1) {
            castLoadRetry += 1
            castClientRetry = 1
            isCasting = false
            castAttachStarted = false
            castLoadedKey = null
            castPending = true
            pushMute()
            castHint = "Starting on your TV…"
            return
        }
        failCastSource(message, playhead.ms)
    }

    var castSentSubtitleKey by remember(anilistId, episode, source?.url) { mutableStateOf("") }
    LaunchedEffect(anilistId, episode) { castLoadRetry = 0 }

    fun loadOntoCast(helper: CastSessionHelper, src: PlayableSource) {
        if (!castAlive.get()) return
        ensureCastNotifications()
        if (!helper.isSessionAvailable()) {
            // No receiver client: do not prepare the relay. That missing client
            // is what made the previous retry call straight back into load.
            scheduleCastClientRetry(latestPlayer)
            return
        }
        val pos = castPlayhead()
        castSentPositionMs = pos
        castPlayheadRequested = true
        runCatching {
            latestPlayer?.pause()
            latestPlayer?.playWhenReady = false
        }
        isCasting = false
        castHint = "Connecting to your TV"
        controlsVisible = true
        pushMute()
        syncCastSubtitles()
        val captions = latestLibrarySubtitles
        castSentSubtitleKey = castCaptionKey(captions)
        castLoadHooks.set(
            helper.load(
                source = src.copy(subtitles = captions),
                title = latestTitle,
                episode = latestEpisode,
                positionMs = pos,
                anilistId = anilistId,
                onReady = { castReady() },
                onError = { message -> castFailed(message) },
            ),
        )
    }

    /** Join the receiver when it is already playing this episode. Do not reload it. */
    fun joinLiveCast(helper: CastSessionHelper, src: PlayableSource, key: String): Boolean {
        val same = castSameItem(
            helper.loadedItem,
            CastItemKey(
                anilistId = anilistId,
                episode = latestEpisode,
                url = src.url,
                audio = castAudioKey(src.audioLanguage),
            ),
        )
        if (castJoin(helper.casting, helper.isLoadInFlight(), same) != CastJoin.Adopt) return false
        castLoadedKey = key
        castAttachStarted = true
        castPending = false
        castJoinedLive = true
        castClientGaveUp = false
        val live = helper.receiverPositionMs()
        if (live > 0L) {
            positionMs = live
            playhead.ms = live
            // Remember the clock so a later load does not treat this attach as
            // still waiting for history. Do not arm a correction: this screen
            // did not request that playhead.
            castSentPositionMs = live
        }
        castPlayheadRequested = false
        isCasting = helper.casting
        castHint = null
        if (castAlive.get()) runCatching { latestPlayer?.pause() }
        pushMute()
        if (helper.isLoadInFlight()) {
            castLoadHooks.set(
                helper.replaceLoadHooks(
                    onReady = { castReady() },
                    onError = { message -> castFailed(message) },
                ),
            )
        }
        return true
    }

    fun applyCastAttach(sessionUp: Boolean) {
        val key = castSourceKey()
        val sourceChanged = castAttachStarted && !isCasting && key != null && key != castLoadedKey
        val decision = reduceCastAttach(
            pending = castPending,
            loadStarted = castAttachStarted,
            sessionUp = sessionUp,
            hasSource = key != null,
            sourceChanged = sourceChanged,
            sessionConnecting = castHelper?.isSessionConnecting() == true,
        )
        castPending = decision.pending
        castAttachStarted = decision.loadStarted
        if (!decision.loadStarted) castLoadedKey = null
        if (!decision.load) return
        val helper = castHelper ?: return
        if (!helper.isSessionAvailable()) {
            castAttachStarted = false
            castPending = true
            return
        }
        val src = latestSources.getOrNull(latestSourceIndex) ?: return
        if (key != null && joinLiveCast(helper, src, key)) return
        castLoadedKey = key
        loadOntoCast(helper, src)
    }

    DisposableEffect(castHelper) {
        val helper = castHelper
        if (helper == null) {
            onDispose { }
        } else {
            val castErrors = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (!castAlive.get() || !isCasting) return
                    val live = runCatching { helper.castPlayer.currentPosition }.getOrDefault(playhead.ms)
                    applyPlaybackError(helper.castPlayer, error, live, fromCast = true)
                }
            }
            helper.attachScreen()
            helper.castPlayer.addListener(castErrors)
            val onEnded: () -> Unit = onEnded@{
                // CastPlayer never emits STATE_ENDED. The receiver goes idle
                // with IDLE_REASON_FINISHED — that is the end of the episode.
                if (!castAlive.get() || !isCasting) return@onEnded
                playbackEnded = true
                playing = false
                controlsVisible = true
            }
            helper.setPlaybackEndedListener(onEnded)
            val availability = object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() {
                    if (!castAlive.get()) return
                    // CastPlayer usually fires this once, often before the
                    // receiver client exists. A give-up from the earlier wait
                    // must not ignore that signal, or the TV never gets the episode.
                    val reset = reduceCastGiveUp(
                        gaveUp = castClientGaveUp,
                        retry = castClientRetry,
                        pending = castPending,
                        availabilitySignaled = true,
                        clientReady = helper.isSessionAvailable(),
                    )
                    castClientGaveUp = reset.gaveUp
                    castClientRetry = reset.retry
                    castPending = reset.pending
                    if (reset.load) applyCastAttach(sessionUp = true)
                }

                override fun onCastSessionUnavailable() {
                    if (!castAlive.get()) return
                    val castingNow = isCasting
                    val requested = castPlayheadRequested
                    val sent = castSentPositionMs
                    val stale = castClockStale
                    val receiverPos = runCatching {
                        helper.castPlayer.currentPosition.coerceAtLeast(0L)
                    }.getOrDefault(latestPosition.ms)
                    val pos = castDisconnectResumeMs(
                        casting = castingNow,
                        clockStale = stale,
                        loadRequested = requested,
                        sentPositionMs = sent,
                        screenPositionMs = positionMs,
                        playheadMs = playhead.ms,
                        receiverMs = receiverPos,
                    )
                    val receiverIsThisEpisode = castingNow && !stale && !requested
                    castPending = false
                    castAttachStarted = false
                    castLoadedKey = null
                    castSentPositionMs = -1L
                    castPlayheadRequested = false
                    castClockStale = false
                    castClientRetry = 0
                    castClientGaveUp = false
                    helper.stopCasting()
                    isCasting = false
                    val local = latestPlayer
                    if (!receiverIsThisEpisode) {
                        positionMs = pos
                        playhead.ms = pos
                    }
                    if (pos > 1_000L) {
                        runCatching { local?.seekTo(pos) }
                        positionMs = pos
                        playhead.ms = pos
                    }
                    pushMute()
                    runCatching { local?.play() }
                    controlsVisible = true
                }
            }
            helper.setAvailabilityListener(availability)
            if (helper.casting || helper.isLoadInFlight()) {
                CastPlaybackService.promote(context.applicationContext, title, episode)
            }
            applyCastAttach(helper.isSessionAvailable())
            onDispose {
                // The next episode installs its own listeners before this
                // screen leaves. Clear only what this screen still owns.
                // Drop load callbacks so they cannot touch a released player.
                // The relay and the Cast session stay with the process.
                castAlive.set(false)
                helper.detachScreen()
                castLoadHooks.getAndSet(null)?.let { helper.clearLoadHooks(it) }
                helper.castPlayer.removeListener(castErrors)
                helper.clearPlaybackEndedListener(onEnded)
                helper.clearAvailabilityListener(availability)
            }
        }
    }

    // A session that was already connected, or that arrived before the stream
    // list resolved, loads once the source exists. The availability callback
    // alone used to return and never retry. A receiver that is still connecting
    // stays pending until its media client exists.
    LaunchedEffect(
        castHelper,
        castPending,
        source?.url,
        source?.audioLanguage,
        sourceIndex,
        episode,
        castClientRetry,
        castClientGaveUp,
    ) {
        // Give-up blocks another immediate poll. Clearing it when a session
        // is signaled (even before the client exists) restarts this effect.
        if (castClientGaveUp) return@LaunchedEffect
        val helper = castHelper ?: return@LaunchedEffect
        val wait = if (castClientRetry > 0) castClientRetryDelayMs(castClientRetry) else null
        if (castClientRetry > 0 && wait == null) {
            abandonCastToPhone(latestPlayer)
            return@LaunchedEffect
        }
        if (wait != null) delay(wait)
        if (castClientGaveUp) return@LaunchedEffect
        if (!helper.isSessionAvailable()) {
            val connecting = helper.isSessionConnecting()
            if (!castPending && !connecting) return@LaunchedEffect
            castPending = true
            repeat(10) {
                delay(500)
                if (castClientGaveUp) return@LaunchedEffect
                if (helper.isSessionAvailable()) {
                    applyCastAttach(sessionUp = true)
                    return@LaunchedEffect
                }
                if (!helper.isSessionConnecting() && !castPending) return@LaunchedEffect
            }
            if (!helper.isSessionAvailable()) abandonCastToPhone(latestPlayer)
            return@LaunchedEffect
        }
        applyCastAttach(sessionUp = true)
    }

    // Watch history often arrives after the first composition. An attach that
    // already started at 0 still takes that saved playhead, even if the phone
    // or the TV has been playing from the start for a few seconds.
    LaunchedEffect(castHelper, anilistId, episode, title, totalEpisodes, skipTimes.outro?.startMs) {
        val helper = castHelper ?: return@LaunchedEffect
        helper.noteProgress(
            if (anilistId <= 0) {
                null
            } else {
                CastProgressTarget(
                    anilistId = anilistId,
                    title = title,
                    episode = episode,
                    totalEpisodes = totalEpisodes,
                    outroStartMs = skipTimes.outro?.startMs,
                )
            },
        )
    }

    LaunchedEffect(startPositionMs, resumeAtMs) {
        if (castJoinedLive) return@LaunchedEffect
        if (!castAttachStarted) return@LaunchedEffect
        if (castSentPositionMs > 1_500L) return@LaunchedEffect
        val target = castAttachPositionMs(
            localPositionMs = latestPlayer?.currentPosition?.takeIf { it > 0L } ?: latestPosition.ms,
            resumeAtMs = resumeAtMs,
            startPositionMs = latestStartPositionMs,
            allowHistoryFallback = latestAllowHistory,
            pendingResume = true,
        )
        if (target <= 1_500L) return@LaunchedEffect
        if (castSentPositionMs >= 0L && target <= castSentPositionMs + 1_500L) return@LaunchedEffect
        val helper = castHelper ?: return@LaunchedEffect
        if (isCasting) {
            helper.castPlayer.seekTo(safeSeekTarget(target, helper.castPlayer.duration, durationMs))
            castSentPositionMs = target
            positionMs = target
            playhead.ms = target
            return@LaunchedEffect
        }
        if (!helper.isSessionAvailable()) return@LaunchedEffect
        val src = latestSources.getOrNull(latestSourceIndex) ?: return@LaunchedEffect
        castLoadedKey = castSourceKey()
        loadOntoCast(helper, src)
    }

    // Episode the current cast item was loaded for. A later episode must not
    // inherit this item's playhead (that seek lands at the previous ending).
    var castLoadedEpisode by remember { mutableIntStateOf(episode) }
    LaunchedEffect(episode, isCasting) {
        if (!isCasting) castLoadedEpisode = episode
    }

    // When quality, episode, or the selected audio language changes mid-cast,
    // push a new load. A shared master keeps the same URL, so the language
    // has to be its own key. The first load is the attach effect above.
    LaunchedEffect(source?.url, source?.audioLanguage, episode) {
        if (!isCasting) return@LaunchedEffect
        // Stop the phone before the new item loads. The local player is rebuilt
        // for the new episode and would otherwise start playing beside the TV.
        runCatching {
            latestPlayer?.pause()
            latestPlayer?.playWhenReady = false
        }
        val helper = castHelper ?: return@LaunchedEffect
        val src = source ?: return@LaunchedEffect
        val screenItem = CastItemKey(
            anilistId = anilistId,
            episode = episode,
            url = src.url,
            audio = castAudioKey(src.audioLanguage),
        )
        // Joining a receiver that is already playing this item must not load it again.
        val alreadyThere = helper.playingItem?.takeIf { !helper.isLoadInFlight() } ?: helper.loadedItem
        if (castJoinedLive && castSameItem(alreadyThere, screenItem)) {
            castLoadedEpisode = episode
            castLoadedKey = castSourceKey()
            castPlayheadRequested = false
            return@LaunchedEffect
        }
        val sameEpisode = castLoadedEpisode == episode
        val pos = if (sameEpisode) {
            helper.castPlayer.currentPosition.coerceAtLeast(0L)
                .takeIf { it > 0L } ?: playhead.ms
        } else {
            startPositionMs.coerceAtLeast(0L)
        }
        castLoadedEpisode = episode
        castLoadedKey = "${sourceIndex}\u0000${src.url}\u0000${src.audioLanguage.orEmpty()}\u0000$episode"
        castSentPositionMs = pos
        castPlayheadRequested = true
        if (!sameEpisode) {
            // Drop the previous item's end so its countdown cannot skip this one.
            // The receiver clock stays on that item until the new load is ready.
            playbackEnded = false
            autoplayCancelled = false
            playhead.ms = pos
            positionMs = pos
            durationMs = 0L
            castClockStale = true
        }
        val captions = latestLibrarySubtitles
        castSentSubtitleKey = castCaptionKey(captions)
        castLoadHooks.set(
            helper.load(
                source = src.copy(subtitles = captions),
                title = title,
                episode = episode,
                positionMs = pos,
                anilistId = anilistId,
                onReady = {
                    if (!castAlive.get()) return@load
                    runCatching {
                        latestPlayer?.pause()
                        latestPlayer?.playWhenReady = false
                    }
                    pushMute()
                    castHint = null
                    isCasting = true
                    catchCastPlayhead(helper, pos, requested = true)
                    castPlayheadRequested = false
                    castClockStale = false
                    if (!sameEpisode) {
                        playbackEnded = false
                        autoplayCancelled = false
                    }
                },
                onError = { message ->
                    if (!castAlive.get()) return@load
                    val live = runCatching {
                        castHelper?.castPlayer?.currentPosition ?: playhead.ms
                    }.getOrDefault(playhead.ms)
                    failCastSource(message, live)
                },
            ),
        )
    }

    // Captions often arrive after the receiver is already playing. Push the
    // sidecar files onto that item instead of leaving the TV without them.
    LaunchedEffect(librarySubtitles) {
        if (!castShouldPushCaptions(isCasting, castSentSubtitleKey, librarySubtitles)) return@LaunchedEffect
        val helper = castHelper ?: return@LaunchedEffect
        repeat(8) {
            if (!castAlive.get()) return@LaunchedEffect
            val tracks = latestLibrarySubtitles
            if (!castShouldPushCaptions(isCasting || helper.casting, castSentSubtitleKey, tracks)) {
                return@LaunchedEffect
            }
            val pos = helper.castPlayer.currentPosition.coerceAtLeast(0L).takeIf { it > 0L } ?: playhead.ms
            if (helper.pushLibrarySubtitles(tracks, pos)) {
                castSentSubtitleKey = castCaptionKey(tracks)
                return@LaunchedEffect
            }
            delay(400)
        }
    }

    LaunchedEffect(castHint) {
        val message = castHint ?: return@LaunchedEffect
        if (message != "Connecting to your TV") {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        delay(4_200)
        if (castHint == message) castHint = null
    }

    LaunchedEffect(player, playbackSpeed, holdBoost, isCasting) {
        val speed = if (holdBoost) 2f else playbackSpeed
        val rate = PlaybackParameters(speed)
        player?.playbackParameters = rate
        if (isCasting) castHelper?.castPlayer?.playbackParameters = rate
    }
    LaunchedEffect(playbackSpeed) {
        playerPrefs.edit().putFloat("playback_speed", playbackSpeed).apply()
    }
    LaunchedEffect(resizeMode) {
        playerPrefs.edit().putInt("resize_mode", resizeMode).apply()
    }

    LaunchedEffect(seekFlashMs) {
        if (seekFlashMs == 0L) return@LaunchedEffect
        val shown = seekFlashMs
        delay(720)
        if (seekFlashMs == shown) seekFlashMs = 0L
    }

    DisposableEffect(playerBundle) {
        ready = false
        buffering = false
        error = null
        textOptions = emptyList()
        videoOptions = emptyList()
        audioOptions = emptyList()
        val activePlayer = playerBundle?.player
        val activeSelector = playerBundle?.trackSelector
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isCasting) {
                    ready = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
                    buffering = playbackState == Player.STATE_BUFFERING
                }
                if (playbackState == Player.STATE_READY) {
                    durationMs = activePlayer?.duration?.coerceAtLeast(0L) ?: 0L
                    error = null
                    // Local ExoPlayer stays ready while Chromecast plays. Do not
                    // cancel the cast end countdown from that player.
                    if (playbackEnded && !isCasting) {
                        // User scrubbed/replayed after end — clear autoplay.
                        playbackEnded = false
                        autoplayCancelled = false
                    }
                    val target = resumeAtMs
                    if (!didSeekStart && target > 1_500L && durationMs > target) {
                        activePlayer?.seekTo(target.coerceAtMost(durationMs - 1_000L))
                        positionMs = target
                        resumeAtMs = 0L
                        didSeekStart = true
                        allowHistoryFallback = false
                    }
                }
                if (playbackState == Player.STATE_ENDED && !isCasting) {
                    buffering = false
                    playbackEnded = true
                    controlsVisible = true
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val quiet = castPhoneShouldStayQuiet(
                    casting = isCasting,
                    handoff = castAttachStarted && !isCasting && !castClientGaveUp,
                    pending = castPending && !castClientGaveUp,
                )
                if (quiet && isPlaying) {
                    activePlayer?.pause()
                    activePlayer?.playWhenReady = false
                }
                // The TV owns the play icon once the handoff starts. A local
                // pause must not flip it, and a local start must not stick.
                if (!quiet) playing = isPlaying
            }

            override fun onTracksChanged(tracks: Tracks) {
                val built = buildTextOptions(tracks, latestLibrarySubtitles)
                val options = if (
                    isCasting &&
                    built.none { it.id != "off" && it.id != "auto" }
                ) {
                    sidecarTextOptions(latestLibrarySubtitles).ifEmpty { built }
                } else {
                    built
                }
                textOptions = options
                selectedTextId = rematchTextId(selectedTextId, options)
                val vids = buildVideoOptions(tracks)
                videoOptions = vids
                selectedVideoId = rematchVideoId(selectedVideoId, vids)
                val audios = buildAudioOptions(tracks)
                audioOptions = audios
                selectedAudioId = rematchAudioId(selectedAudioId, audios)
                if (activeSelector != null) {
                    applyTextSelection(activeSelector, selectedTextId, options, subsEnabled)
                    applyVideoSelection(activeSelector, selectedVideoId, vids)
                    applyAudioSelection(activeSelector, selectedAudioId, audios)
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                val pos = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: playhead.ms
                applyPlaybackError(activePlayer, e, pos, fromCast = false)
            }
        }
        activePlayer?.addListener(listener)
        onDispose {
            val view = playerViewRef
            if (view != null && view.player === activePlayer) view.player = null
            activePlayer?.removeListener(listener)
            activePlayer?.release()
        }
    }

    LaunchedEffect(playlist, playAttempt) {
        val live = playhead.ms
        sourceIndex = 0
        // A Sub/Dub reload reuses this screen. Keep the live playhead instead of
        // seeking back to the position from when playback first started.
        resumeAtMs = if (live > 1_500L) live else startPositionMs
        didSeekStart = false
        allowHistoryFallback = live <= 1_500L
        if (playlist.isEmpty()) error = "No playable source"
    }

    // Parent may load watch-history into startPositionMs after first composition.
    LaunchedEffect(startPositionMs) {
        if (!didSeekStart && allowHistoryFallback && startPositionMs > resumeAtMs) {
            resumeAtMs = startPositionMs
        }
    }

    // Apply resume even if READY already fired before history arrived.
    // Duration is a key so a length that arrives late can still seek. A file
    // that stays shorter than the target never seeks, and the progress loop
    // drops that hold instead of freezing history.
    LaunchedEffect(resumeAtMs, player, ready, didSeekStart, durationMs) {
        val p = player ?: return@LaunchedEffect
        if (didSeekStart || !ready || resumeAtMs <= 1_500L) return@LaunchedEffect
        val d = p.duration
        if (d <= resumeAtMs) return@LaunchedEffect
        p.seekTo(resumeAtMs.coerceAtMost(d - 1_000L))
        positionMs = resumeAtMs
        resumeAtMs = 0L
        didSeekStart = true
        allowHistoryFallback = false
    }

    LaunchedEffect(subsEnabled, selectedTextId, textOptions, trackSelector) {
        val selector = trackSelector ?: return@LaunchedEffect
        applyTextSelection(selector, selectedTextId, textOptions, subsEnabled)
    }

    // CastPlayer ignores the local TrackSelector — push captions onto MediaTracks.
    LaunchedEffect(isCasting, subsEnabled, selectedTextId, textOptions, librarySubtitles) {
        if (!isCasting) return@LaunchedEffect
        syncCastSubtitles()
    }

    // Local ExoPlayer may already be paused with empty track groups; still list
    // the library captions in the menu while casting.
    LaunchedEffect(isCasting, librarySubtitles, textOptions) {
        if (!isCasting) return@LaunchedEffect
        val external = librarySubtitles
        if (external.isEmpty()) return@LaunchedEffect
        if (textOptions.any { it.id != "off" && it.id != "auto" }) return@LaunchedEffect
        textOptions = sidecarTextOptions(external)
    }

    LaunchedEffect(selectedVideoId, videoOptions, trackSelector) {
        val selector = trackSelector ?: return@LaunchedEffect
        applyVideoSelection(selector, selectedVideoId, videoOptions)
    }

    LaunchedEffect(selectedAudioId, audioOptions, trackSelector) {
        val selector = trackSelector ?: return@LaunchedEffect
        applyAudioSelection(selector, selectedAudioId, audioOptions)
    }

    LaunchedEffect(subStyle, playerViewRef) {
        applyCaptionStyle(playerViewRef, subStyle)
        context.getSharedPreferences("anikitty_player", 0).edit()
            .putInt("sub_style_v", SUB_STYLE_VERSION)
            .putFloat("sub_size_sp", subStyle.sizeSp)
            .putFloat("sub_bottom", subStyle.bottomPad)
            .putInt("sub_color", subStyle.color)
            .putString("sub_bg_mode", subStyle.bgMode.name)
            .putBoolean("sub_bg", subStyle.bgMode != SubBgMode.None)
            .putInt("sub_edge", subStyle.edgeType)
            .putBoolean("sub_bold", subStyle.bold)
            .putBoolean("sub_italic", subStyle.italic)
            .putFloat("sub_bg_alpha", subStyle.bgAlpha)
            .apply()
    }

    LaunchedEffect(activePlayer, scrubbing, isCasting, nearEndDismissed, playbackEnded, nextTriggered, episode) {
        var lastProgressAt = 0L
        var heldResumeMs = 0L
        var lastSkipKey: String? = null
        var lastAutoSkippedKey: String? = null
        var lastMinute = -2
        // Stale CastPlayer position/duration belong to the previous item until
        // this episode has buffered or reached ready.
        var castItemStarted = false
        var castLeftEnding = false
        var stallBuffered = -1L
        var stallSince = 0L
        val p = activePlayer
        while (p != null) {
            if (!scrubbing) {
                // A new episode releases this player. Reading it afterwards
                // throws and takes the process down, so this tick just stops.
                val clock = runCatching { playerClock(p) }.getOrNull() ?: break
                val rawPos = clock.positionMs
                val rawDur = clock.durationMs
                if (
                    isCasting &&
                    !castClockStale &&
                    (clock.state == Player.STATE_READY || clock.state == Player.STATE_BUFFERING)
                ) {
                    castItemStarted = true
                }
                val trustCastClock = (!isCasting || castItemStarted) && !castClockStale
                val pos = if (trustCastClock) rawPos else playhead.ms
                if (trustCastClock) {
                    playhead.ms = rawPos
                    if (rawDur > 0 && rawDur != durationMs) durationMs = rawDur
                }
                if (!isCasting && p is ExoPlayer) {
                    val format = p.videoFormat
                    val height = format?.height ?: 0
                    if (height > 0 && height != renderedHeight) {
                        renderedHeight = height
                        val bitrate = format?.bitrate ?: 0
                        if (bitrate > 200_000) {
                            playerPrefs.edit().putInt("last_video_bitrate", bitrate).apply()
                        }
                    }
                    val buffered = clock.bufferedMs
                    val stalled = clock.playWhenReady &&
                        !clock.playing &&
                        clock.state == Player.STATE_BUFFERING
                    if (stalled && buffered == stallBuffered) {
                        if (stallSince == 0L) stallSince = System.currentTimeMillis()
                        if (System.currentTimeMillis() - stallSince >= 8_000L && latestSources.size > latestSourceIndex + 1) {
                            stallSince = 0L
                            val decision = sourceFailover(
                                index = latestSourceIndex,
                                count = latestSources.size,
                                livePositionMs = maxOf(pos, resumeAtMs),
                                heldResumeMs = resumeAtMs,
                                historyMs = startPositionMs,
                                allowHistory = allowHistoryFallback,
                            )
                            if (decision.nextIndex != null) {
                                val next = nextIndexSkippingDead(fromCast = false) ?: decision.nextIndex
                                resumeAtMs = decision.resumeMs
                                positionMs = decision.resumeMs
                                playhead.ms = decision.resumeMs
                                didSeekStart = false
                                allowHistoryFallback = false
                                sourceIndex = next
                            }
                        }
                    } else {
                        stallBuffered = buffered
                        stallSince = if (stalled) System.currentTimeMillis() else 0L
                    }
                }
                val d = if (trustCastClock) rawDur else 0L
                if (clock.playing != playing) playing = clock.playing
                if (isCasting) {
                    val nowBuffering = clock.state == Player.STATE_BUFFERING
                    if (nowBuffering != buffering) buffering = nowBuffering
                }
                val nowReady = clock.state == Player.STATE_READY ||
                    clock.state == Player.STATE_BUFFERING ||
                    isCasting
                if (nowReady != ready) ready = nowReady
                val active = skipTimes.activeAt(pos)
                val skipKey = if (active == null) null else "${active.first}:${active.second.endMs}"
                if (skipKey != lastSkipKey) {
                    lastSkipKey = skipKey
                    shownSkip = active
                }
                if (active != null && skipKey != lastAutoSkippedKey) {
                    val (kind, iv) = active
                    val shouldAuto = kind == "recap" ||
                        (autoSkip && (kind == "intro" || kind == "credits" || kind == "preview"))
                    if (shouldAuto) {
                        lastAutoSkippedKey = skipKey
                        // CastPlayer and ExoPlayer share this seek path.
                        runCatching { p.seekTo(safeSeekTarget(iv.endMs, rawDur, durationMs)) }
                        playhead.ms = iv.endMs
                    }
                }
                val dur = if (d > 0) d else if (trustCastClock) durationMs else 0L
                if (trustCastClock && dur > 0L && pos < dur - 5_000L) castLeftEnding = true
                // CastPlayer stays IDLE when the item finishes (playWhenReady
                // still true). Ignore that until this episode has actually played
                // away from the previous item's ending.
                if (
                    isCasting &&
                    castItemStarted &&
                    castLeftEnding &&
                    !playbackEnded &&
                    dur >= NEAR_END_MIN_DURATION_MS &&
                    pos >= dur - 1_500L &&
                    !clock.playing &&
                    clock.playWhenReady &&
                    clock.state == Player.STATE_IDLE
                ) {
                    playbackEnded = true
                    controlsVisible = true
                }
                val remaining = (dur - pos).coerceAtLeast(0L)
                val offer = onNextEpisode != null &&
                    !playbackEnded &&
                    !nearEndDismissed &&
                    !nextTriggered &&
                    dur >= NEAR_END_MIN_DURATION_MS &&
                    remaining in 1L..NEAR_END_THRESHOLD_MS
                if (offer != nearEndOffer) nearEndOffer = offer
                val mins = if (dur > 30_000L) (remaining / 60_000L).toInt() else -1
                if (mins != lastMinute) {
                    lastMinute = mins
                    shelfMinutes = mins
                }
                val requestedResume = maxOf(
                    resumeAtMs,
                    if (!didSeekStart) startPositionMs else 0L,
                )
                heldResumeMs = reachableResumeMs(
                    armedResumeMs(heldResumeMs, requestedResume),
                    liveDurationForResume(rawDur, trustCastClock),
                )
                val waitingResume = resumeStillAhead(heldResumeMs, pos)
                if (!waitingResume) heldResumeMs = 0L
                if (progressSaveDue(pos, lastProgressAt, openingSeekPending = waitingResume)) {
                    lastProgressAt = pos
                    // Phone and Chromecast share this playhead. Casting uses
                    // the cast player; the credits marker is the same one.
                    latestOnProgress?.invoke(pos, dur, latestSkip)
                }
            }
            delay(250)
        }
    }

    DisposableEffect(episode) {
        // Capture this episode's saver. rememberUpdatedState would already
        // point at the next episode by the time dispose runs. The credits
        // marker is read here, while this effect is still the one leaving:
        // the loader below replaces it only after this dispose.
        val flush = onProgress
        onDispose {
            flush?.invoke(playhead.ms, durationMs, skipTimes)
        }
    }

    LaunchedEffect(source?.url, source?.skip, durationMs, episode, titleHint) {
        val durationSec = if (durationMs > 0L) durationMs / 1000.0 else 0.0
        val provider = source?.skip ?: SkipTimes()
        val own = chooseSkip(provider, durationSec = durationSec)
        val malId = malIdFromTitleHint(titleHint)
        val stamps = if ((own.intro == null || own.outro == null) && durationSec > 0.0 && malId > 0 && episode > 0) {
            runCatching { loadAniSkip(malId, episode) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        // This source's markers win. AniSkip fills a side this stream did not send.
        skipTimes = chooseSkip(provider, stamps, durationSec)
    }

    LaunchedEffect(nearEndOffer, showEndAutoplay) {
        if (nearEndOffer || showEndAutoplay) {
            controlsVisible = true
        }
    }

    LaunchedEffect(sleepChoice) {
        if (sleepChoice <= 0) return@LaunchedEffect
        val minutes = sleepChoice
        sleepArmedAt = System.currentTimeMillis()
        delay(minutes * 60_000L)
        if (sleepChoice != minutes) return@LaunchedEffect
        latestActive?.pause()
        playing = false
        controlsVisible = true
        sleepNote = "Sleep timer paused playback"
        sleepChoice = 0
    }

    LaunchedEffect(sleepChoice) {
        if (sleepChoice <= 0) return@LaunchedEffect
        while (sleepChoice > 0) {
            delay(30_000)
            sleepTick++
        }
    }

    LaunchedEffect(playbackEnded, sleepChoice) {
        if (!playbackEnded || sleepChoice != -1) return@LaunchedEffect
        autoplayCancelled = true
        sleepNote = "Sleep timer stopped at the end of this episode"
        sleepChoice = 0
        controlsVisible = true
    }

    LaunchedEffect(showEndAutoplay, episode, autoplayCancelled, nextTriggered) {
        if (!showEndAutoplay || autoplayCancelled || nextTriggered) return@LaunchedEffect
        for (sec in AUTOPLAY_COUNTDOWN_SEC downTo 1) {
            autoplaySecLeft = sec
            delay(1_000)
            if (autoplayCancelled || !playbackEnded || nextTriggered) return@LaunchedEffect
        }
        goNextEpisode()
    }

    LaunchedEffect(nextTriggered) {
        if (!nextTriggered) return@LaunchedEffect
        // If resolve fails, parent keeps this episode — allow retry.
        delay(15_000)
        nextTriggered = false
    }

    LaunchedEffect(controlsVisible, playing, scrubbing, panel, isCasting, nearEndOffer, showEndAutoplay, chromeFocused, controlsLocked) {
        if (nearEndOffer || showEndAutoplay) return@LaunchedEffect
        // Keep chrome up while locked so the unlock control stays reachable.
        if (controlsLocked) return@LaunchedEffect
        if (controlsVisible && playing && !scrubbing && panel == PlayerMenuPage.None && !chromeFocused) {
            delay(if (isCasting) 5_500 else 3_800)
            controlsVisible = false
        }
    }

    fun toggleMute() {
        val next = !muted
        muted = next
        if (isCasting) {
            castHelper?.setTvMuted(next)
        } else {
            val level = if (next) 0f else 1f
            castHelper?.heldVolume = level
            activePlayer?.volume = level
            player?.volume = level
        }
        controlsVisible = true
    }

    DisposableEffect(isCasting) {
        if (isCasting) {
            CastHardwareVolume.onStep = { direction ->
                if (direction == 0) {
                    toggleMute()
                    true
                } else {
                    val delta = if (direction > 0) 0.05f else -0.05f
                    val level = castHelper?.adjustDeviceVolume(delta)
                    if (level == null) {
                        false
                    } else {
                        if (direction > 0 && level > 0.02f && muted) {
                            muted = false
                            castHelper?.setTvMuted(false)
                        }
                        gestureHud = GestureHud(
                            kind = GestureHudKind.Volume,
                            level = level,
                            caption = "TV",
                        )
                        true
                    }
                }
            }
        }
        onDispose { CastHardwareVolume.onStep = null }
    }

    LaunchedEffect(player, muted) {
        pushMute()
    }

    DisposableEffect(player, volumeBoost, muted) {
        val exo = player as? ExoPlayer
        fun applyBoost() {
            val session = exo?.audioSessionId ?: androidx.media3.common.C.AUDIO_SESSION_ID_UNSET
            val gain = when {
                muted || volumeBoost <= 0 || session == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET -> 0
                volumeBoost == 1 -> 250
                volumeBoost == 2 -> 600
                else -> 1000
            }
            if (loudnessHold.session == session && loudnessHold.gain == gain &&
                (gain == 0 || loudnessHold.effect != null)
            ) {
                return
            }
            loudnessHold.effect?.release()
            loudnessHold.effect = null
            loudnessHold.session = session
            loudnessHold.gain = gain
            if (gain == 0) return
            loudnessHold.effect = runCatching {
                android.media.audiofx.LoudnessEnhancer(session).apply {
                    setTargetGain(gain)
                    enabled = true
                }
            }.getOrNull()
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = applyBoost()
        }
        exo?.addListener(listener)
        playerPrefs.edit().putInt("volume_boost", volumeBoost).apply()
        applyBoost()
        onDispose {
            exo?.removeListener(listener)
            loudnessHold.release()
        }
    }

    fun seekBy(deltaMs: Long) {
        // A seek while the TV is still connecting would move the phone.
        if (!isCasting && !castClientGaveUp && (castAttachStarted || castPending)) return
        val p = activePlayer ?: return
        val target = safeSeekTarget(p.currentPosition + deltaMs, p.duration, durationMs)
        p.seekTo(target)
        positionMs = target
        playhead.ms = target
        val sameDir = seekFlashMs == 0L ||
            (seekFlashMs > 0L && deltaMs > 0L) ||
            (seekFlashMs < 0L && deltaMs < 0L)
        seekFlashMs = if (sameDir) seekFlashMs + deltaMs else deltaMs
        if (!controlsLocked) controlsVisible = true
    }

    fun skipTo(endMs: Long) {
        if (!isCasting && !castClientGaveUp && (castAttachStarted || castPending)) return
        val p = activePlayer ?: return
        // Prefer the remote CastPlayer when casting — same skip markers as local.
        val target = safeSeekTarget(endMs, p.duration, durationMs)
        p.seekTo(target)
        positionMs = target
        playhead.ms = target
        controlsVisible = true
    }

    fun pickQuality(index: Int) {
        if (index == sourceIndex || index !in playlist.indices) return
        resumeAtMs = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: playhead.ms
        didSeekStart = false
        allowHistoryFallback = false
        // Keep the user's resolution lock across mirror switches when possible.
        sourceIndex = index
        panel = PlayerMenuPage.None
        controlsVisible = true
    }

    val activeSkip = shownSkip
    val qualityLabel = when {
        selectedVideoId != "auto" ->
            videoOptions.firstOrNull { it.id == selectedVideoId }?.label ?: selectedVideoId
        renderedHeight >= 2160 -> "Auto · 2160p"
        renderedHeight >= 1440 -> "Auto · 1440p"
        renderedHeight >= 1080 -> "Auto · 1080p"
        renderedHeight >= 720 -> "Auto · 720p"
        renderedHeight > 0 -> "Auto · ${renderedHeight}p"
        videoOptions.any { it.height > 0 } -> "Auto"
        else -> source?.label ?: "Quality"
    }
    val subLabel = when {
        !subsEnabled -> "Off"
        selectedTextId == "auto" -> "Auto"
        else -> textOptions.firstOrNull { it.id == selectedTextId }?.label ?: "Subs"
    }
    val audioLabel = if (audio == "dub") "Dub" else "Sub"

    fun enterPip() {
        if (
            playbackEnded || isCasting || castPending ||
            (castAttachStarted && !isCasting && !castClientGaveUp)
        ) {
            return
        }
        val host = activity ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!host.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val rect = android.graphics.Rect()
        playerViewRef?.getGlobalVisibleRect(rect)
        val params = PipAutoEnter.params(
            activity = host,
            autoEnter = true,
            videoWidth = player?.videoSize?.width ?: 0,
            videoHeight = player?.videoSize?.height ?: renderedHeight,
            sourceRect = rect,
        )
        val entered = try {
            host.enterPictureInPictureMode(params)
        } catch (_: IllegalStateException) {
            false
        }
        if (entered) {
            panel = PlayerMenuPage.None
            controlsVisible = false
        }
    }

    val handoffToCast = castAttachStarted && !isCasting && !castClientGaveUp
    val castScreenUp = castPhoneShouldStayQuiet(
        casting = isCasting,
        handoff = castAttachStarted && !castClientGaveUp,
        pending = castPending && !castClientGaveUp,
    )
    fun castHandoffInFlight(): Boolean = castScreenUp && !isCasting
    fun playOnScreen() {
        if (castHandoffInFlight()) return
        (if (isCasting) castHelper?.castPlayer else player)?.play()
    }
    fun pauseOnScreen() {
        if (castHandoffInFlight()) return
        (if (isCasting) castHelper?.castPlayer else player)?.pause()
    }
    fun toggleOnScreen() {
        if (castHandoffInFlight()) return
        val current = if (isCasting) castHelper?.castPlayer else player
        current?.let { if (it.isPlaying) it.pause() else it.play() }
    }
    val castBlocks = castBlocksPictureInPicture(isCasting, castPending, handoffToCast)
    val autoPip = shouldAutoEnterPip(
        localPlaying = playing && player?.isPlaying == true,
        playbackEnded = playbackEnded,
        castBlocks = castBlocks,
        playWhenReady = player?.playWhenReady == true,
    )
    var inPip by remember {
        mutableStateOf(activity?.isInPictureInPictureMode == true)
    }
    LaunchedEffect(inPip, subStyle, playerViewRef) {
        applyCaptionStyle(playerViewRef, subStyle, scale = if (inPip) 0.62f else 1f)
    }
    DisposableEffect(activity) {
        val host = activity as? ComponentActivity
        if (host == null) return@DisposableEffect onDispose {}
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            if (info.isInPictureInPictureMode) {
                panel = PlayerMenuPage.None
                controlsVisible = false
            }
        }
        host.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            host.removeOnPictureInPictureModeChangedListener(listener)
            PipAutoEnter.update(host, allowed = false)
        }
    }
    SideEffect {
        PipControls.onPlayPause = { toggleOnScreen() }
        PipControls.onSeek = { delta -> seekBy(delta) }
    }
    DisposableEffect(Unit) {
        onDispose {
            PipControls.onPlayPause = null
            PipControls.onSeek = null
        }
    }
    LaunchedEffect(autoPip, activity, renderedHeight) {
        val host = activity ?: return@LaunchedEffect
        val rect = android.graphics.Rect()
        playerViewRef?.getGlobalVisibleRect(rect)
        PipAutoEnter.update(
            activity = host,
            allowed = autoPip,
            videoWidth = player?.videoSize?.width ?: 0,
            videoHeight = player?.videoSize?.height?.takeIf { it > 0 } ?: renderedHeight,
            sourceRect = rect,
        )
    }

    @Composable
    fun SettingsRoot(compact: Boolean) {
        val speedValue = if (kotlin.math.abs(playbackSpeed - 1f) < 0.01f) "Normal" else speedLabel(playbackSpeed)
        PlayerMenuRow(
            label = "Play Speed",
            value = speedValue,
            icon = Icons.Outlined.PlayCircle,
            showChevron = true,
            onClick = { panel = PlayerMenuPage.Speed },
        )
        PlayerMenuRow(
            label = "Quality",
            value = menuQualityValue(qualityLabel),
            icon = Icons.Outlined.Equalizer,
            showChevron = true,
            onClick = { panel = PlayerMenuPage.Quality },
        )
        PlayerMenuRow(
            label = "Audio Tracks",
            value = audioOptions.firstOrNull { it.id == selectedAudioId }?.label
                ?: if (audio == "dub") "Dub" else "Sub",
            icon = Icons.Outlined.Translate,
            showChevron = true,
            onClick = { panel = PlayerMenuPage.Audio },
        )
        PlayerMenuRow(
            label = "Volume Boost",
            value = boostLabel(volumeBoost),
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            showChevron = true,
            onClick = { panel = PlayerMenuPage.Volume },
        )
        PlayerMenuRow(
            label = "Auto-skip intros & outros",
            icon = Icons.Outlined.SkipNext,
            selected = autoSkip,
            onClick = {
                autoSkip = !autoSkip
                playerPrefs.edit().putBoolean("auto_skip", autoSkip).apply()
            },
        )
        PlayerMenuSectionLabel("Captions")
        PlayerMenuRow(
            label = "Subtitles & style",
            value = subLabel,
            icon = Icons.Outlined.ClosedCaption,
            showChevron = true,
            onClick = { panel = PlayerMenuPage.Subs },
        )
        PlayerMenuSectionLabel("Watch")
        if (!isMovie && onPickEpisode != null && totalEpisodes > 1) {
            PlayerMenuRow(
                label = "Episodes",
                value = "E$episode",
                icon = Icons.Outlined.VideoLibrary,
                showChevron = true,
                onClick = {
                    episodeJumpFocus = false
                    panel = PlayerMenuPage.Episodes
                },
            )
        }
        if (onNextEpisode != null && episode < totalEpisodes) {
            PlayerMenuRow(
                label = "Next episode",
                icon = Icons.Outlined.SkipNext,
                showChevron = true,
                onClick = {
                    panel = PlayerMenuPage.None
                    goNextEpisode()
                },
            )
        }
        PlayerMenuRow(
            label = "Picture",
            value = resizeLabel(resizeMode),
            icon = Icons.Outlined.AspectRatio,
            showChevron = true,
            onClick = { panel = PlayerMenuPage.Resize },
        )
        PlayerMenuRow(
            label = "Sleep timer",
            value = sleepLabel(sleepChoice),
            icon = Icons.Outlined.Bedtime,
            showChevron = true,
            onClick = { panel = PlayerMenuPage.Sleep },
        )
        PlayerMenuRow(
            label = "Lock controls",
            icon = Icons.Outlined.Lock,
            showChevron = true,
            onClick = {
                controlsLocked = true
                panel = PlayerMenuPage.None
                controlsVisible = false
            },
        )
        if (compact) {
            PlayerMenuRow(
                label = "Screenshot",
                icon = Icons.Outlined.PhotoCamera,
                showChevron = true,
                onClick = {
                    panel = PlayerMenuPage.None
                    capturePlayerFrame(context, playerViewRef)
                },
            )
            if (!playbackEnded && !castBlocks) {
                PlayerMenuRow(
                    label = "Picture in picture",
                    icon = Icons.Outlined.PictureInPicture,
                    showChevron = true,
                    onClick = { enterPip() },
                )
            }
        }
    }

    @Composable
    fun PanelContents() {
        val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 840
        when (panel) {
            PlayerMenuPage.None -> Unit
            PlayerMenuPage.Root -> SettingsRoot(compact)
            PlayerMenuPage.Speed -> {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                    PlayerMenuRow(
                        label = if (kotlin.math.abs(rate - 1f) < 0.01f) "1.0x" else speedLabel(rate),
                        selected = kotlin.math.abs(playbackSpeed - rate) < 0.01f,
                        onClick = { playbackSpeed = rate },
                    )
                }
                PlayerMenuSectionLabel("Display")
                listOf(
                    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Fill",
                    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Zoom",
                ).forEach { (mode, label) ->
                    PlayerMenuRow(
                        label = label,
                        selected = resizeMode == mode,
                        onClick = { resizeMode = mode },
                    )
                }
            }
            PlayerMenuPage.Quality -> {
                val resolutions = videoOptions.ifEmpty {
                    listOf(VideoOption(id = "auto", label = "Auto (best available)"))
                }
                // Source chip lands on CDN picks first; Quality chip keeps resolution first.
                if (sourcePanelFocus && playlist.isNotEmpty()) {
                    PlayerMenuSectionLabel("Sources")
                    playlist.forEachIndexed { index, src ->
                        val meta = listOfNotNull(
                            src.serverName?.takeIf { it.isNotBlank() },
                            src.quality?.takeIf { it.isNotBlank() },
                            src.audio?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { null }
                        PlayerMenuRow(
                            label = src.label.ifBlank {
                                src.serverName?.takeIf { it.isNotBlank() } ?: "Source ${index + 1}"
                            },
                            value = meta
                                ?: src.subtitles.joinToString(", ") { it.label }
                                    .takeIf { src.subtitles.isNotEmpty() }
                                    .orEmpty(),
                            selected = index == sourceIndex,
                            onClick = { pickQuality(index) },
                        )
                    }
                }
                PlayerMenuSectionLabel("Resolution")
                resolutions.forEach { opt ->
                    PlayerMenuRow(
                        label = opt.label,
                        selected = selectedVideoId == opt.id,
                        onClick = { selectedVideoId = opt.id },
                    )
                }
                if (!sourcePanelFocus && playlist.isNotEmpty()) {
                    PlayerMenuSectionLabel("Sources")
                    playlist.forEachIndexed { index, src ->
                        val meta = listOfNotNull(
                            src.serverName?.takeIf { it.isNotBlank() },
                            src.quality?.takeIf { it.isNotBlank() },
                            src.audio?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { null }
                        PlayerMenuRow(
                            label = src.label.ifBlank {
                                src.serverName?.takeIf { it.isNotBlank() } ?: "Source ${index + 1}"
                            },
                            value = meta
                                ?: src.subtitles.joinToString(", ") { it.label }
                                    .takeIf { src.subtitles.isNotEmpty() }
                                    .orEmpty(),
                            selected = index == sourceIndex,
                            onClick = { pickQuality(index) },
                        )
                    }
                }
            }
            PlayerMenuPage.Subs -> {
                if (onPickAudio != null) {
                    PlayerMenuSectionLabel("Audio")
                    PlayerMenuRow(
                        label = "Sub",
                        selected = audio != "dub",
                        onClick = {
                            selectedAudioId = "auto"
                            onPickAudio.invoke("sub")
                        },
                    )
                    PlayerMenuRow(
                        label = "Dub",
                        selected = audio == "dub",
                        onClick = {
                            selectedAudioId = "auto"
                            onPickAudio.invoke("dub")
                        },
                    )
                }
                PlayerMenuSectionLabel("Subtitles")
                if (captionsLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Loading subtitles…",
                            color = Color(0xFFB5B5B5),
                            fontSize = 13.sp,
                        )
                    }
                }
                PlayerMenuRow(
                    label = "Off",
                    selected = !subsEnabled || selectedTextId == "off",
                    onClick = {
                        subsEnabled = false
                        selectedTextId = "off"
                    },
                )
                PlayerMenuRow(
                    label = "Auto",
                    selected = subsEnabled && selectedTextId == "auto",
                    onClick = {
                        subsEnabled = true
                        selectedTextId = "auto"
                    },
                )
                textOptions.filter { it.id != "off" && it.id != "auto" }.forEach { opt ->
                    PlayerMenuRow(
                        label = opt.label,
                        selected = subsEnabled && selectedTextId == opt.id,
                        onClick = {
                            subsEnabled = true
                            selectedTextId = opt.id
                        },
                    )
                }
                PlayerMenuSectionLabel("Style")
                SubtitleSizePanel(style = subStyle, onChange = { subStyle = it })
                SubtitleTimingPanel(style = subStyle, onChange = { subStyle = it })
                captionColors().forEach { (color, label) ->
                    PlayerMenuRow(
                        label = label,
                        selected = subStyle.color == color,
                        onClick = { subStyle = subStyle.copy(color = color) },
                    )
                }
                listOf(
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE to "Outline",
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to "Shadow",
                    CaptionStyleCompat.EDGE_TYPE_NONE to "None",
                ).forEach { (edge, label) ->
                    PlayerMenuRow(
                        label = label,
                        selected = subStyle.edgeType == edge,
                        onClick = { subStyle = subStyle.copy(edgeType = edge) },
                    )
                }
                SubtitleBackgroundPanel(style = subStyle, onChange = { subStyle = it })
            }
            PlayerMenuPage.Size -> {
                SubtitleSizePanel(
                    style = subStyle,
                    onChange = { subStyle = it },
                )
            }
            PlayerMenuPage.Timing -> {
                SubtitleTimingPanel(
                    style = subStyle,
                    onChange = { subStyle = it },
                )
            }
            PlayerMenuPage.Volume -> {
                listOf(0 to "Off", 1 to "Low", 2 to "Medium", 3 to "High").forEach { (level, label) ->
                    PlayerMenuRow(
                        label = label,
                        selected = volumeBoost == level,
                        onClick = { volumeBoost = level },
                    )
                }
            }
            PlayerMenuPage.Audio -> {
                if (onPickAudio != null) {
                    PlayerMenuRow(
                        label = "Subtitled",
                        selected = audio != "dub",
                        onClick = {
                            selectedAudioId = "auto"
                            onPickAudio.invoke("sub")
                        },
                    )
                    PlayerMenuRow(
                        label = "Dubbed",
                        selected = audio == "dub",
                        onClick = {
                            selectedAudioId = "auto"
                            onPickAudio.invoke("dub")
                        },
                    )
                }
                val embedded = audioOptions
                if (embedded.isNotEmpty()) {
                    embedded.forEach { opt ->
                        PlayerMenuRow(
                            label = opt.label,
                            selected = selectedAudioId == opt.id ||
                                (selectedAudioId == "auto" && opt.selectedByPlayer),
                            onClick = { selectedAudioId = opt.id },
                        )
                    }
                } else if (onPickAudio == null) {
                    PlayerMenuRow(
                        label = if (audio == "dub") "Dubbed" else "Subtitled",
                        selected = true,
                        onClick = { selectedAudioId = "auto" },
                    )
                }
            }
            PlayerMenuPage.Color -> {
                captionColors().forEach { (color, label) ->
                    PlayerMenuRow(
                        label = label,
                        selected = subStyle.color == color,
                        onClick = { subStyle = subStyle.copy(color = color) },
                    )
                }
            }
            PlayerMenuPage.Edge -> {
                listOf(
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE to "Outline",
                    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to "Shadow",
                    CaptionStyleCompat.EDGE_TYPE_NONE to "None",
                ).forEach { (edge, label) ->
                    PlayerMenuRow(
                        label = label,
                        selected = subStyle.edgeType == edge,
                        onClick = { subStyle = subStyle.copy(edgeType = edge) },
                    )
                }
            }
            PlayerMenuPage.Background -> {
                SubtitleBackgroundPanel(
                    style = subStyle,
                    onChange = { subStyle = it },
                )
            }
            PlayerMenuPage.Resize -> {
                listOf(
                    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Fill",
                    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch",
                ).forEach { (mode, label) ->
                    PlayerMenuRow(
                        label = label,
                        selected = resizeMode == mode,
                        onClick = { resizeMode = mode },
                    )
                }
            }
            PlayerMenuPage.Sleep -> {
                listOf(
                    0 to "Off",
                    15 to "15 minutes",
                    30 to "30 minutes",
                    45 to "45 minutes",
                    -1 to "End of this episode",
                ).forEach { (value, label) ->
                    PlayerMenuRow(
                        label = label,
                        selected = sleepChoice == value,
                        onClick = {
                            sleepNote = null
                            sleepChoice = value
                        },
                    )
                }
            }
            PlayerMenuPage.Episodes -> {
                val fraction = if (durationMs > 0L && shelfMinutes >= 0) {
                    (1f - (shelfMinutes * 60_000f / durationMs.toFloat())).coerceIn(0f, 1f)
                } else {
                    0f
                }
                EpisodeSelectorPanel(
                    episode = episode,
                    totalEpisodes = totalEpisodes.coerceAtLeast(1),
                    watchedThrough = watchedThrough,
                    positionFraction = fraction,
                    slides = displaySlides,
                    fallbackArt = fallbackArt,
                    minutesLeft = shelfMinutes.takeIf { it >= 0 },
                    anilistId = anilistId,
                    onPick = { picked ->
                        // The clicked episode button is the focused node. Removing
                        // the panel in this click disposes it mid-gesture and crashes.
                        if (picked != episode) {
                            onPickEpisode?.invoke(picked)
                        }
                        episodeJumpFocus = false
                        controlsVisible = true
                        downloadScope.launch {
                            yield()
                            panel = PlayerMenuPage.None
                        }
                    },
                    onToggleWatched = onToggleWatched,
                    requestJumpFocus = episodeJumpFocus,
                )
            }
        }
    }

    LaunchedEffect(panel) {
        if (panel != PlayerMenuPage.Episodes) {
            episodeJumpFocus = false
        }
    }

    @Composable
    fun VideoStage(modifier: Modifier) {
        val stageFocus = remember { FocusRequester() }
        LaunchedEffect(television, controlsVisible, panel) {
            if (television && panel == PlayerMenuPage.None && !controlsVisible) {
                awaitTvFocus(stageFocus)
            }
        }
        Box(
        modifier = modifier
            .clipToBounds()
            .background(KittyInk)
            .onFocusChanged { chromeFocused = it.hasFocus }
            .focusRequester(stageFocus)
            .focusProperties { canFocus = panel == PlayerMenuPage.None && !controlsVisible }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val showChrome = {
                    if (!controlsLocked) controlsVisible = true
                }
                when (event.key) {
                    Key.MediaPlay -> {
                        playOnScreen()
                        showChrome()
                        true
                    }
                    Key.MediaPause -> {
                        pauseOnScreen()
                        showChrome()
                        true
                    }
                    Key.MediaPlayPause -> {
                        toggleOnScreen()
                        showChrome()
                        true
                    }
                    Key.MediaRewind, Key.MediaPrevious -> {
                        seekBy(-10_000)
                        true
                    }
                    Key.MediaFastForward, Key.MediaNext -> {
                        seekBy(10_000)
                        true
                    }
                    Key.DirectionLeft -> {
                        if (!controlsVisible) {
                            if (controlsLocked) controlsVisible = true else seekBy(-10_000)
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionRight -> {
                        if (!controlsVisible) {
                            if (controlsLocked) controlsVisible = true else seekBy(10_000)
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (controlsLocked) {
                            controlsLocked = false
                            controlsVisible = true
                            true
                        } else if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .pointerInput(controlsLocked) {
                detectTapGestures(
                    onTap = {
                        if (controlsLocked) {
                            controlsVisible = true
                            return@detectTapGestures
                        }
                        if (!theater) {
                            stageChrome = !stageChrome
                        } else if (panel.isStickyStylePage()) {
                            // Keep Size / Color / … open so captions can be previewed live.
                        } else if (panel != PlayerMenuPage.None) {
                            panel = PlayerMenuPage.None
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = { offset ->
                        if (controlsLocked) return@detectTapGestures
                        // Double-tap either side skips 10 seconds.
                        if (offset.x < size.width / 2f) seekBy(-10_000) else seekBy(10_000)
                    },
                )
            }
            .pointerInput(controlsLocked, panel) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val start = down.position
                    if (panel != PlayerMenuPage.None && !panel.isStickyStylePage()) {
                        // A settings sheet is open: its own scrolling owns the
                        // gesture. Wait out this press without touching volume
                        // or brightness, and without stealing the drag.
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) break
                        }
                        return@awaitEachGesture
                    }
                    val slideLevel = start.x < size.width / 2f
                    var dragged = false
                    var horizontal = false
                    val releasedEarly = withTimeoutOrNull(280L) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull true
                            if (!change.pressed) return@withTimeoutOrNull true
                            val dx = kotlin.math.abs(change.position.x - start.x)
                            val dy = kotlin.math.abs(change.position.y - start.y)
                            if (!controlsLocked && dx > 36f && dx > dy) {
                                dragged = true
                                horizontal = true
                                return@withTimeoutOrNull false
                            }
                            if (dy > 28f && dy > dx) {
                                dragged = true
                                return@withTimeoutOrNull false
                            }
                        }
                    }
                    // Locked: still allow brightness / volume edge swipes; block 2× hold.
                    if (!dragged && releasedEarly == null) {
                        if (controlsLocked) return@awaitEachGesture
                        holdBoost = true
                        swallowCenterClick = true
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) break
                        }
                        holdBoost = false
                    } else if (dragged && horizontal) {
                        gestureDragging = true
                        val origin = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: positionMs
                        val span = (activePlayer?.duration?.takeIf { it > 0L } ?: durationMs).coerceAtLeast(1L)
                        var target = origin
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.pressed }
                            if (change == null) break
                            change.consume()
                            val dx = change.position.x - start.x
                            val deltaMs = (dx / size.width.coerceAtLeast(1).toFloat()) * span.toFloat()
                            target = (origin + deltaMs.toLong()).coerceIn(0L, span)
                            gestureHud = GestureHud(
                                kind = GestureHudKind.Seek,
                                level = target.toFloat() / span.toFloat(),
                                caption = formatPlayerClock(target),
                            )
                        }
                        val landed = safeSeekTarget(target, activePlayer?.duration ?: span, durationMs)
                        activePlayer?.seekTo(landed)
                        positionMs = landed
                        playhead.ms = landed
                        gestureDragging = false
                    } else if (dragged) {
                        gestureDragging = true
                        val activity = context as? Activity
                        val startBrightness = activity?.let { brightnessFraction(it) } ?: 0.5f
                        val startVolume = volumeFraction(context)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.pressed }
                            if (change == null) break
                            val delta = (start.y - change.position.y) / size.height
                            change.consume()
                            if (slideLevel && activity != null) {
                                val level = (startBrightness + delta).coerceIn(0.01f, 1f)
                                applyBrightness(activity, level)
                                gestureHud = GestureHud(
                                    kind = GestureHudKind.Brightness,
                                    level = level,
                                )
                            } else if (isCasting) {
                                val level = castHelper?.adjustDeviceVolume(delta) ?: startVolume
                                if (delta > 0.012f && level > 0.02f && muted) {
                                    muted = false
                                    castHelper?.setTvMuted(false)
                                }
                                gestureHud = GestureHud(
                                    kind = GestureHudKind.Volume,
                                    level = level,
                                    caption = "TV",
                                )
                            } else {
                                val level = (startVolume + delta).coerceIn(0f, 1f)
                                applyVolumeFraction(context, level)
                                gestureHud = GestureHud(
                                    kind = GestureHudKind.Volume,
                                    level = level,
                                )
                            }
                        }
                        gestureDragging = false
                    }
                }
            },
    ) {
        LaunchedEffect(holdBoost) {
            if (holdBoost) return@LaunchedEffect
            delay(350)
            swallowCenterClick = false
        }
        LaunchedEffect(gestureHud, gestureDragging) {
            if (gestureDragging) return@LaunchedEffect
            val shown = gestureHud ?: return@LaunchedEffect
            delay(700)
            if (!gestureDragging && gestureHud == shown) gestureHud = null
        }
        AnimatedVisibility(
            visible = holdBoost,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(999.dp),
                        ambientColor = Color.Black.copy(alpha = 0.4f),
                        spotColor = Color.Black.copy(alpha = 0.3f),
                    )
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .clearAndSetSemantics {
                        contentDescription = "Playing at double speed"
                        liveRegion = LiveRegionMode.Polite
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "2× speed",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.2.sp,
                )
            }
        }
        AnimatedVisibility(
            visible = seekFlashMs != 0L,
            enter = fadeIn(tween(90)),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(if (seekFlashMs < 0L) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 28.dp),
        ) {
            val secs = (kotlin.math.abs(seekFlashMs) / 1000L).toInt().coerceAtLeast(1)
            val label = if (seekFlashMs < 0L) "−${secs}s" else "+${secs}s"
            val flashShape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .shadow(
                        elevation = 16.dp,
                        shape = flashShape,
                        ambientColor = Color.Black.copy(alpha = 0.5f),
                        spotColor = Color.Black.copy(alpha = 0.35f),
                    )
                    .clip(flashShape)
                    .background(Color(0xCC101012))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.28f), flashShape)
                    .clearAndSetSemantics {
                        contentDescription = seekFlashDescription(seekFlashMs)
                        liveRegion = LiveRegionMode.Polite
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    if (seekFlashMs < 0L) Icons.Outlined.Replay10 else Icons.Outlined.Forward10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 0.3.sp,
                )
            }
        }
        if (player != null && error == null && !castScreenUp) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        this.player = player
                        subtitleView?.setApplyEmbeddedStyles(false)
                        applyCaptionStyle(this, subStyle)
                        playerViewRef = this
                    }
                },
                update = {
                    it.player = player
                    it.resizeMode = resizeMode
                    applyCaptionStyle(it, subStyle)
                    playerViewRef = it
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        val switchingAudio = audioSwitchLabel(audio, source?.audioLanguage)
        if (!inPip && showAudioSwitch && switchingAudio != null && !castScreenUp && error == null) {
            Text(
                switchingAudio,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        if (
            !inPip && !theater && error == null && (player != null || castScreenUp) &&
            panel == PlayerMenuPage.None && !showEndAutoplay
        ) {
            val showStageTransport = castScreenUp || !playing || stageChrome
            if (showStageTransport) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (castScreenUp) 72.dp else 64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        .clickable {
                            if (swallowCenterClick) return@clickable
                            val current = if (isCasting) castHelper?.castPlayer else player
                            if (current != null && playbackEnded && onNextEpisode != null && !castHandoffInFlight()) {
                                goNextEpisode()
                            } else {
                                toggleOnScreen()
                            }
                            stageChrome = true
                            if (castScreenUp) controlsVisible = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = centerTransportDescription(
                            playing = playing,
                            ended = playbackEnded,
                            hasNext = onNextEpisode != null,
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(if (castScreenUp) 34.dp else 32.dp),
                    )
                }
            }
            if ((castScreenUp || stageChrome) && !controlsLocked) {
                ChromeDisc(
                    onClick = { seekBy(-10_000) },
                    size = if (castScreenUp) 52.dp else 44.dp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp),
                ) {
                    Icon(
                        Icons.Outlined.Replay10,
                        contentDescription = "Back 10 seconds",
                        tint = Color.White,
                    )
                }
                ChromeDisc(
                    onClick = { seekBy(10_000) },
                    size = if (castScreenUp) 52.dp else 44.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                ) {
                    Icon(
                        Icons.Outlined.Forward10,
                        contentDescription = "Forward 10 seconds",
                        tint = Color.White,
                    )
                }
            }
        }

        if (castScreenUp && (!isCasting || !theater || !controlsVisible || controlsLocked)) {
            CastStatusBadge(
                episode = episode,
                title = title,
                isMovie = isMovie,
                connecting = !isCasting,
                buffering = buffering,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = if (theater) 16.dp else 12.dp, start = 20.dp, end = 20.dp),
            )
        }

        val hud = gestureHud
        var heldHud by remember { mutableStateOf<GestureHud?>(null) }
        if (hud != null) heldHud = hud
        val reducedHud = site.anikitty.app.ui.theme.prefersReducedMotion()
        AnimatedVisibility(
            visible = hud != null,
            modifier = Modifier
                .align(
                when (heldHud?.kind) {
                    GestureHudKind.Brightness -> Alignment.CenterStart
                    GestureHudKind.Seek -> Alignment.Center
                    else -> Alignment.CenterEnd
                },
            )
                .padding(horizontal = 28.dp),
            enter = fadeIn(tween(if (reducedHud) 0 else 140)),
            exit = fadeOut(tween(if (reducedHud) 0 else 180)),
        ) {
            val shown = heldHud ?: return@AnimatedVisibility
            val pct = (shown.level.coerceIn(0f, 1f) * 100f).toInt()
            val hudShape = RoundedCornerShape(18.dp)
            val hudDescription = when {
                shown.kind == GestureHudKind.Seek -> "Seek to ${shown.caption ?: "$pct percent"}"
                shown.kind == GestureHudKind.Brightness -> "Brightness $pct percent"
                shown.caption != null -> "${shown.caption} volume $pct percent"
                else -> "Volume $pct percent"
            }
            if (shown.kind == GestureHudKind.Seek) {
                Row(
                    modifier = Modifier
                        .shadow(
                            elevation = 14.dp,
                            shape = hudShape,
                            ambientColor = Color.Black.copy(alpha = 0.45f),
                            spotColor = Color.Black.copy(alpha = 0.3f),
                        )
                        .clip(hudShape)
                        .background(Color(0xCC101012))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.28f), hudShape)
                        .clearAndSetSemantics {
                            contentDescription = hudDescription
                            liveRegion = LiveRegionMode.Polite
                        }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Forward10,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            shown.caption ?: "$pct%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.16f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(shown.level.coerceIn(0f, 1f))
                                    .background(Color.White),
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .shadow(
                            elevation = 14.dp,
                            shape = hudShape,
                            ambientColor = Color.Black.copy(alpha = 0.45f),
                            spotColor = Color.Black.copy(alpha = 0.3f),
                        )
                        .clip(hudShape)
                        .background(Color(0xCC101012))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.28f), hudShape)
                        .clearAndSetSemantics {
                            contentDescription = hudDescription
                            liveRegion = LiveRegionMode.Polite
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        when {
                            shown.kind == GestureHudKind.Brightness && shown.level < 0.34f ->
                                Icons.Outlined.BrightnessLow
                            shown.kind == GestureHudKind.Brightness && shown.level < 0.67f ->
                                Icons.Outlined.BrightnessMedium
                            shown.kind == GestureHudKind.Brightness ->
                                Icons.Outlined.BrightnessHigh
                            shown.level <= 0.01f -> Icons.AutoMirrored.Outlined.VolumeOff
                            shown.level < 0.45f -> Icons.AutoMirrored.Outlined.VolumeDown
                            else -> Icons.AutoMirrored.Outlined.VolumeUp
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(118.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.14f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(shown.level.coerceIn(0f, 1f))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.72f), Color.White),
                                    ),
                                ),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (shown.caption != null) {
                        Text(
                            shown.caption,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        "$pct%",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        if (!inPip && !ready && error == null && !castScreenUp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = PlayerChrome.Accent)
                if (sourceIndex > 0 && playlist.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Source ${sourceIndex + 1} of ${playlist.size}",
                        color = PlayerChrome.Paper,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else if (
            !inPip && ready && buffering && error == null &&
            seekFlashMs == 0L && !scrubbing && !holdBoost &&
            !(theater && controlsVisible && !controlsLocked) &&
            !(!theater && castScreenUp)
        ) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.9f),
                strokeWidth = 2.5.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
            )
        }

        if (!inPip && sleepNote != null) {
            Text(
                sleepNote!!,
                color = PlayerChrome.Paper,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 72.dp, start = 20.dp, end = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlayerChrome.Sheet)
                    .border(1.dp, PlayerChrome.SheetStroke, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        if (!inPip && castHint != null) {
            Text(
                castHint!!,
                color = PlayerChrome.Paper,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 72.dp, start = 20.dp, end = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlayerChrome.Sheet)
                    .border(1.dp, PlayerChrome.SheetStroke, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        if (!inPip && (nearEndOffer || showEndAutoplay)) {
            val playNowFocus = remember { FocusRequester() }
            LaunchedEffect(television, showEndAutoplay) {
                if (television && showEndAutoplay) awaitTvFocus(playNowFocus)
            }
            NextEpisodePrompt(
                modifier = Modifier
                    .align(if (showEndAutoplay) Alignment.Center else Alignment.BottomStart)
                    .then(
                        if (showEndAutoplay) {
                            Modifier.padding(28.dp)
                        } else {
                            Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(start = 18.dp, bottom = 128.dp, end = 160.dp)
                        },
                    ),
                nextEpisode = episode + 1,
                ended = showEndAutoplay,
                secondsLeft = autoplaySecLeft,
                player = activePlayer,
                durationMs = durationMs,
                busy = nextTriggered,
                onPlayNow = { goNextEpisode() },
                playNowFocus = if (television && showEndAutoplay) playNowFocus else null,
                onDismiss = {
                    if (showEndAutoplay) {
                        autoplayCancelled = true
                    } else {
                        nearEndDismissed = true
                    }
                },
            )
        }

        if (refreshingSources && error == null) {
            Text(
                "Looking for a fresh stream…",
                color = PlayerChrome.Paper,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (error != null) {
            val errorView = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.LaunchedEffect(error) {
                site.anikitty.app.ui.theme.KittyHaptics.perform(
                    errorView,
                    site.anikitty.app.ui.theme.KittyHaptics.Kind.Error,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                site.anikitty.app.ui.components.KittyFace(
                    modifier = Modifier.size(72.dp),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Couldn't play this source",
                    color = PlayerChrome.Paper,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error!!.take(160),
                    color = PlayerChrome.Graphite,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                val retryFocus = remember { FocusRequester() }
                val retryShape = RoundedCornerShape(12.dp)
                LaunchedEffect(television, error) {
                    if (television && error != null) awaitTvFocus(retryFocus)
                }
                Text(
                    "Try again",
                    color = PlayerChrome.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .focusRequester(retryFocus)
                        .playerFocusCue(retryShape)
                        .clip(retryShape)
                        .background(PlayerChrome.Accent)
                        .clickable {
                            error = null
                            ready = false
                            playAttempt++
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (!inPip && controlsLocked) {
            val unlockFocus = remember { FocusRequester() }
            val unlockShape = RoundedCornerShape(999.dp)
            LaunchedEffect(television, controlsLocked) {
                if (television && controlsLocked) awaitTvFocus(unlockFocus)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 16.dp)
                    .focusRequester(unlockFocus)
                    .playerFocusCue(unlockShape)
                    .clip(unlockShape)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), unlockShape)
                    .clickable {
                        controlsLocked = false
                        controlsVisible = true
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.LockOpen,
                    contentDescription = "Unlock controls",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (television) "Controls locked · select to unlock" else "Controls locked · tap to unlock",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        AnimatedVisibility(
            visible = !inPip && theater && !controlsLocked && (controlsVisible || error != null || panel != PlayerMenuPage.None),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val phoneChrome = !television && !castScreenUp && panel == PlayerMenuPage.None
                if (phoneChrome) {
                    val res = shortQuality(qualityLabel)
                    val mode = if (audio == "dub") "DUB" else "SUB"
                    AniFusePlayerChrome(
                        title = title,
                        episodeLine = if (isMovie) "Movie" else "Episode $episode",
                        statusPill = "$res · $mode",
                        playing = playing,
                        speedLabel = speedLabel(playbackSpeed),
                        showEpisodes = !isMovie && onPickEpisode != null && totalEpisodes > 1,
                        showNext = onNextEpisode != null && episode < totalEpisodes,
                        castUnavailable = castRouteReason,
                        onCastUnavailable = { castHint = it },
                        player = activePlayer,
                        scrubbing = scrubbing,
                        scrubValue = scrubValue,
                        durationMs = durationMs,
                        skipTimes = skipTimes,
                        thumbnails = source?.thumbnails,
                        mediaUrl = source?.url,
                        onBack = { leaveTheater() },
                        onLock = {
                            controlsLocked = true
                            panel = PlayerMenuPage.None
                            controlsVisible = false
                        },
                        onSettings = {
                            panel = PlayerMenuPage.Quality
                            controlsVisible = true
                        },
                        onPlayPause = {
                            if (!swallowCenterClick) toggleOnScreen()
                            controlsVisible = true
                        },
                        onSeekBack = { seekBy(-10_000) },
                        onSeekForward = { seekBy(10_000) },
                        onEpisodes = {
                            panel = PlayerMenuPage.Episodes
                            controlsVisible = true
                        },
                        onSources = {
                            panel = PlayerMenuPage.Quality
                            controlsVisible = true
                        },
                        onSubtitles = {
                            panel = PlayerMenuPage.Subs
                            controlsVisible = true
                        },
                        onSpeed = {
                            panel = PlayerMenuPage.Speed
                            controlsVisible = true
                        },
                        onNext = { goNextEpisode() },
                        onScrub = {
                            scrubbing = true
                            scrubValue = it
                            controlsVisible = true
                        },
                        onScrubFinished = { fraction ->
                            val p = activePlayer
                            val raw = (fraction * durationMs).toLong()
                            val target = if (p != null) safeSeekTarget(raw, p.duration, durationMs) else raw.coerceAtLeast(0L)
                            p?.seekTo(target)
                            playhead.ms = target
                            positionMs = target
                            scrubbing = false
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (television) 96.dp else 160.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            ),
                        ),
                )

                // Title strip — only when settings sheet is closed.
                if (panel == PlayerMenuPage.None && (television || castScreenUp)) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChromeDisc(onClick = { leaveTheater() }, size = 44.dp) {
                            Icon(
                                if (television) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.FullscreenExit,
                                contentDescription = if (television) "Close" else "Exit full screen",
                                tint = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when {
                                    isCasting && isMovie -> "Casting · Movie"
                                    isCasting -> "Casting · Episode $episode of $totalEpisodes"
                                    isMovie -> "Movie"
                                    else -> "Episode $episode of $totalEpisodes"
                                },
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                        if (!isMovie && onPickEpisode != null && episode > 1) {
                            ChromeDisc(
                                onClick = { goPreviousEpisode() },
                                size = 44.dp,
                            ) {
                                Icon(
                                    Icons.Outlined.SkipPrevious,
                                    contentDescription = "Previous episode",
                                    tint = Color.White,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (onNextEpisode != null && episode < totalEpisodes) {
                            ChromeDisc(onClick = { goNextEpisode() }, size = 44.dp) {
                                Icon(
                                    Icons.Outlined.SkipNext,
                                    contentDescription = "Next episode",
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    // Hidden while the ended up-next card owns the center.
                    if (!showEndAutoplay && !television && castScreenUp) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .playerFocusCue(CircleShape)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable {
                                if (swallowCenterClick) return@clickable
                                val current = if (isCasting) castHelper?.castPlayer else player
                                if (current != null && playbackEnded && onNextEpisode != null && !castHandoffInFlight()) {
                                    goNextEpisode()
                                } else {
                                    toggleOnScreen()
                                }
                                controlsVisible = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = centerTransportDescription(
                            playing = playing,
                            ended = playbackEnded,
                            hasNext = onNextEpisode != null,
                        ),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    }
                }

                if (television || castScreenUp || panel != PlayerMenuPage.None) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.union(WindowInsets.displayCutout),
                        )
                        .padding(horizontal = if (television) 36.dp else 22.dp)
                        .padding(top = if (television) 8.dp else 18.dp, bottom = if (television) 14.dp else 20.dp),
                ) {
                    LiveTimeline(
                        player = activePlayer,
                        scrubbing = scrubbing,
                        scrubValue = scrubValue,
                        durationMs = durationMs,
                        skipTimes = skipTimes,
                        thumbnails = source?.thumbnails,
                        mediaUrl = source?.url,
                        onScrub = {
                            scrubbing = true
                            scrubValue = it
                            controlsVisible = true
                        },
                        onScrubFinished = { fraction ->
                            val p = activePlayer
                            val raw = (fraction * durationMs).toLong()
                            val target = if (p != null) {
                                safeSeekTarget(raw, p.duration, durationMs)
                            } else {
                                raw.coerceAtLeast(0L)
                            }
                            p?.seekTo(target)
                            playhead.ms = target
                            positionMs = target
                            scrubbing = false
                        },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (castScreenUp) {
                        CastTransportRow(
                            playing = playing,
                            muted = muted,
                            player = if (isCasting) castHelper?.castPlayer else null,
                            scrubbing = scrubbing,
                            scrubValue = scrubValue,
                            durationMs = durationMs,
                            showEpisodes = !isMovie && onPickEpisode != null && totalEpisodes > 1,
                            castUnavailable = castRouteReason,
                            onCastUnavailable = { castHint = it },
                            speedLabel = speedLabel(playbackSpeed),
                            onSpeed = {
                                panel = if (panel == PlayerMenuPage.Speed) {
                                    PlayerMenuPage.None
                                } else {
                                    PlayerMenuPage.Speed
                                }
                                controlsVisible = true
                            },
                            onPlayPause = {
                                toggleOnScreen()
                                controlsVisible = true
                            },
                            onMute = { toggleMute() },
                            onSeekBack = { seekBy(-10_000) },
                            onSeekForward = { seekBy(10_000) },
                            onEpisodes = {
                                if (panel == PlayerMenuPage.Episodes) {
                                    panel = PlayerMenuPage.None
                                    episodeJumpFocus = false
                                } else {
                                    episodeJumpFocus = false
                                    panel = PlayerMenuPage.Episodes
                                }
                                controlsVisible = true
                            },
                            onSettings = {
                                panel = if (panel == PlayerMenuPage.None) {
                                    PlayerMenuPage.Root
                                } else {
                                    PlayerMenuPage.None
                                }
                                controlsVisible = true
                            },
                            onExitTheater = { leaveTheater() },
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val playFocus = remember { FocusRequester() }
                            LaunchedEffect(television, controlsVisible, panel, controlsLocked, showEndAutoplay) {
                                if (
                                    television &&
                                    controlsVisible &&
                                    !controlsLocked &&
                                    panel == PlayerMenuPage.None &&
                                    !showEndAutoplay
                                ) {
                                    awaitTvFocus(playFocus)
                                }
                            }
                            PlayerCapsule {
                                PillIcon(
                                    icon = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    description = if (playing) "Pause" else "Play",
                                    focusRequester = if (television) playFocus else null,
                                    onClick = {
                                        toggleOnScreen()
                                        controlsVisible = true
                                    },
                                )
                                PillIcon(
                                    icon = if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                                    description = if (muted) "Unmute" else "Mute",
                                    onClick = { toggleMute() },
                                )
                                LiveClockText(
                                    player = activePlayer,
                                    scrubbing = scrubbing,
                                    scrubValue = scrubValue,
                                    durationMs = durationMs,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                            PlayerCapsule {
                                PillIcon(
                                    icon = Icons.Outlined.Replay10,
                                    description = "Back 10 seconds",
                                    onClick = { seekBy(-10_000) },
                                )
                                PillIcon(
                                    icon = Icons.Outlined.Forward10,
                                    description = "Forward 10 seconds",
                                    onClick = { seekBy(10_000) },
                                )
                                PillLabel(
                                    label = "+85",
                                    description = "Skip ahead 85 seconds",
                                    onClick = { seekBy(85_000) },
                                )
                                PillLabel(
                                    label = speedLabel(playbackSpeed),
                                    description = "Play speed",
                                    onClick = {
                                        panel = if (panel == PlayerMenuPage.Speed) {
                                            PlayerMenuPage.None
                                        } else {
                                            PlayerMenuPage.Speed
                                        }
                                        controlsVisible = true
                                    },
                                )
                                val compactPills = LocalConfiguration.current.screenWidthDp < 840
                                if (!isMovie && onPickEpisode != null && totalEpisodes > 1) {
                                    PillIcon(
                                        icon = Icons.Outlined.VideoLibrary,
                                        description = "Episodes",
                                        onClick = {
                                            if (panel == PlayerMenuPage.Episodes) {
                                                panel = PlayerMenuPage.None
                                                episodeJumpFocus = false
                                            } else {
                                                episodeJumpFocus = false
                                                panel = PlayerMenuPage.Episodes
                                            }
                                            controlsVisible = true
                                        },
                                    )
                                }
                                if (!compactPills) {
                                    PillIcon(
                                        icon = Icons.Outlined.PhotoCamera,
                                        description = "Screenshot",
                                        onClick = { capturePlayerFrame(context, playerViewRef) },
                                    )
                                }
                                PillIcon(
                                    icon = Icons.Outlined.Settings,
                                    description = "Settings",
                                    onClick = {
                                        panel = if (panel == PlayerMenuPage.None) {
                                            PlayerMenuPage.Root
                                        } else {
                                            PlayerMenuPage.None
                                        }
                                        controlsVisible = true
                                    },
                                )
                                if (!compactPills && !television) {
                                    PillIcon(
                                        icon = Icons.Outlined.PictureInPicture,
                                        description = "Picture in picture",
                                        onClick = { enterPip() },
                                    )
                                }
                                if (!television) {
                                    PillIcon(
                                        icon = Icons.Outlined.FullscreenExit,
                                        description = "Exit full screen",
                                        onClick = { leaveTheater() },
                                    )
                                }
                                CastRouteButton(
                                    unavailable = castRouteReason,
                                    onUnavailable = { castHint = it },
                                    modifier = Modifier.size(48.dp),
                                )
                            }
                        }
                    }
                }
                }

                if (panel != PlayerMenuPage.None) {
                    PlayerMenuFrame(
                        theater = true,
                        page = panel,
                        onBack = { panel = PlayerMenuPage.Root },
                        onDismiss = { panel = PlayerMenuPage.None },
                        focusGate = menuFocusGate,
                    ) {
                        if (!television && panel != PlayerMenuPage.Root) {
                            PlayerDrawerTabs(page = panel, onSelect = { panel = it })
                        }
                        PanelContents()
                    }
                }
            }
        }

        if (!inPip && showGestureHint) {
            PlayerGestureHint(
                modifier = Modifier.align(Alignment.Center),
                onDismiss = {
                    showGestureHint = false
                    playerPrefs.edit().putBoolean("gesture_hint_seen", true).apply()
                },
            )
        }
        if (!inPip && activeSkip != null && panel == PlayerMenuPage.None) {
            val (kind, iv) = activeSkip
            val label = when (kind) {
                "intro" -> "Skip Intro"
                "credits" -> "Skip Outro"
                "recap" -> "Skip Recap"
                "preview" -> "Skip Preview"
                else -> "Skip"
            }
            val chromeUp = theater && controlsVisible && !controlsLocked
            val avoidNextCard = nearEndOffer || showEndAutoplay
            val skipShape = RoundedCornerShape(if (isCasting) 14.dp else 12.dp)
            Text(
                label,
                color = Color.White,
                fontSize = if (isCasting) 15.sp else 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier
                    .align(if (!television && chromeUp) Alignment.BottomEnd else if (avoidNextCard) Alignment.BottomEnd else Alignment.BottomStart)
                    .then(
                        if (theater) {
                            Modifier.windowInsetsPadding(
                                WindowInsets.navigationBars.union(WindowInsets.displayCutout),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        start = if (avoidNextCard) 0.dp else 22.dp,
                        end = if (avoidNextCard) 22.dp else 0.dp,
                        bottom = if (chromeUp) {
                            if (isCasting) 128.dp else 118.dp
                        } else {
                            22.dp
                        },
                    )
                    .clip(skipShape)
                    .playerFocusCue(skipShape)
                    .background(Color.Black.copy(alpha = if (isCasting) 0.72f else 0.62f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), skipShape)
                    .clickable(role = Role.Button) { skipTo(iv.endMs) }
                    .clearAndSetSemantics { contentDescription = skipActionDescription(kind) }
                    .padding(
                        horizontal = if (isCasting) 18.dp else 16.dp,
                        vertical = if (isCasting) 12.dp else 10.dp,
                    ),
            )
        }
    }
    }

    var dismissY by remember { mutableFloatStateOf(0f) }
    val dismissScope = rememberCoroutineScope()
    fun Modifier.dismissDrag(): Modifier = pointerInput(onDismiss) {
        detectVerticalDragGestures(
            onVerticalDrag = { change, drag ->
                if (drag > 0f || dismissY > 0f) {
                    change.consume()
                    dismissY = (dismissY + drag).coerceAtLeast(0f)
                }
            },
            onDragEnd = {
                if (dismissY > 160f) {
                    onDismiss()
                } else {
                    val from = dismissY
                    dismissScope.launch {
                        animate(from, 0f) { value, _ -> dismissY = value }
                    }
                }
            },
            onDragCancel = {
                val from = dismissY
                dismissScope.launch {
                    animate(from, 0f) { value, _ -> dismissY = value }
                }
            },
        )
    }
    val playerPalette = KittyDarkPalette.copy(
        accent = LookState.accent,
        onAccent = LookState.onAccent(),
    )
    CompositionLocalProvider(LocalKittyPalette provides playerPalette) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { androidx.compose.ui.unit.IntOffset(0, dismissY.toInt()) }
            .background(Color(0xFF0B0C0E)),
    ) {
        val currentEpisodeTitle = if (isMovie) {
            null
        } else {
            displaySlides.firstOrNull { it.number == episode }?.title
                ?.takeIf { it.isNotBlank() && !it.equals("Episode $episode", ignoreCase = true) }
        }
        val barTitle = currentEpisodeTitle ?: title
        val barLine = when {
            isMovie -> "Movie"
            currentEpisodeTitle != null -> "Episode $episode · $title"
            else -> "Episode $episode"
        }
        if (!theater) {
            PlayerPageBar(
                title = barTitle,
                episodeLine = barLine,
                onBack = onClose,
                modifier = Modifier.dismissDrag(),
            )
        }
        val shelfFraction = if (durationMs > 0L && shelfMinutes >= 0) {
            (1f - (shelfMinutes * 60_000f / durationMs.toFloat())).coerceIn(0f, 1f)
        } else {
            0f
        }
        if (theater) {
            VideoStage(modifier = stageModifier.then(Modifier.weight(1f)))
        } else {
            Box(
                modifier = stageModifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                VideoStage(modifier = Modifier.matchParentSize())
                if (durationMs > 0L && controlsVisible) {
                    // Transport inside the frame, like the reference page; it
                    // hides with the rest of the chrome so subtitles stay clear.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                                ),
                            )
                            .padding(start = 16.dp, end = 12.dp, top = 34.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        LiveTimeline(
                            player = activePlayer,
                            scrubbing = scrubbing,
                            scrubValue = scrubValue,
                            durationMs = durationMs,
                            skipTimes = skipTimes,
                            thumbnails = source?.thumbnails,
                            mediaUrl = source?.url,
                            onScrub = {
                                scrubbing = true
                                scrubValue = it
                            },
                            onScrubFinished = { fraction ->
                                val p = activePlayer
                                val raw = (fraction * durationMs).toLong()
                                val target = if (p != null) {
                                    safeSeekTarget(raw, p.duration, durationMs)
                                } else {
                                    raw.coerceAtLeast(0L)
                                }
                                p?.seekTo(target)
                                playhead.ms = target
                                positionMs = target
                                scrubbing = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LiveClockText(
                                player = activePlayer,
                                scrubbing = scrubbing,
                                scrubValue = scrubValue,
                                durationMs = durationMs,
                                modifier = Modifier.weight(1f),
                            )
                            ChromeDisc(
                                onClick = { subsEnabled = !subsEnabled },
                                size = 36.dp,
                            ) {
                                Icon(
                                    Icons.Outlined.ClosedCaption,
                                    contentDescription = if (subsEnabled) {
                                        "Turn subtitles off"
                                    } else {
                                        "Turn subtitles on"
                                    },
                                    tint = if (subsEnabled) {
                                        LookState.accent
                                    } else {
                                        Color.White.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CastRouteButton(
                                    unavailable = castRouteReason,
                                    onUnavailable = { castHint = it },
                                )
                            }
                            ChromeDisc(onClick = { enterTheater() }, size = 36.dp) {
                                Icon(
                                    Icons.Rounded.Fullscreen,
                                    contentDescription = "Full screen",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (!theater) {
            PlayerEpisodeShelf(
                title = title,
                episodeTitle = currentEpisodeTitle,
                episode = episode,
                totalEpisodes = totalEpisodes.coerceAtLeast(1),
                watchedThrough = watchedThrough,
                positionFraction = shelfFraction,
                audio = audio,
                isMovie = isMovie,
                autoNext = autoNext,
                onPickEpisode = { picked -> onPickEpisode?.invoke(picked) },
                onToggleAutoNext = {
                    autoNext = !autoNext
                    playerPrefs.edit().putBoolean("auto_next", autoNext).apply()
                },
                episodeSlides = displaySlides,
                anilistId = anilistId,
                fallbackArt = fallbackArt,
                minutesLeft = shelfMinutes.takeIf { it >= 0 },
                onToggleWatched = onToggleWatched,
                sourceLabel = source?.serverName?.takeIf { it.isNotBlank() }
                    ?: source?.qualityRating?.takeIf { it.isNotBlank() }
                    ?: "Auto",
                qualityLabel = shortQuality(qualityLabel),
                qualitySelected = panel == PlayerMenuPage.Quality && !sourcePanelFocus,
                onQuality = {
                    if (panel == PlayerMenuPage.Quality && !sourcePanelFocus) {
                        panel = PlayerMenuPage.None
                    } else {
                        sourcePanelFocus = false
                        panel = PlayerMenuPage.Quality
                    }
                    controlsVisible = true
                },
                sourceSelected = panel == PlayerMenuPage.Quality && sourcePanelFocus,
                onSource = {
                    if (panel == PlayerMenuPage.Quality && sourcePanelFocus) {
                        sourcePanelFocus = false
                        panel = PlayerMenuPage.None
                    } else {
                        sourcePanelFocus = true
                        panel = PlayerMenuPage.Quality
                    }
                    controlsVisible = true
                },
                audioSelected = panel == PlayerMenuPage.Audio,
                onAudio = {
                    sourcePanelFocus = false
                    panel = if (panel == PlayerMenuPage.Audio) {
                        PlayerMenuPage.None
                    } else {
                        PlayerMenuPage.Audio
                    }
                    controlsVisible = true
                },
                onMore = {
                    sourcePanelFocus = false
                    panel = if (panel == PlayerMenuPage.None) PlayerMenuPage.Root else PlayerMenuPage.None
                    controlsVisible = true
                },
                onEpisodes = {
                    if (panel == PlayerMenuPage.Episodes) {
                        panel = PlayerMenuPage.None
                        episodeJumpFocus = false
                    } else {
                        episodeJumpFocus = true
                        panel = PlayerMenuPage.Episodes
                    }
                    controlsVisible = true
                },
                onShare = {
                    if (anilistId > 0) {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "https://anilist.co/anime/$anilistId",
                            )
                            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                        }
                        runCatching {
                            context.startActivity(
                                android.content.Intent.createChooser(send, "Share")
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                },
                shareEnabled = anilistId > 0,
                downloadEnabled = download?.canStart == true,
                onDownload = {
                    val current = source
                    if (current != null && download?.canStart == true) {
                        downloadScope.launch {
                            EpisodeDownloads.enqueue(context, anilistId, episode, title, current)
                        }
                    }
                },
                onRemoveDownload = if (download?.canRemove == true) {
                    {
                        val row = savedRow
                        if (row != null) downloadScope.launch { EpisodeDownloads.remove(context, row) }
                    }
                } else {
                    null
                },
                modifier = Modifier.weight(1f).dismissDrag(),
            )
        }
    }
    if (!theater && panel != PlayerMenuPage.None) {
        PlayerMenuFrame(
            theater = false,
            page = panel,
            onBack = { panel = PlayerMenuPage.Root },
            onDismiss = { panel = PlayerMenuPage.None },
        ) {
            PanelContents()
        }
    }
    }
    }
}

@Composable
private fun ShelfAction(
    label: String,
    enabled: Boolean,
    selected: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val filled = selected
    val tint = when {
        filled -> Color.Black
        enabled -> Color.White.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.3f)
    }
    Box(
        modifier = Modifier
            .height(36.dp)
            .then(if (enabled) Modifier.spacePress(onClick) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) Color.White else Color(0xFF0C0C10))
            .border(
                0.5.dp,
                if (filled) Color.White else Color.White.copy(alpha = if (enabled) 0.08f else 0.04f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
        }
    }
}

@OptIn(UnstableApi::class)
private fun subtitleConfig(
    track: SubtitleTrack,
    default: Boolean = false,
): MediaItem.SubtitleConfiguration? {
    val uri = track.playableUri() ?: return null
    val mime = track.resolvedMime()
    return MediaItem.SubtitleConfiguration.Builder(uri)
        .setMimeType(mime)
        .setLanguage(track.language ?: "en")
        .setLabel(track.label)
        .setSelectionFlags(if (default) C.SELECTION_FLAG_DEFAULT else 0)
        .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
        .build()
}

private data class PlayerBundle(
    val player: ExoPlayer,
    val trackSelector: DefaultTrackSelector,
    val pipeline: PlaybackPipeline,
)

private data class PlayerClock(
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val state: Int,
    val playWhenReady: Boolean,
    val bufferedMs: Long = 0L,
)

private fun playerClock(player: Player): PlayerClock = PlayerClock(
    positionMs = player.currentPosition.coerceAtLeast(0L),
    durationMs = player.duration,
    playing = player.isPlaying,
    state = player.playbackState,
    playWhenReady = player.playWhenReady,
    bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
)

@OptIn(UnstableApi::class)
private fun buildTextOptions(tracks: Tracks, external: List<SubtitleTrack>): List<TextOption> {
    val out = ArrayList<TextOption>()
    out += TextOption(id = "off", label = "Off")
    out += TextOption(id = "auto", label = "Auto")
    var externalIdx = 0
    val usedIds = HashSet<String>()
    tracks.groups.forEachIndexed { _, group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
        for (ti in 0 until group.length) {
            if (!group.isTrackSupported(ti)) continue
            val format: Format = group.getTrackFormat(ti)
            val label = format.label
                ?: format.language?.uppercase(Locale.US)
                ?: external.getOrNull(externalIdx)?.label
                ?: "Track ${out.size - 1}"
            externalIdx++
            var id = stableTextId(format.language, label)
            if (!usedIds.add(id)) {
                id = "$id#$ti"
                usedIds += id
            }
            out += TextOption(
                id = id,
                label = label,
                mediaGroup = group.mediaTrackGroup,
                trackIndex = ti,
            )
        }
    }
    return out
}

/** Stable across quality switches (group indices are not). */
private fun stableTextId(language: String?, label: String): String {
    val lang = language?.trim()?.lowercase(Locale.US).orEmpty()
    val normLabel = label.trim().lowercase(Locale.US)
    return when {
        lang.isNotBlank() && lang != "und" -> "lang:$lang|$normLabel"
        else -> "label:$normLabel"
    }
}

/** Off / Auto plus one row per sidecar, for the cast caption menu. */
private fun sidecarTextOptions(external: List<site.anikitty.app.data.servers.SubtitleTrack>): List<TextOption> {
    if (external.isEmpty()) return emptyList()
    return listOf(
        TextOption(id = "off", label = "Off"),
        TextOption(id = "auto", label = "Auto"),
    ) + external.map { track ->
        TextOption(
            id = stableTextId(track.language, track.label),
            label = track.label.ifBlank { "Subtitles" },
        )
    }
}

/** Language code embedded in [stableTextId], or null for off/auto/label-only. */
private fun languageFromTextOptionId(id: String?): String? {
    if (id.isNullOrBlank() || id == "off" || id == "auto") return null
    if (!id.startsWith("lang:")) return null
    val lang = id.removePrefix("lang:").substringBefore('|').trim().lowercase(Locale.US)
    return lang.takeIf { it.isNotBlank() && it != "und" }
}

private fun rematchTextId(previous: String, options: List<TextOption>): String {
    if (previous == "off" || previous == "auto") return previous
    if (options.any { it.id == previous }) return previous
    val lang = previous.removePrefix("lang:").substringBefore("|").takeIf {
        previous.startsWith("lang:")
    }
    if (!lang.isNullOrBlank()) {
        options.firstOrNull { it.id.startsWith("lang:$lang") }?.id?.let { return it }
    }
    val labelKey = when {
        previous.startsWith("label:") -> previous.removePrefix("label:")
        previous.startsWith("lang:") -> previous.substringAfter("|", "")
        else -> previous
    }
    if (labelKey.isNotBlank()) {
        options.firstOrNull {
            it.id == "label:$labelKey" || it.label.equals(labelKey, ignoreCase = true)
        }?.id?.let { return it }
    }
    return "auto"
}

@OptIn(UnstableApi::class)
private fun buildVideoOptions(tracks: Tracks): List<VideoOption> {
    data class Cand(val height: Int, val width: Int, val group: TrackGroup, val index: Int, val bitrate: Int)
    val cands = ArrayList<Cand>()
    for (i in 0 until tracks.groups.size) {
        val group = tracks.groups[i]
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        val media = group.mediaTrackGroup
        for (t in 0 until group.length) {
            if (!group.isTrackSupported(t)) continue
            val format = group.getTrackFormat(t)
            val height = format.height
            if (height <= 0) continue
            cands += Cand(
                height = height,
                width = format.width.coerceAtLeast(0),
                group = media,
                index = t,
                bitrate = format.bitrate,
            )
        }
    }
    if (cands.isEmpty()) {
        return listOf(VideoOption(id = "auto", label = "Auto (best available)"))
    }
    // One row per unique height — keep the highest-bitrate variant for that height.
    val bestByHeight = cands
        .groupBy { it.height }
        .map { (_, list) -> list.maxBy { it.bitrate } }
        .sortedByDescending { it.height }
    val out = ArrayList<VideoOption>(bestByHeight.size + 1)
    out += VideoOption(id = "auto", label = "Auto (best available)")
    for (c in bestByHeight) {
        val label = when {
            c.height >= 2160 -> "2160p · 4K"
            c.height >= 1440 -> "1440p · QHD"
            c.height >= 1080 -> "1080p · Full HD"
            c.height >= 720 -> "720p · HD"
            c.height >= 480 -> "480p"
            else -> "${c.height}p"
        }
        out += VideoOption(
            id = "${c.height}p",
            label = label,
            height = c.height,
            width = c.width,
            mediaGroup = c.group,
            trackIndex = c.index,
        )
    }
    return out
}

private fun rematchVideoId(previous: String, options: List<VideoOption>): String {
    if (previous == "auto") return "auto"
    if (options.any { it.id == previous }) return previous
    val height = previous.removeSuffix("p").toIntOrNull()
    if (height != null) {
        options.firstOrNull { it.height == height }?.id?.let { return it }
        // Closest lower-or-equal height if the exact rung disappeared after a source switch.
        options.filter { it.height in 1..height }.maxByOrNull { it.height }?.id?.let { return it }
    }
    return "auto"
}

/** Language, name, and channel layout for one in-file audio track. */
internal fun audioTrackLabel(
    language: String?,
    label: String?,
    channelCount: Int,
    index: Int,
): String {
    val named = label?.trim().orEmpty()
    val lang = language?.trim()?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
    val channels = when (channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        in 6..8 -> "Surround"
        else -> null
    }
    val base = when {
        named.isNotEmpty() && lang != null && !named.equals(lang, ignoreCase = true) ->
            "$named · ${lang.uppercase(Locale.US)}"
        named.isNotEmpty() -> named
        lang != null -> lang.uppercase(Locale.US)
        else -> "Track $index"
    }
    return if (channels != null && !base.contains(channels, ignoreCase = true)) "$base · $channels" else base
}

@OptIn(UnstableApi::class)
internal fun buildAudioOptions(tracks: Tracks): List<AudioOption> {
    val out = ArrayList<AudioOption>()
    val used = HashSet<String>()
    tracks.groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEach
        for (ti in 0 until group.length) {
            if (!group.isTrackSupported(ti)) continue
            val format = group.getTrackFormat(ti)
            val label = audioTrackLabel(format.language, format.label, format.channelCount, out.size + 1)
            var id = stableTextId(format.language, label)
            if (!used.add(id)) {
                id = "$id#$ti"
                used += id
            }
            out += AudioOption(
                id = id,
                label = label,
                mediaGroup = group.mediaTrackGroup,
                trackIndex = ti,
                selectedByPlayer = group.isTrackSelected(ti),
            )
        }
    }
    return out
}

internal fun rematchAudioId(previous: String, options: List<AudioOption>): String {
    if (previous == "auto") return "auto"
    if (options.any { it.id == previous }) return previous
    return "auto"
}

@OptIn(UnstableApi::class)
private fun applyAudioSelection(
    selector: DefaultTrackSelector,
    selectedId: String,
    options: List<AudioOption>,
) {
    val params = selector.buildUponParameters()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
    if (selectedId == "auto") {
        selector.parameters = params.build()
        return
    }
    val concrete = options.firstOrNull { it.id == selectedId && it.mediaGroup != null }
    selector.parameters = if (concrete?.mediaGroup != null) {
        params.setOverrideForType(
            TrackSelectionOverride(concrete.mediaGroup, listOf(concrete.trackIndex)),
        ).build()
    } else {
        params.build()
    }
}

@OptIn(UnstableApi::class)
private fun applyVideoSelection(
    selector: DefaultTrackSelector,
    selectedId: String,
    options: List<VideoOption>,
) {
    val params = selector.buildUponParameters()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    if (selectedId == "auto") {
        selector.parameters = params
            .setForceHighestSupportedBitrate(false)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .build()
        return
    }
    // A chosen height is a ceiling. The ladder can still drop when that rung stalls.
    val concrete = options.firstOrNull { it.id == selectedId }
    val height = concrete?.height?.takeIf { it > 0 }
        ?: selectedId.removeSuffix("p").toIntOrNull()?.takeIf { it > 0 }
    selector.parameters = if (height != null) {
        params
            .setForceHighestSupportedBitrate(false)
            .setMaxVideoSize(Int.MAX_VALUE, height)
            .build()
    } else {
        params.build()
    }
}

@OptIn(UnstableApi::class)
private fun applyTextSelection(
    selector: DefaultTrackSelector,
    selectedId: String,
    options: List<TextOption>,
    enabled: Boolean,
) {
    val params = selector.buildUponParameters()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setSelectUndeterminedTextLanguage(true)
        .setPreferredTextLanguage("en")
    if (!enabled || selectedId == "off") {
        selector.parameters = params
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        return
    }
    params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
    if (selectedId == "auto") {
        // Preferred language picks English. Locking the first group used to
        // override that and play whatever track the file listed first.
        selector.parameters = params.build()
        return
    }
    val concrete = options.firstOrNull { it.id == selectedId }
    val mediaGroup = concrete?.mediaGroup
    if (mediaGroup != null) {
        params.setOverrideForType(
            TrackSelectionOverride(mediaGroup, listOf(concrete.trackIndex)),
        )
    }
    selector.parameters = params.build()
}

@OptIn(UnstableApi::class)
private fun applyCaptionStyle(playerView: PlayerView?, style: SubStyle, scale: Float = 1f) {
    val view = playerView?.subtitleView ?: return
    view.visibility = android.view.View.VISIBLE
    view.setApplyEmbeddedStyles(false)
    view.setApplyEmbeddedFontSizes(false)
    val alpha = (style.bgAlpha.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
    // Backdrop modes approximate caption plates on Media3 SubtitleView.
    val (bg, window) = when (style.bgMode) {
        SubBgMode.None -> AndroidColor.TRANSPARENT to AndroidColor.TRANSPARENT
        SubBgMode.Soft -> AndroidColor.TRANSPARENT to AndroidColor.argb((alpha * 0.7f).toInt(), 0, 0, 0)
        SubBgMode.Blur -> AndroidColor.argb((alpha * 0.45f).toInt(), 16, 16, 18) to
            AndroidColor.argb(alpha, 0, 0, 0)
        SubBgMode.Solid -> AndroidColor.argb(alpha, 0, 0, 0) to AndroidColor.TRANSPARENT
    }
    view.setStyle(
        CaptionStyleCompat(
            style.color,
            bg,
            window,
            style.edgeType,
            AndroidColor.BLACK,
            Typeface.create(
                Typeface.SANS_SERIF,
                when {
                    style.bold && style.italic -> Typeface.BOLD_ITALIC
                    style.bold -> Typeface.BOLD
                    style.italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                },
            ),
        ),
    )
    val metrics = view.resources.displayMetrics
    val sizeSp = style.sizeSp.coerceIn(SUB_SIZE_SP_MIN, SUB_SIZE_SP_MAX)
    // Exact sp → px. No hidden density floor that keeps captions oversized.
    val px = sizeSp * metrics.density * scale.coerceIn(0.45f, 1.4f)
    view.setFixedTextSize(Cue.TEXT_SIZE_TYPE_ABSOLUTE, px)
    view.setBottomPaddingFraction(style.bottomPad.coerceIn(SUB_BOTTOM_MIN, SUB_BOTTOM_MAX))
}

private fun loadSubStyle(prefs: android.content.SharedPreferences): SubStyle {
    val version = prefs.getInt("sub_style_v", 0)
    if (version < SUB_STYLE_VERSION && stockCaptionLook(prefs, version)) return SubStyle()
    val bgMode = when (prefs.getString("sub_bg_mode", null)) {
        SubBgMode.None.name -> SubBgMode.None
        SubBgMode.Soft.name -> SubBgMode.Soft
        SubBgMode.Blur.name -> SubBgMode.Blur
        SubBgMode.Solid.name -> SubBgMode.Solid
        else -> if (prefs.getBoolean("sub_bg", true)) SubBgMode.Soft else SubBgMode.None
    }
    val storedSize = if (prefs.contains("sub_size_sp")) {
        prefs.getFloat("sub_size_sp", DEFAULT_SUB_SIZE_SP)
    } else {
        DEFAULT_SUB_SIZE_SP
    }
    // The 10.5 default rewrite is only for looks saved before the overlay.
    // A size chosen on version 5 or later stays, even when it sits near 10.5.
    val sizeSp = when {
        version >= SUB_STYLE_OVERLAY && prefs.contains("sub_size_sp") -> storedSize
        !prefs.contains("sub_size_sp") -> DEFAULT_SUB_SIZE_SP
        kotlin.math.abs(storedSize - 10.5f) < 0.25f -> DEFAULT_SUB_SIZE_SP
        else -> storedSize
    }
    // Persist Low→Top as saved. Only clamp to valid slider bounds — never rewrite Low up.
    val rawBottom = if (prefs.contains("sub_bottom")) {
        prefs.getFloat("sub_bottom", DEFAULT_SUB_BOTTOM_PAD)
    } else {
        DEFAULT_SUB_BOTTOM_PAD
    }
    val bottom = if (rawBottom.isFinite()) {
        rawBottom.coerceIn(SUB_BOTTOM_MIN, SUB_BOTTOM_MAX)
    } else {
        DEFAULT_SUB_BOTTOM_PAD
    }
    return SubStyle(
        sizeSp = sizeSp.coerceIn(SUB_SIZE_SP_MIN, SUB_SIZE_SP_MAX),
        bottomPad = bottom,
        color = prefs.getInt("sub_color", AndroidColor.WHITE),
        bgMode = bgMode,
        edgeType = prefs.getInt("sub_edge", CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW),
        bold = prefs.getBoolean("sub_bold", false),
        italic = prefs.getBoolean("sub_italic", false),
        bgAlpha = prefs.getFloat("sub_bg_alpha", 0.55f).let { if (it.isFinite()) it.coerceIn(0.15f, 1f) else 0.55f },
    )
}

/**
 * True when the saved look is still a stock caption style we can replace.
 * The old 9/10.5sp soft-box fingerprint only applies before version 5.
 * From the overlay onward, the only stock look we replace is the untouched
 * drop-shadow default. A chosen size, box, or color stays.
 */
private fun stockCaptionLook(prefs: android.content.SharedPreferences, version: Int): Boolean {
    if (version >= SUB_STYLE_OVERLAY) return shadowStockFromOverlay(prefs)
    if (!prefs.contains("sub_size_sp") && !prefs.contains("sub_bg_mode")) return true
    val size = prefs.getFloat("sub_size_sp", 9f)
    val bg = prefs.getString("sub_bg_mode", SubBgMode.Soft.name)
    val edge = prefs.getInt("sub_edge", CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW)
    val color = prefs.getInt("sub_color", AndroidColor.WHITE)
    val bold = prefs.getBoolean("sub_bold", false)
    val previousSize = kotlin.math.abs(size - 9f) < 0.4f || kotlin.math.abs(size - 10.5f) < 0.25f
    return previousSize &&
        bg == SubBgMode.Soft.name &&
        edge == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW &&
        color == AndroidColor.WHITE &&
        !bold
}

/**
 * The 1.2.7-dev.6 stock look: 20sp italic, no box, drop shadow, light grey.
 * That was written as soon as the player opened, so it is not a custom choice.
 */
private fun shadowStockFromOverlay(prefs: android.content.SharedPreferences): Boolean {
    if (!prefs.contains("sub_size_sp") || !prefs.contains("sub_edge")) return false
    val size = prefs.getFloat("sub_size_sp", 0f)
    val bottom = prefs.getFloat("sub_bottom", -1f)
    val alpha = prefs.getFloat("sub_bg_alpha", -1f)
    return kotlin.math.abs(size - 20f) < 0.4f &&
        kotlin.math.abs(bottom - 0.20f) < 0.02f &&
        kotlin.math.abs(alpha - 0.55f) < 0.02f &&
        prefs.getString("sub_bg_mode", null) == SubBgMode.None.name &&
        prefs.getInt("sub_edge", -1) == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW &&
        prefs.getInt("sub_color", 0) == AndroidColor.rgb(242, 242, 242) &&
        !prefs.getBoolean("sub_bold", true) &&
        prefs.getBoolean("sub_italic", false)
}

/** Italic outlined captions, clear of the control pills. A saved custom look is kept. */
private const val SUB_STYLE_VERSION = 6
/** First saved version that already records an intentional overlay caption look. */
private const val SUB_STYLE_OVERLAY = 5
private const val DEFAULT_SUB_SIZE_SP = 20f
/** Free-range caption size — small enough for dense lines, large enough for big-screen theater. */
private const val SUB_SIZE_SP_MIN = 6f
private const val SUB_SIZE_SP_MAX = 72f
/** Sits the line just above the progress bar and control pills. */
private const val DEFAULT_SUB_BOTTOM_PAD = 0.20f
private const val SUB_BOTTOM_MIN = 0.03f
private const val SUB_BOTTOM_MAX = 0.30f

private fun formatPlayerClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun brightnessFraction(activity: Activity): Float {
    val current = activity.window.attributes.screenBrightness
    if (current >= 0f) return current.coerceIn(0.01f, 1f)
    val raw = android.provider.Settings.System.getInt(
        activity.contentResolver,
        android.provider.Settings.System.SCREEN_BRIGHTNESS,
        128,
    )
    return (raw / 255f).coerceIn(0.01f, 1f)
}

private fun applyBrightness(activity: Activity, fraction: Float) {
    activity.window.attributes = activity.window.attributes.apply {
        screenBrightness = fraction.coerceIn(0.01f, 1f)
    }
}

private fun volumeFraction(context: android.content.Context): Float {
    val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return 0f
    val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / max.toFloat()
}

private fun applyVolumeFraction(context: android.content.Context, fraction: Float) {
    val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return
    val stream = android.media.AudioManager.STREAM_MUSIC
    val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
    val next = (fraction.coerceIn(0f, 1f) * max).toInt().coerceIn(0, max)
    if (next != audio.getStreamVolume(stream)) audio.setStreamVolume(stream, next, 0)
}

private fun nudgeBrightness(context: android.content.Context, view: android.view.View, delta: Float): Float {
    val activity = context as? Activity ?: return 0.5f
    val base = brightnessFraction(activity)
    val next = (base + delta).coerceIn(0.01f, 1f)
    if (base <= 0.02f && next <= 0.02f || base >= 0.99f && next >= 0.99f) {
        KittyHaptics.perform(view, KittyHaptics.Kind.Tick)
    }
    activity.window.attributes = activity.window.attributes.apply { screenBrightness = next }
    return next
}

private fun nudgeVolume(context: android.content.Context, view: android.view.View, delta: Float): Float {
    val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return 0f
    val stream = android.media.AudioManager.STREAM_MUSIC
    val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
    val current = audio.getStreamVolume(stream)
    val steps = when {
        delta > 0.012f -> 1
        delta < -0.012f -> -1
        else -> 0
    }
    val next = (current + steps).coerceIn(0, max)
    if (steps != 0 && (next == 0 || next == max) && next != current) {
        KittyHaptics.perform(view, KittyHaptics.Kind.Tick)
    }
    if (next != current) audio.setStreamVolume(stream, next, 0)
    return next / max.toFloat()
}

private fun shortQuality(label: String): String {
    val m = Regex("""(\d{3,4}p)""", RegexOption.IGNORE_CASE).find(label)
    return m?.groupValues?.get(1) ?: label.take(12)
}

private fun resizeLabel(mode: Int): String = when (mode) {
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Fill"
    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
    else -> "Fit"
}

private fun menuQualityValue(raw: String): String {
    val parts = raw.split(" · ")
    return if (parts.size == 2) "${parts[0]} (${parts[1]})" else raw
}

private fun subtitlePercent(sizeSp: Float): Int =
    (sizeSp / DEFAULT_SUB_SIZE_SP * 100f).toInt().coerceIn(30, 360)

@Composable
private fun PlayerMenuSectionLabel(label: String) {
    Text(
        label.uppercase(),
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp, end = 16.dp),
    )
}

@Composable
private fun CaptionPreview(style: SubStyle) {
    val previewColor = Color(style.color)
    val previewBg = when (style.bgMode) {
        SubBgMode.None -> Color.Transparent
        SubBgMode.Soft -> Color.Black.copy(alpha = style.bgAlpha * 0.55f)
        SubBgMode.Blur -> Color.Black.copy(alpha = style.bgAlpha * 0.7f)
        SubBgMode.Solid -> Color.Black.copy(alpha = style.bgAlpha.coerceAtLeast(0.75f))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Sample subtitle · 字幕サンプル",
            color = previewColor,
            fontSize = style.sizeSp.coerceIn(12f, 40f).sp,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (style.italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
            maxLines = 2,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(previewBg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SubtitleSizePanel(
    style: SubStyle,
    onChange: (SubStyle) -> Unit,
) {
    val sizeSp = style.sizeSp
    val percent = subtitlePercent(sizeSp)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        CaptionPreview(style)
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "A",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier
                    .playerFocusCue(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onChange(style.copy(sizeSp = (sizeSp - 2f).coerceIn(SUB_SIZE_SP_MIN, SUB_SIZE_SP_MAX)))
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Slider(
                value = sizeSp,
                onValueChange = { onChange(style.copy(sizeSp = it.coerceIn(SUB_SIZE_SP_MIN, SUB_SIZE_SP_MAX))) },
                valueRange = SUB_SIZE_SP_MIN..SUB_SIZE_SP_MAX,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
            Text(
                "A",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .playerFocusCue(RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onChange(style.copy(sizeSp = (sizeSp + 2f).coerceIn(SUB_SIZE_SP_MIN, SUB_SIZE_SP_MAX)))
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Text(
            "$percent% · ${sizeSp.toInt()} sp",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listOf(50, 75, 100, 150, 200, 300).forEach { preset ->
                val sp = (DEFAULT_SUB_SIZE_SP * preset / 100f)
                    .coerceIn(SUB_SIZE_SP_MIN, SUB_SIZE_SP_MAX)
                val selected = kotlin.math.abs(sizeSp - sp) < 0.8f
                Text(
                    "$preset%",
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .playerFocusCue(RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
                        .clickable { onChange(style.copy(sizeSp = sp)) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        // Bold and italic are stored but had no UI until now.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            captionToggleChip("Bold", style.bold) { onChange(style.copy(bold = !style.bold)) }
            captionToggleChip("Italic", style.italic) { onChange(style.copy(italic = !style.italic)) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Reset to default",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .playerFocusCue(RoundedCornerShape(999.dp))
                .clip(RoundedCornerShape(999.dp))
                .clickable { onChange(SubStyle()) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun captionToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .playerFocusCue(RoundedCornerShape(999.dp))
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun SubtitleTimingPanel(
    style: SubStyle,
    onChange: (SubStyle) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        CaptionPreview(style)
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Bottom",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
            Slider(
                value = style.bottomPad,
                onValueChange = { onChange(style.copy(bottomPad = it.coerceIn(SUB_BOTTOM_MIN, SUB_BOTTOM_MAX))) },
                valueRange = SUB_BOTTOM_MIN..SUB_BOTTOM_MAX,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
        }
        Text(
            timingLabel(style.bottomPad),
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listOf("Low" to 0.06f, "Mid" to 0.12f, "High" to 0.18f, "Top" to 0.24f).forEach { (label, pad) ->
                captionToggleChip(label, kotlin.math.abs(style.bottomPad - pad) < 0.01f) {
                    onChange(style.copy(bottomPad = pad))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SubtitleBackgroundPanel(
    style: SubStyle,
    onChange: (SubStyle) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        CaptionPreview(style)
        Spacer(modifier = Modifier.height(16.dp))
        listOf(
            SubBgMode.None to "None",
            SubBgMode.Soft to "Soft",
            SubBgMode.Blur to "Blur",
            SubBgMode.Solid to "Solid",
        ).forEach { (mode, label) ->
            PlayerMenuRow(
                label = label,
                selected = style.bgMode == mode,
                onClick = { onChange(style.copy(bgMode = mode)) },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Opacity",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
            Slider(
                value = style.bgAlpha,
                onValueChange = { onChange(style.copy(bgAlpha = it.coerceIn(0.15f, 1f))) },
                valueRange = 0.15f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
        }
        Text(
            "${(style.bgAlpha * 100).toInt()}%",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * One-time hint for the player's touch gestures. Shown once per install and
 * dismissed by tapping anywhere on it; never shown on TV (remote focus instead).
 */
@Composable
private fun PlayerGestureHint(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Touch gestures",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Double-tap left or right to seek 10s\nDrag the left edge for brightness, the right edge for volume\nLong-press to play at 2×",
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Got it",
            color = Color.Black,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private enum class GestureHudKind { Brightness, Volume, Seek }

private data class GestureHud(
    val kind: GestureHudKind,
    val level: Float,
    val caption: String? = null,
)

private fun timingLabel(pad: Float): String = when {
    pad <= 0.09f -> "Low"
    pad <= 0.15f -> "Mid"
    pad <= 0.21f -> "High"
    else -> "Top"
}

private fun boostLabel(level: Int): String = when (level) {
    1 -> "Low"
    2 -> "Medium"
    3 -> "High"
    else -> "Off"
}

private fun edgeLabel(edge: Int): String = when (edge) {
    CaptionStyleCompat.EDGE_TYPE_OUTLINE -> "Outline"
    CaptionStyleCompat.EDGE_TYPE_NONE -> "None"
    else -> "Shadow"
}

private fun bgLabel(mode: SubBgMode): String = when (mode) {
    SubBgMode.None -> "None"
    SubBgMode.Soft -> "Soft"
    SubBgMode.Blur -> "Blur"
    SubBgMode.Solid -> "Solid"
}

private fun captionColors(): List<Pair<Int, String>> = listOf(
    AndroidColor.rgb(242, 242, 242) to "Grey",
    AndroidColor.WHITE to "White",
    AndroidColor.YELLOW to "Yellow",
    AndroidColor.CYAN to "Cyan",
    AndroidColor.rgb(255, 210, 140) to "Warm",
    AndroidColor.rgb(180, 255, 180) to "Mint",
    AndroidColor.rgb(220, 220, 255) to "Ice",
)

private fun captionColorLabel(color: Int): String =
    captionColors().firstOrNull { it.first == color }?.second ?: "Custom"

private fun sleepLabel(choice: Int): String = when (choice) {
    15 -> "15 min"
    30 -> "30 min"
    45 -> "45 min"
    -1 -> "End"
    else -> "Off"
}

internal fun speedLabel(speed: Float): String {
    return when {
        kotlin.math.abs(speed - 1f) < 0.01f -> "1×"
        speed == speed.toInt().toFloat() -> "${speed.toInt()}×"
        else -> String.format(Locale.US, "%.2g×", speed)
    }
}

private fun capturePlayerFrame(context: android.content.Context, playerView: PlayerView?) {
    val surfaceView = playerView?.videoSurfaceView as? android.view.SurfaceView
    val surface = surfaceView?.holder?.surface
    if (surfaceView == null || surface == null || !surface.isValid ||
        surfaceView.width <= 0 || surfaceView.height <= 0
    ) {
        Toast.makeText(context, "Couldn't capture this frame", Toast.LENGTH_SHORT).show()
        return
    }
    val bitmap = android.graphics.Bitmap.createBitmap(
        surfaceView.width,
        surfaceView.height,
        android.graphics.Bitmap.Config.ARGB_8888,
    )
    android.view.PixelCopy.request(
        surfaceView,
        bitmap,
        { result ->
            if (result != android.view.PixelCopy.SUCCESS) {
                Toast.makeText(context, "Couldn't capture this frame", Toast.LENGTH_SHORT).show()
                return@request
            }
            val dir = java.io.File(context.cacheDir, "frames").apply { mkdirs() }
            val file = java.io.File(dir, "frame-${System.currentTimeMillis()}.png")
            val wrote = runCatching {
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }.isSuccess
            if (!wrote) {
                Toast.makeText(context, "Couldn't capture this frame", Toast.LENGTH_SHORT).show()
                return@request
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(share, "Save frame")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        android.os.Handler(android.os.Looper.getMainLooper()),
    )
}

private fun formatClock(ms: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private class LoudnessHold {
    var effect: android.media.audiofx.LoudnessEnhancer? = null
    var session: Int = androidx.media3.common.C.AUDIO_SESSION_ID_UNSET
    var gain: Int = Int.MIN_VALUE

    fun release() {
        effect?.release()
        effect = null
        session = androidx.media3.common.C.AUDIO_SESSION_ID_UNSET
        gain = Int.MIN_VALUE
    }
}

internal fun formatClockPadded(ms: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

private fun formatDurationMinutes(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms.coerceAtLeast(0L)).coerceAtLeast(1L)
    return if (minutes == 1L) "1 minute" else "$minutes minutes"
}

private const val DEFAULT_UA = DOWNLOAD_USER_AGENT
