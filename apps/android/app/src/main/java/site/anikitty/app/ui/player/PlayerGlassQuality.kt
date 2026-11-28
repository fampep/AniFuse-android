// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.anikitty.app.ui.components.stageGlassPlate
import site.anikitty.app.ui.theme.KittyHaptics
import site.anikitty.app.ui.theme.PlayerChrome
import site.anikitty.app.ui.tv.isTelevision

/** Compact chip label for Auto / 1080p / 720p rows. */
internal fun glassResolutionChipLabel(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "Auto"
    val auto = trimmed.startsWith("Auto", ignoreCase = true)
    val height = Regex("""(\d{3,4})\s*[pP]""").find(trimmed)?.groupValues?.getOrNull(1)
    return when {
        auto && height != null -> "Auto · ${height}p"
        auto -> "Auto"
        height != null -> "${height}p"
        else -> trimmed.take(18)
    }
}

/** TalkBack string for a resolution chip. */
internal fun glassResolutionChipDescription(label: String, selected: Boolean): String =
    if (selected) "$label, selected" else label

/** Secondary line under a source card — server · quality · audio. */
internal fun glassSourceMetaLine(
    serverName: String?,
    quality: String?,
    audio: String?,
    subtitleFallback: String? = null,
): String {
    val primary = listOfNotNull(
        serverName?.takeIf { it.isNotBlank() },
        quality?.takeIf { it.isNotBlank() },
        audio?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    if (primary.isNotBlank()) return primary
    return subtitleFallback?.takeIf { it.isNotBlank() }.orEmpty()
}

/** Race-dock style RTT badge, or null when the host has no sample. */
internal fun glassSourceRttLabel(latencyMs: Long?): String? {
    val ms = latencyMs?.takeIf { it > 0L } ?: return null
    return if (ms >= 1000L) {
        String.format("%.1fs", ms / 1000.0)
    } else {
        "${ms}ms"
    }
}

/**
 * Playlist source pulse — LEAD on the playing row, live ms / HOT / DOWN on
 * the rest. Same vocabulary as the Store Race dock so Sources feels wired.
 */
internal fun glassSourcePulseLabel(
    selected: Boolean,
    latencyMs: Long?,
    score: Int = 0,
    cold: Boolean = false,
): String? = site.anikitty.app.ui.app.raceDockPulseLabel(
    selected = selected,
    cold = cold,
    score = score,
    latencyMs = latencyMs,
)

/** Armed shadow failover chip — never LEAD; always STANDBY · ms when probed. */
internal fun glassShadowStandbyLabel(latencyMs: Long?): String =
    site.anikitty.app.ui.app.raceDockStandbyLabel(latencyMs)

/** Meta under a standby card — server · quality · audio · failover. */
internal fun glassShadowStandbyMeta(
    serverName: String?,
    quality: String?,
    audio: String?,
): String {
    val base = glassSourceMetaLine(serverName, quality, audio)
    return if (base.isBlank()) "Failover standby" else "$base · failover"
}

/**
 * Theater status pill — append STANDBY · ms when a fresh shadow is armed so
 * failover is visible before the Sources sheet opens.
 */
internal fun glassTheaterStatusWithShadow(
    baseParts: List<String>,
    shadowReady: Boolean,
    standbyLatencyMs: Long?,
): String {
    val parts = ArrayList<String>(baseParts.size + 1)
    for (part in baseParts) {
        if (part.isNotBlank()) parts += part
    }
    if (shadowReady) parts += glassShadowStandbyLabel(standbyLatencyMs)
    return parts.joinToString(" · ")
}

@Composable
internal fun GlassResolutionChipRow(
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val television = isTelevision()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Horizontal scroll eats D-pad left/right on TV — phone only.
            .horizontalScroll(rememberScrollState(), enabled = !television)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (id, label) ->
            GlassResolutionChip(
                label = glassResolutionChipLabel(label),
                selected = id == selectedId,
                onClick = { onSelect(id) },
            )
        }
    }
}

@Composable
internal fun GlassResolutionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(999.dp)
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .menuInitialFocus()
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clip(shape)
            .background(
                when {
                    selected -> Color.White
                    focused -> Color.White.copy(alpha = 0.18f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
            )
            .border(
                // Constant 2.dp so focus does not resize the chip (TV-safe).
                width = 2.dp,
                color = when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.0f)
                    else -> Color.White.copy(alpha = 0.16f)
                },
                shape = shape,
            )
            .clickable(role = Role.Button) {
                KittyHaptics.perform(view, KittyHaptics.Kind.Tick)
                onClick()
            }
            .clearAndSetSemantics {
                contentDescription = glassResolutionChipDescription(label, selected)
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
internal fun GlassSourceCard(
    title: String,
    meta: String,
    rttLabel: String?,
    selected: Boolean,
    onClick: () -> Unit,
    cardModifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(16.dp)
    var focused by remember { mutableStateOf(false) }
    val plate = when {
        selected -> Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(2.dp, if (focused) Color.White else Color.White.copy(alpha = 0.28f), shape)
        focused -> Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(2.dp, Color.White, shape)
        else -> Modifier
            .stageGlassPlate(shape = shape)
            .border(2.dp, Color.Transparent, shape)
    }
    Row(
        modifier = cardModifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .menuInitialFocus()
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .then(plate)
            .clickable(role = Role.Button) {
                KittyHaptics.perform(view, KittyHaptics.Kind.Confirm)
                onClick()
            }
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(title)
                    if (meta.isNotBlank()) append(", ").append(meta)
                    if (rttLabel != null) append(", ").append(rttLabel)
                    if (selected) append(", selected")
                }
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Dns,
            contentDescription = null,
            tint = if (selected || focused) Color.White else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = PlayerChrome.Paper,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    color = PlayerChrome.Faint,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (rttLabel != null) {
            Text(
                rttLabel,
                color = if (selected || focused) Color.White else Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = if (selected) 0.18f else 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
            )
        }
    }
}
