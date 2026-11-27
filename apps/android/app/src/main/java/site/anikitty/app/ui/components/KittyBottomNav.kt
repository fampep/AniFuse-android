// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import site.anikitty.app.ui.theme.KittyDockFill
import site.anikitty.app.ui.theme.KittyDockStroke
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
    val sheenTop: Color,
)

@Composable
private fun dockColors(): DockColors {
    val palette = LocalKittyPalette.current
    return if (palette.dark) {
        DockColors(
            fill = KittyDockFill,
            stroke = KittyDockStroke,
            selected = Color.White,
            idle = Color(0xFF8A8A8A),
            ink = Color(0xFF080808),
            sheenTop = Color.White.copy(alpha = 0.16f),
        )
    } else {
        DockColors(
            fill = Color.White.copy(alpha = 0.96f),
            stroke = palette.hairline,
            selected = palette.accent,
            idle = palette.muted,
            ink = palette.onAccent,
            sheenTop = Color.White.copy(alpha = 0.42f),
        )
    }
}

/**
 * Floating glass capsule. Translucent fill so artwork shows through,
 * with a spring-driven white pill under the selected tab.
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
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = if (nightDock) 26.dp else 14.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = if (nightDock) 0.68f else 0.16f),
                    spotColor = Color.Black.copy(alpha = if (nightDock) 0.52f else 0.12f),
                )
                .clip(shape)
                .background(dock.fill)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(dock.sheenTop, Color.Transparent),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = if (nightDock) 0.20f else 0.44f), shape)
                .border(1.dp, dock.stroke, shape)
                .padding(horizontal = 5.dp, vertical = 5.dp),
        ) {
            if (tabs.isNotEmpty()) {
                val selectedIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
                val slide by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = if (reduced) {
                        snap()
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    },
                    label = "dockSlide",
                )
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val tabWidth = maxWidth / tabs.size
                    Box(
                        Modifier
                            .offset(x = tabWidth * slide)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(dock.selected)
                            .shadow(
                                elevation = 0.dp,
                                shape = RoundedCornerShape(22.dp),
                            ),
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
                        animationSpec = if (reduced) snap() else tween(160),
                        label = "navTint",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 1f,
                        animationSpec = if (reduced) snap() else tween(180, easing = FastOutSlowInEasing),
                        label = "navScale",
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
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            tab.label,
                            color = tint,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = (-0.15).sp,
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
            .clip(RoundedCornerShape(22.dp))
            .background(dock.fill)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
            .border(1.dp, dock.stroke, RoundedCornerShape(22.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 10.dp),
        ) {
            PrismMark(modifier = Modifier.size(22.dp), glow = false)
            Text(
                "AniFuse",
                color = palette.text,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = (-0.4).sp,
            )
        }
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
                    .padding(horizontal = 12.dp, vertical = 12.dp),
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
            .size(42.dp)
            .spacePress(onClick)
            .clip(CircleShape)
            .background(Color(0xE6141418))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
