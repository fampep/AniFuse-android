// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import site.anikitty.app.data.skip.SkipTimes
import site.anikitty.app.ui.app.spacePress
import site.anikitty.app.ui.components.StageGlassDisc
import site.anikitty.app.ui.components.stageGlassPlate
import site.anikitty.app.ui.theme.KittyHaptics

/**
 * Phone theater chrome — Plex-inspired transport geometry, AniFuse glass skin.
 *
 * Center row: Previous · −10 · Play/Pause · +10 · Next
 * Bottom: scrubber + Plex elapsed/−remaining clock, then glass dock
 * (Episodes / Sources / Quality / Subs / Speed).
 */
@Composable
internal fun AniFusePlayerChrome(
    title: String,
    episodeLine: String,
    statusPill: String,
    playing: Boolean,
    speedLabel: String,
    qualityLabel: String,
    showEpisodes: Boolean,
    showPrevious: Boolean,
    showNext: Boolean,
    castUnavailable: String?,
    onCastUnavailable: (String) -> Unit,
    player: Player?,
    scrubbing: Boolean,
    scrubValue: Float,
    durationMs: Long,
    skipTimes: SkipTimes,
    thumbnails: site.anikitty.app.data.servers.ThumbnailTrack?,
    mediaUrl: String?,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onSettings: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onEpisodes: () -> Unit,
    onPrevious: () -> Unit,
    onSources: () -> Unit,
    onQuality: () -> Unit,
    onSubtitles: () -> Unit,
    onSpeed: () -> Unit,
    onNext: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(240.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f)),
                    ),
                ),
        )

        // Top chrome — title / episode stay readable like Plex metadata strip.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .then(
                        if (showEpisodes) {
                            Modifier.spacePress(onClick = onEpisodes)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    episodeLine,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (statusPill.isNotBlank()) {
                    Text(
                        statusPill,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .stageGlassPlate(shape = RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            ChromeIcon(Icons.Outlined.Lock, "Lock controls", onLock)
            CastRouteButton(
                unavailable = castUnavailable,
                onUnavailable = onCastUnavailable,
                modifier = Modifier.size(44.dp),
            )
            ChromeIcon(Icons.Outlined.Settings, "Settings", onSettings)
        }

        // Plex-style center transport — episode change sits next to seek/play.
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showPrevious) {
                RoundTransport(
                    Icons.Outlined.SkipPrevious,
                    "Previous episode",
                    48.dp,
                    onPrevious,
                )
            } else {
                Spacer(Modifier.size(48.dp))
            }
            RoundTransport(Icons.Outlined.Replay10, "Back 10 seconds", 56.dp, onSeekBack)
            RoundTransport(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                if (playing) "Pause" else "Play",
                84.dp,
                onPlayPause,
                primary = true,
            )
            RoundTransport(Icons.Outlined.Forward10, "Forward 10 seconds", 56.dp, onSeekForward)
            if (showNext) {
                RoundTransport(Icons.Outlined.SkipNext, "Next episode", 48.dp, onNext)
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            LiveTimeline(
                player = player,
                scrubbing = scrubbing,
                scrubValue = scrubValue,
                durationMs = durationMs,
                skipTimes = skipTimes,
                thumbnails = thumbnails,
                mediaUrl = mediaUrl,
                onScrub = onScrub,
                onScrubFinished = onScrubFinished,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .stageGlassPlate(shape = RoundedCornerShape(24.dp), elevated = true)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showEpisodes) {
                        ActionPill(
                            Icons.Outlined.VideoLibrary,
                            "Episodes",
                            onEpisodes,
                            emphasized = true,
                        )
                    }
                    ActionPill(Icons.Outlined.Layers, "Sources", onSources)
                    ActionPill(Icons.Outlined.Equalizer, qualityLabel, onQuality)
                    ActionPill(Icons.Outlined.ClosedCaption, "Subs", onSubtitles)
                    ActionPill(Icons.Outlined.Speed, speedLabel, onSpeed)
                }
            }
        }
    }
}

/** Whether the Plex-style transport should show previous / next episode discs. */
internal fun chromeShowsPreviousEpisode(showEpisodes: Boolean, episode: Int): Boolean =
    showEpisodes && episode > 1

internal fun chromeShowsNextEpisode(showNext: Boolean): Boolean = showNext

@Composable
private fun ChromeIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val view = LocalView.current
    StageGlassDisc(
        onClick = {
            KittyHaptics.perform(view, KittyHaptics.Kind.Tick)
            onClick()
        },
        size = 44.dp,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RoundTransport(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val view = LocalView.current
    StageGlassDisc(
        onClick = {
            KittyHaptics.perform(
                view,
                if (primary) KittyHaptics.Kind.Confirm else KittyHaptics.Kind.Tick,
            )
            onClick()
        },
        size = size,
        selected = primary,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (primary) Color.Black else Color.White,
            modifier = Modifier.size(if (size >= 80.dp) 38.dp else if (size > 52.dp) 26.dp else 22.dp),
        )
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                if (emphasized) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
            )
            .spacePress {
                KittyHaptics.perform(view, KittyHaptics.Kind.Tick)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
