// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

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
