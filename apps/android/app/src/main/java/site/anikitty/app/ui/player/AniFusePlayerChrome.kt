// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import site.anikitty.app.data.skip.SkipTimes

@Composable
internal fun AniFusePlayerChrome(
    title: String,
    episodeLine: String,
    statusPill: String,
    playing: Boolean,
    speedLabel: String,
    showEpisodes: Boolean,
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
    onSources: () -> Unit,
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
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    episodeLine,
                    color = Color(0xFFA1A1A1),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (statusPill.isNotBlank()) {
                    Text(
                        statusPill,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF292929), RoundedCornerShape(8.dp))
                            .background(Color(0xCC101010))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
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
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundTransport(Icons.Outlined.Replay10, "Back 10 seconds", 56.dp, onSeekBack)
            RoundTransport(
                if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                if (playing) "Pause" else "Play",
                72.dp,
                onPlayPause,
            )
            RoundTransport(Icons.Outlined.Forward10, "Forward 10 seconds", 56.dp, onSeekForward)
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showEpisodes) ActionPill(Icons.Outlined.VideoLibrary, "Episodes", onEpisodes)
                ActionPill(Icons.Outlined.Layers, "Sources", onSources)
                ActionPill(Icons.Outlined.ClosedCaption, "Subtitles", onSubtitles)
                ActionPill(Icons.Outlined.Speed, speedLabel, onSpeed)
                if (showNext) ActionPill(Icons.Outlined.SkipNext, "Next Ep", onNext)
            }
        }
    }
}

@Composable
private fun ChromeIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RoundTransport(icon: ImageVector, description: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xCC101010))
            .border(1.dp, Color(0xFF292929), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(if (size > 60.dp) 32.dp else 24.dp))
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xCC101010))
            .border(1.dp, Color(0xFF292929), RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
