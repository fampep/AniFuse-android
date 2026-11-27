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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import site.anikitty.app.ui.app.spacePress
import site.anikitty.app.ui.theme.prefersReducedMotion

val StageGlassFill = Color(0xD6101010)
val StageGlassStroke = Color(0xFF292929)
val StageGlassSheen = Color.White.copy(alpha = 0.10f)

/** Floating glass disc used by home chrome, headers, and search tools. */
@Composable
fun StageGlassDisc(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduced = prefersReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = if (reduced) {
            snap()
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "discScale",
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (selected) 10.dp else 6.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.40f),
            )
            .then(
                if (selected) {
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color.White, CircleShape)
                } else {
                    Modifier.stageFrosted(CircleShape)
                },
            )
            .spacePress(onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Soft glass plate with a top sheen — doors, empty states, continue art. */
fun Modifier.stageGlassPlate(
    shape: Shape = RoundedCornerShape(22.dp),
    elevated: Boolean = false,
): Modifier = this
    .shadow(
        elevation = if (elevated) 16.dp else 8.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.50f),
        spotColor = Color.Black.copy(alpha = 0.34f),
    )
    .clip(shape)
    .background(Color(0xFF101010))
    .background(
        Brush.verticalGradient(
            listOf(StageGlassSheen, Color.Transparent),
        ),
    )
    .border(0.5.dp, Color.White.copy(alpha = 0.22f), shape)

/** Progress fill that reads as a white light bar on charcoal. */
@Composable
fun StageProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = 4.dp,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .height(barHeight)
            .clip(shape)
            .background(Color(0xFF2C2C2C))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.72f), Color.White),
                    ),
                ),
        )
    }
}
