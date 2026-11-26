// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import site.anikitty.app.data.skip.SkipTimes

/**
 * Flagship timeline — soft track, buffered rail, luminous progress bloom,
 * differentiated skip markers, springy glass thumb.
 */
@Composable
fun GlassScrubber(
    progress: Float,
    durationMs: Long,
    scrubbing: Boolean,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
    skipTimes: SkipTimes = SkipTimes(),
    buffered: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = if (scrubbing) {
            tween(0)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "scrubProgress",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (scrubbing) 1.42f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
        label = "thumbScale",
    )
    val latestOnScrub by rememberUpdatedState(onScrub)
    val latestOnFinished by rememberUpdatedState(onScrubFinished)
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    var latestFraction by remember { mutableFloatStateOf(clamped) }

    fun jumpBy(deltaMs: Long) {
        if (durationMs <= 0L) return
        val baseMs = latestFraction * durationMs
        val next = (baseMs + deltaMs).coerceIn(0f, durationMs.toFloat()) / durationMs
        latestFraction = next
        latestOnScrub(next)
        latestOnFinished(next)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) latestFraction = clamped
            }
            .semantics {
                contentDescription = seekBarDescription(clamped, durationMs)
                progressBarRangeInfo = ProgressBarRangeInfo(clamped.coerceIn(0f, 1f), 0f..1f)
                setProgress { value ->
                    if (durationMs <= 0L) return@setProgress false
                    val next = value.coerceIn(0f, 1f)
                    latestFraction = next
                    latestOnScrub(next)
                    latestOnFinished(next)
                    true
                }
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        jumpBy(-10_000)
                        true
                    }
                    Key.DirectionRight -> {
                        jumpBy(10_000)
                        true
                    }
                    else -> false
                }
            }
            .border(
                if (focused) 2.dp else 0.dp,
                Color.White.copy(alpha = if (focused) 0.85f else 0f),
                RoundedCornerShape(8.dp),
            ),
    ) {
        val trackWidthPx = with(density) { maxWidth.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(durationMs, trackWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val slop = viewConfiguration.touchSlop
                        val start = down.position
                        fun fractionAt(x: Float): Float {
                            if (trackWidthPx <= 0f) return 0f
                            return (x / trackWidthPx).coerceIn(0f, 1f)
                        }
                        var latest = fractionAt(start.x)
                        var dragging = false
                        var finished = false
                        try {
                            while (true) {
                                if (!dragging) {
                                    val event = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        awaitPointerEvent()
                                    }
                                    if (event == null) {
                                        dragging = true
                                        latestFraction = latest
                                        latestOnScrub(latest)
                                        continue
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) break
                                    if (!change.pressed) {
                                        latestFraction = latest
                                        latestOnScrub(latest)
                                        latestOnFinished(latest)
                                        finished = true
                                        break
                                    }
                                    val dx = change.position.x - start.x
                                    val dy = change.position.y - start.y
                                    if (dx * dx + dy * dy > slop * slop) {
                                        dragging = true
                                        change.consume()
                                        latest = fractionAt(change.position.x)
                                        latestFraction = latest
                                        latestOnScrub(latest)
                                    }
                                } else {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) {
                                        latestOnFinished(latest)
                                        finished = true
                                        break
                                    }
                                    change.consume()
                                    latest = fractionAt(change.position.x)
                                    latestFraction = latest
                                    latestOnScrub(latest)
                                }
                            }
                        } finally {
                            if (dragging && !finished) latestOnFinished(latest)
                        }
                    }
                },
        ) {
            val cy = size.height / 2f
            val trackH = (if (scrubbing) 6.dp else 4.5.dp).toPx()
            val radius = CornerRadius(trackH / 2f, trackH / 2f)
            val unplayed = Color(0xFF242426)
            val bufferedColor = Color(0xFF6E6E72)
            val played = Color.White

            drawRoundRect(
                color = unplayed,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = radius,
            )

            val bufferedW = size.width * buffered.coerceIn(0f, 1f)
            if (bufferedW > 0f) {
                drawRoundRect(
                    color = bufferedColor,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(bufferedW, trackH),
                    cornerRadius = radius,
                )
            }

            val progressW = size.width * animatedProgress
            if (progressW > 0f) {
                // Soft bloom under the white rail so the playhead reads luminous.
                val bloomH = trackH * 2.4f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = if (scrubbing) 0.28f else 0.16f),
                            Color.White.copy(alpha = 0f),
                        ),
                    ),
                    topLeft = Offset(0f, cy - bloomH / 2f),
                    size = Size(progressW, bloomH),
                    cornerRadius = CornerRadius(bloomH / 2f, bloomH / 2f),
                )
                drawRoundRect(
                    color = played,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(progressW, trackH),
                    cornerRadius = radius,
                )
            }

            if (durationMs > 0 && !skipTimes.isEmpty) {
                val dur = durationMs.coerceAtLeast(1L).toFloat()
                skipTimes.segments.forEach { (kind, iv) ->
                    val start = (iv.startMs / dur).coerceIn(0f, 1f)
                    val end = (iv.endMs / dur).coerceIn(0f, 1f)
                    val left = size.width * start
                    val w = (size.width * (end - start)).coerceAtLeast(trackH)
                    val marker = when (kind) {
                        "intro" -> Color.White.copy(alpha = 0.55f)
                        "credits" -> Color.White.copy(alpha = 0.32f)
                        else -> Color.White.copy(alpha = 0.22f)
                    }
                    drawRoundRect(
                        color = marker,
                        topLeft = Offset(left, cy - trackH / 2f),
                        size = Size(w, trackH),
                        cornerRadius = radius,
                    )
                }
            }

            val thumbR = 7.dp.toPx() * thumbScale
            val thumbX = progressW.coerceIn(thumbR, (size.width - thumbR).coerceAtLeast(thumbR))
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = thumbR + 3.dp.toPx(),
                center = Offset(thumbX, cy),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = thumbR + 1.5.dp.toPx(),
                center = Offset(thumbX, cy),
            )
            drawCircle(
                color = Color.White,
                radius = thumbR,
                center = Offset(thumbX, cy),
            )
            if (scrubbing) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.18f),
                    radius = thumbR * 0.32f,
                    center = Offset(thumbX, cy),
                )
            }
        }
    }
}
