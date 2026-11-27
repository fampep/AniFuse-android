// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import site.anikitty.app.ui.icons.FluentuiSystemIconsPersonOutline
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.components.KittyAttention
import site.anikitty.app.ui.theme.KittyHaptics
import site.anikitty.app.ui.theme.KittyMotion
import site.anikitty.app.ui.theme.prefersReducedMotion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.anikitty.app.ui.tv.awaitTvFocus
import site.anikitty.app.ui.tv.isTelevision

/** Round AniList picture, the same mark My Space uses next to the name. */
@Composable
fun ProfileMark(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val palette = LocalKittyPalette.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "AniList profile",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            site.anikitty.app.ui.components.KittyFace(
                modifier = Modifier.size(size * 0.82f),
                contentDescription = "Profile",
            )
        }
    }
}
private val SpaceShape = RoundedCornerShape(22.dp)

/**
 * Press sinks the control, then a spring brings it back. Reduced motion stays still.
 * On a television the same control grows slightly and draws a white focus stroke.
 * [grabFocus] is for the first action on a television screen, such as Play.
 */
fun Modifier.spacePress(onClick: () -> Unit): Modifier =
    spacePress(onClick = onClick, grabFocus = false)

fun Modifier.spacePress(onClick: () -> Unit, grabFocus: Boolean): Modifier = composed {
    val reduced = prefersReducedMotion()
    val television = isTelevision()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            !reduced && pressed -> 0.96f
            television && focused && !reduced -> 1.02f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press",
    )
    val view = androidx.compose.ui.platform.LocalView.current
    val requester = remember { FocusRequester() }
    if (grabFocus) {
        LaunchedEffect(Unit) { awaitTvFocus(requester) }
    }
    var chain = graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    if (television) {
        chain = chain
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
    }
    if (grabFocus) {
        chain = chain.focusRequester(requester)
    }
    chain.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = {
            KittyHaptics.perform(view, KittyHaptics.Kind.Confirm)
            KittyAttention.nudge(0.25f, 0f)
            onClick()
        },
    )
}

@Composable
fun SpacePageHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
) {
    val palette = LocalKittyPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .border(1.dp, palette.hairline, CircleShape)
                    .spacePress(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = palette.text,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = title,
            color = palette.text,
            fontWeight = FontWeight.Black,
            fontSize = 34.sp,
            letterSpacing = (-1.1).sp,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun SpaceLabel(text: String, padded: Boolean = true) {
    Text(
        text = text,
        modifier = if (padded) Modifier.padding(horizontal = 20.dp) else Modifier,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        ),
        color = LocalKittyPalette.current.muted,
    )
}

@Composable
fun SpaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    grabFocus: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalKittyPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(SpaceShape)
            .then(if (onClick != null) Modifier.spacePress(onClick, grabFocus) else Modifier)
            .background(palette.surface)
            .border(0.5.dp, palette.hairline, SpaceShape)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun SpaceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalKittyPalette.current
    val text by animateColorAsState(
        if (selected) palette.text else palette.muted,
        tween(KittyMotion.duration(180)),
        label = "chipText",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .spacePress(onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(width = if (selected) 22.dp else 0.dp, height = 2.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.Transparent),
        )
    }
}
