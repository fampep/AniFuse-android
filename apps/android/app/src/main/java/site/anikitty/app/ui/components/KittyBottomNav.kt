// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import site.anikitty.app.ui.app.spacePress
import site.anikitty.app.ui.theme.KittyHaptics
import site.anikitty.app.ui.theme.LocalKittyPalette
import site.anikitty.app.ui.theme.prefersReducedMotion

data class KittyNavTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private data class DockColors(
    val fill: Color,
    val stroke: Color,
    val selected: Color,
    val idle: Color,
    val ink: Color,
)

@Composable
private fun dockColors(): DockColors {
    val palette = LocalKittyPalette.current
    return if (palette.dark) {
        DockColors(
            fill = Color(0xB3101010),
            stroke = Color(0xFF292929),
            selected = Color.White,
            idle = Color(0xFF707070),
            ink = Color(0xFF080808),
        )
    } else {
        DockColors(
            fill = Color.White.copy(alpha = 0.96f),
            stroke = palette.hairline,
            selected = palette.accent,
            idle = palette.muted,
            ink = palette.onAccent,
        )
    }
}

/**
 * Floating glass capsule. The fill is translucent so artwork shows through,
 * and one white pill slides to the selected tab.
 */
@Composable
fun KittyBottomNav(
    tabs: List<KittyNavTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    profileTabId: String? = null,
    profileImageUrl: String? = null,
) {
    val view = LocalView.current
    val reduced = prefersReducedMotion()
    val nightDock = LocalKittyPalette.current.dark
    val dock = dockColors()
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(
                    elevation = if (nightDock) 18.dp else 10.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = if (nightDock) 0.55f else 0.12f),
                    spotColor = Color.Black.copy(alpha = if (nightDock) 0.4f else 0.08f),
                )
                .clip(shape)
                .background(dock.fill)
                .border(1.dp, Color.White.copy(alpha = if (nightDock) 0.14f else 0.35f), shape)
                .border(1.dp, dock.stroke, shape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            if (tabs.isNotEmpty()) {
                val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                val slide by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = if (reduced) snap() else tween(280, easing = FastOutSlowInEasing),
                    label = "dockSlide",
                )
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val tabWidth = maxWidth / tabs.size
                    Box(
                        Modifier
                            .offset(x = tabWidth * slide)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(dock.selected),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            tabs.forEach { tab ->
                val selected = tab.id == selectedId
                val tint by animateColorAsState(
                    targetValue = if (selected) dock.ink else dock.idle,
                    animationSpec = if (reduced) snap() else tween(180),
                    label = "navTint",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                            contentDescription = tab.label
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                KittyHaptics.perform(view, KittyHaptics.Kind.Tick)
                                onSelect(tab.id)
                            },
                        )
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (tab.id == profileTabId && !profileImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(profileImageUrl)
                                .size(64)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (selected) 1.5.dp else 0.dp,
                                    color = if (selected) dock.ink else Color.Transparent,
                                    shape = CircleShape,
                                ),
                        )
                    } else {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = (-0.1).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            }
        }
    }
}

/**
 * Living-room version of the phone dock. Same destinations, sized for a remote.
 * Focus fills the row white so the selection is obvious from the couch.
 */
@Composable
fun KittySideNav(
    tabs: List<KittyNavTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dock = dockColors()
    val palette = LocalKittyPalette.current
    Column(
        modifier = modifier
            .width(196.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(dock.fill)
            .border(1.dp, dock.stroke, RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "AniFuse",
            color = palette.text,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        )
        tabs.forEach { tab ->
            val selected = tab.id == selectedId
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.hasFocus }
                    .clip(shape)
                    .background(if (focused || selected) dock.selected else Color.Transparent)
                    .border(
                        width = if (selected && !focused) 1.dp else 0.dp,
                        color = if (selected && !focused) dock.selected else Color.Transparent,
                        shape = shape,
                    )
                    .clickable { onSelect(tab.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                    contentDescription = null,
                    tint = if (focused || selected) dock.ink else dock.idle,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    tab.label,
                    color = if (focused || selected) dock.ink else dock.idle,
                    fontSize = 15.sp,
                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun KittyRoundIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .spacePress(onClick)
            .clip(CircleShape)
            .background(Color(0xE6141418))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
