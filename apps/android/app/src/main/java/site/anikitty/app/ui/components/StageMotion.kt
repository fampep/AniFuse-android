// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import site.anikitty.app.ui.theme.prefersReducedMotion

/**
 * One short rise for content that replaces a shimmer.
 * The caller owns when this enters composition, so a scroll recycle does not replay it.
 */
@Composable
internal fun Modifier.stageArrive(): Modifier {
    val reduced = prefersReducedMotion()
    if (reduced) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 12.dp.toPx()
    }
}

/**
 * Shared-axis pose for a predictive back swipe.
 * The translation matches the stage exit (`width / 6`) so the gesture and the
 * transition are the same motion, not two different slides.
 */
internal data class StageBackPose(
    val translationFraction: Float,
    val alpha: Float,
    val scale: Float,
) {
    companion object {
        val Rest = StageBackPose(translationFraction = 0f, alpha = 1f, scale = 1f)
    }
}

internal const val STAGE_EDGE_LEFT = 0
internal const val STAGE_EDGE_RIGHT = 1

/** Fraction of the width the shared-axis exit travels. Keep in step with the stage transition. */
internal const val STAGE_BACK_TRAVEL = 1f / 6f

internal fun stageBackPose(progress: Float, edge: Int, reduced: Boolean = false): StageBackPose {
    if (reduced) return StageBackPose.Rest
    val p = progress.coerceIn(0f, 1f)
    if (p == 0f) return StageBackPose.Rest
    val sign = if (edge == STAGE_EDGE_RIGHT) -1f else 1f
    return StageBackPose(
        translationFraction = sign * p * STAGE_BACK_TRAVEL,
        alpha = 1f - p * 0.34f,
        scale = 1f - p * 0.04f,
    )
}

/**
 * Mutable bridge so a back callback registered once still sees the latest
 * screen, even if the predictive-back API keeps the first lambda.
 */
internal class StageBackBridge {
    var slide: Boolean = true
    var reduced: Boolean = false
    var onPose: (StageBackPose) -> Unit = {}
    var onCommit: () -> Unit = {}
    var onHaptic: () -> Unit = {}
}
