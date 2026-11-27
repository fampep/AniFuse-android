// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import site.anikitty.app.ui.theme.LocalKittyPalette
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import site.anikitty.app.ui.theme.prefersReducedMotion

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val reduced = prefersReducedMotion()
    val holdMs = when {
        reduced -> 120L
        HomeShelfMemory.fresh() -> 420L
        else -> 900L
    }

    val progress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else 520, easing = FastOutSlowInEasing),
        label = "progress",
    )

    val sweepAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else 700, easing = FastOutSlowInEasing),
        label = "sweep",
    )

    val ink = LocalKittyPalette.current.text
    val fillAlphaAnim by animateFloatAsState(
        targetValue = if (sweepAnim > 0.6f || reduced) 1f else 0f,
        animationSpec = tween(if (reduced) 0 else 400, easing = LinearEasing),
        label = "fillAlpha",
    )

    LaunchedEffect(Unit) {
        delay(40)
        startAnimation = true
        delay(holdMs)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalKittyPalette.current.page)
            .windowInsetsPadding(WindowInsets.statusBars)
            .drawBehind {
                val radius = size.minDimension * (0.22f + 0.28f * progress)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ink.copy(alpha = 0.14f * progress),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val sweepWidth = 800f
        val currentX = sweepAnim * (sweepWidth * 2) - (sweepWidth / 2f)
        val strokeBrush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                ink.copy(alpha = 0.8f),
                ink,
                ink.copy(alpha = 0.8f),
                Color.Transparent,
                Color.Transparent,
            ),
            start = Offset(currentX - sweepWidth / 2f, 0f),
            end = Offset(currentX + sweepWidth / 2f, 0f),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .graphicsLayer {
                    val scale = 0.8f + (0.2f * progress)
                    scaleX = scale
                    scaleY = scale
                    translationY = 12f * (1f - progress)
                    alpha = progress.coerceIn(0f, 1f)
                },
        ) {
            Text(
                text = "ANIFUSE",
                color = ink.copy(alpha = 0.42f * fillAlphaAnim),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.2.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            site.anikitty.app.ui.components.PrismMark(
                modifier = Modifier.size(112.dp),
                glow = true,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "AniFuse",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.8).sp,
                        drawStyle = Stroke(
                            width = 2f,
                            join = StrokeJoin.Round,
                            cap = StrokeCap.Round,
                        ),
                    ),
                    color = ink.copy(alpha = 0.1f),
                )
                Text(
                    text = "AniFuse",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.8).sp,
                        brush = strokeBrush,
                        drawStyle = Stroke(
                            width = 3f,
                            join = StrokeJoin.Round,
                            cap = StrokeCap.Round,
                        ),
                    ),
                    color = Color.Unspecified,
                    modifier = Modifier.graphicsLayer { alpha = 0.9f },
                )
                Text(
                    text = "AniFuse",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.8).sp,
                    ),
                    color = ink.copy(alpha = fillAlphaAnim),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.34f * fillAlphaAnim.coerceIn(0.001f, 1f))
                    .height(1.dp)
                    .background(ink.copy(alpha = 0.72f * fillAlphaAnim)),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Artwork carries the colour. Chrome stays glass.",
                color = ink.copy(alpha = 0.48f * fillAlphaAnim),
                fontSize = 13.sp,
                letterSpacing = 0.2.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
