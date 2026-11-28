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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
internal fun GlassResolutionChipRow(
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
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
    Text(
        label,
        color = if (selected) Color.Black else Color.White,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
                shape = shape,
            )
            .clickable(role = Role.Button) {
                KittyHaptics.perform(view, KittyHaptics.Kind.Tick)
                onClick()
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
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .then(
                if (selected) {
                    Modifier
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
                } else {
                    Modifier.stageGlassPlate(shape = shape)
                },
            )
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
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
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
                color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
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
