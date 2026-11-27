// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.anikitty.app.ui.app.spacePress
import site.anikitty.app.ui.theme.KittyElevation
import site.anikitty.app.ui.theme.KittyRadius
import site.anikitty.app.ui.theme.KittySpace
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.theme.prefersReducedMotion

/** Flat page plate. Use [KittyCard] when the block should read as a raised group. */
@Composable
fun KittySurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalKittyPalette.current
    Box(
        modifier = modifier.background(palette.page),
        content = { content() },
    )
}

@Composable
fun KittyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalKittyPalette.current
    val shape = RoundedCornerShape(KittyRadius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.spacePress(onClick) else Modifier)
            .background(palette.surface)
            .border(0.5.dp, palette.hairline, shape)
            .padding(KittySpace.md),
        content = content,
    )
}

@Composable
fun KittyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = prefersReducedMotion()
    val fill = if (selected) Color.White else Color(0xFF101010)
    val ink = if (selected) Color(0xFF080808) else Color(0xFFA1A1A1)
    val shape = RoundedCornerShape(KittyRadius.pill)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = if (reduced) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "chipScale",
    )
    Text(
        label,
        color = ink,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 12.sp,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill)
            .border(1.dp, if (selected) Color.White else Color(0xFF292929), shape)
            .spacePress(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Short label. Quality, audio, and the demo mark all use this. */
@Composable
fun KittyBadge(
    label: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = true,
) {
    val ink = if (onDark) Color.White else Color.Black
    val plate = if (onDark) Color(0xCC101010) else Color.White.copy(alpha = 0.92f)
    val shape = RoundedCornerShape(999.dp)
    Text(
        label,
        color = ink,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        maxLines = 1,
        modifier = modifier
            .background(plate, shape)
            .border(1.dp, ink.copy(alpha = 0.22f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun KittyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val palette = LocalKittyPalette.current
    Box(
        modifier = modifier
            .size(size)
            .spacePress(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = palette.text, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun KittyListRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val palette = LocalKittyPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .spacePress(onClick)
            .padding(KittySpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            androidx.compose.foundation.layout.Spacer(Modifier.size(KittySpace.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(detail, color = palette.muted, fontSize = 12.sp, maxLines = 1)
        }
        trailing?.invoke(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KittyBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalKittyPalette.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Color(0xFF101010),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentColor = palette.text,
        tonalElevation = KittyElevation.sheet,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = KittySpace.sm)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(KittyRadius.pill))
                    .background(Color.White.copy(alpha = 0.28f)),
            )
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = KittySpace.md, vertical = KittySpace.sm)) {
            Text(
                "PANEL",
                color = palette.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
            )
            Text(title, color = palette.text, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = (-0.4).sp)
            androidx.compose.foundation.layout.Spacer(Modifier.size(KittySpace.sm))
            content()
            androidx.compose.foundation.layout.Spacer(Modifier.size(KittySpace.lg))
        }
    }
}
