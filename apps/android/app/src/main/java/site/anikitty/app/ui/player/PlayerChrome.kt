// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package site.anikitty.app.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import site.anikitty.app.ui.tv.isTelevision

@Composable
internal fun ChromeDisc(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    large: Boolean = false,
    accent: Boolean = false,
    focusRequester: FocusRequester? = null,
    content: @Composable () -> Unit,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    var focused by remember { mutableStateOf(false) }
    site.anikitty.app.ui.components.StageGlassDisc(
        onClick = {
            site.anikitty.app.ui.theme.KittyHaptics.perform(
                view,
                if (accent || large) {
                    site.anikitty.app.ui.theme.KittyHaptics.Kind.Confirm
                } else {
                    site.anikitty.app.ui.theme.KittyHaptics.Kind.Tick
                },
            )
            onClick()
        },
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    Modifier.border(2.dp, Color.White, CircleShape)
                } else {
                    Modifier
                },
            ),
        size = size,
        selected = accent || focused,
        content = { content() },
    )
}

@Composable
internal fun LiveClockText(
    player: Player?,
    scrubbing: Boolean,
    scrubValue: Float,
    durationMs: Long,
    modifier: Modifier = Modifier,
    /** Plex timeline: elapsed left, −remaining right under the scrubber. */
    spread: Boolean = false,
) {
    var liveMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, scrubbing) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            if (!scrubbing) {
                val next = p.currentPosition.coerceAtLeast(0L)
                if (next != liveMs) liveMs = next
            }
            delay(200)
        }
    }
    val shown = if (scrubbing) (scrubValue * durationMs).toLong() else liveMs
    val remain = (durationMs - shown).coerceAtLeast(0L)
    val semantics = Modifier.clearAndSetSemantics {
        contentDescription = playbackClockDescription(shown, durationMs)
    }
    if (spread) {
        Row(
            modifier = modifier.then(semantics),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatClockPadded(shown),
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                "−${formatClockPadded(remain)}",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    } else {
        Text(
            "${formatClockPadded(shown)}  −${formatClockPadded(remain)}",
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = modifier.then(semantics),
        )
    }
}

@Composable
internal fun PlayerCapsule(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val television = isTelevision()
    Row(
        modifier = Modifier
            .height(if (television) 42.dp else 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            // A scrollable row eats D-pad left/right, so the remote never reaches the other controls.
            .horizontalScroll(rememberScrollState(), enabled = !television)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * White ring while focus sits on this control.
 * Apply it on the node that receives focus. A wrapper around a text field
 * never becomes focused itself. Width stays constant. A 0dp stroke grows
 * the button when it gains focus, and disposing that node mid-click crashes
 * the player.
 */
@Composable
internal fun Modifier.playerFocusCue(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.hasFocus }
        .border(
            width = 2.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = shape,
        )
}

@Composable
internal fun PillIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val box = if (isTelevision()) 40.dp else 48.dp
    val glyph = if (isTelevision()) 18.dp else 22.dp
    Box(
        modifier = Modifier
            .size(box)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .playerFocusCue(CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(glyph),
        )
    }
}

@Composable
internal fun PillLabel(
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    val box = if (isTelevision()) 40.dp else 48.dp
    Box(
        modifier = Modifier
            .size(box)
            .playerFocusCue(CircleShape)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
        )
    }
}
