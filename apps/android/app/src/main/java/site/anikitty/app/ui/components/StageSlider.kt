// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import site.anikitty.app.ui.theme.KittyHaptics
import site.anikitty.app.ui.theme.prefersReducedMotion
import kotlin.math.abs

/**
 * Live pose of one slide while a finger is down.
 * [parallax] is a fraction of the page width (image lags the page).
 * [scale] and [alpha] ease with the offset so the gesture itself is the animation.
 */
internal data class StageSlidePose(
    val parallax: Float,
    val scale: Float,
    val alpha: Float,
    val veil: Float,
    val tilt: Float,
)

/** Smoothstep so scale and fade ease in the middle of a swipe, not in a straight line. */
internal fun stageEase(distance: Float): Float {
    val x = distance.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * [offset] is the signed page delta: 0 at rest, negative while the current
 * slide moves toward the next one. Translation stays linear with the finger.
 * Scale, opacity, and the soft veil use [stageEase].
 */
internal fun stageSlidePose(offset: Float): StageSlidePose {
    val distance = abs(offset).coerceIn(0f, 1f)
    val eased = stageEase(distance)
    return StageSlidePose(
        parallax = -offset * 0.42f,
        scale = 1f - eased * 0.11f,
        alpha = 1f - eased * 0.38f,
        veil = eased * 0.48f,
        tilt = offset * -9f,
    )
}

/**
 * Full-bleed image slider. Adjacent slides stay composed so the crossfade is
 * live during the drag. The snap after release uses a low-bounce spring.
 * [autoAdvanceMillis] is 0 when the slider should wait for a gesture.
 */
@Composable
fun StageSlider(
    pageCount: Int,
    modifier: Modifier = Modifier,
    selected: Int = 0,
    autoAdvanceMillis: Long = 0L,
    showIndex: Boolean = false,
    indexInset: Dp = 16.dp,
    onPageSettled: (Int) -> Unit = {},
    pageContent: @Composable BoxScope.(page: Int) -> Unit,
) {
    if (pageCount <= 0) return
    val reduced = prefersReducedMotion()
    val view = LocalView.current
    val pagerState = rememberPagerState(
        initialPage = selected.coerceIn(0, pageCount - 1),
        pageCount = { pageCount },
    )
    var resumeAutoAt by remember { mutableLongStateOf(0L) }
    var autoAdvancing by remember { mutableIntStateOf(0) }
    var settled by remember { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(selected, pageCount) {
        val target = selected.coerceIn(0, pageCount - 1)
        if (pagerState.isScrollInProgress || pagerState.currentPage == target) return@LaunchedEffect
        if (reduced) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress && autoAdvancing == 0) {
            resumeAutoAt = SystemClock.uptimeMillis() + 9_000L
        }
    }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        val page = pagerState.currentPage
        if (page == settled) return@LaunchedEffect
        settled = page
        KittyHaptics.perform(view, KittyHaptics.Kind.Tick)
        onPageSettled(page)
    }
    LaunchedEffect(pageCount, autoAdvanceMillis, reduced) {
        if (reduced || autoAdvanceMillis <= 0L || pageCount < 2) return@LaunchedEffect
        while (true) {
            delay(autoAdvanceMillis)
            if (pagerState.isScrollInProgress) continue
            if (SystemClock.uptimeMillis() < resumeAutoAt) continue
            autoAdvancing = 1
            try {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % pageCount)
            } finally {
                autoAdvancing = 0
            }
        }
    }

    Box(modifier) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = if (reduced) {
                    snap()
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                },
            ),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val offset = if (reduced) {
                0f
            } else {
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            }
            val pose = stageSlidePose(offset)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.62f)
                            translationX = pose.parallax * size.width
                            scaleX = pose.scale.coerceAtLeast(0.9f)
                            scaleY = pose.scale.coerceAtLeast(0.9f)
                            alpha = pose.alpha.coerceIn(0.55f, 1f)
                            rotationY = pose.tilt
                            cameraDistance = 16f * density
                        },
                ) {
                    pageContent(page)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = pose.veil.coerceIn(0f, 0.5f))),
                )
            }
        }
        if (showIndex && pageCount > 1) {
            val live = pagerState.currentPage + pagerState.currentPageOffsetFraction
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = indexInset),
            ) {
                repeat(pageCount) { index ->
                    val distance = abs(live - index).coerceIn(0f, 1f)
                    val selectedDot = distance < 0.5f
                    val width by animateDpAsState(
                        targetValue = if (selectedDot) 16.dp else 5.dp,
                        animationSpec = if (reduced) snap() else spring(stiffness = Spring.StiffnessMedium),
                        label = "stageDot",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(4.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 1f - distance * 0.62f)),
                    )
                }
            }
        }
    }
}
