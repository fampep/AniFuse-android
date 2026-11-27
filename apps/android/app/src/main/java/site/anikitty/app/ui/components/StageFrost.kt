// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import site.anikitty.app.ui.theme.LocalKittyPalette

internal val LocalStageHaze = staticCompositionLocalOf<HazeState?> { null }
internal val LocalStageBackPose = compositionLocalOf { StageBackPose.Rest }

private val NightFrost = HazeStyle(
    backgroundColor = Color(0xFF101010),
    tint = HazeTint(Color(0xB3101010)),
    blurRadius = 28.dp,
    noiseFactor = 0.08f,
    fallbackTint = HazeTint(Color(0xE6101010)),
)

private val DayFrost = HazeStyle(
    backgroundColor = Color(0xFFF7F7F5),
    tint = HazeTint(Color.White.copy(alpha = 0.55f)),
    blurRadius = 28.dp,
    noiseFactor = 0.04f,
    fallbackTint = HazeTint(Color.White.copy(alpha = 0.94f)),
)

/**
 * One haze source and one predictive-back pose for the shell under this call.
 * Inline so screen `return`s inside [content] still leave the host composable.
 */
@Composable
internal fun ProvideStageChrome(
    pose: StageBackPose,
    content: @Composable () -> Unit,
) {
    val haze = remember { HazeState() }
    CompositionLocalProvider(
        LocalStageHaze provides haze,
        LocalStageBackPose provides pose,
        content = content,
    )
}

/** Samples the page so floating chrome can blur the artwork behind it. */
@Composable
internal fun Modifier.stageHazeSource(): Modifier {
    val state = LocalStageHaze.current ?: return this
    return this.hazeSource(state)
}

/**
 * Real backdrop blur on API 31+ when the device is not low-RAM.
 * Older releases and low-RAM phones get the monochrome scrim instead of a hole.
 */
@Composable
internal fun Modifier.stageFrosted(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val night = LocalKittyPalette.current.dark
    val state = LocalStageHaze.current
    val blur = frostBlurEnabled()
    val frosted = if (state != null) {
        this.clip(shape).hazeEffect(state, if (night) NightFrost else DayFrost) {
            blurEnabled = blur
        }
    } else {
        this.clip(shape).background(if (night) StageGlassFill else Color.White.copy(alpha = 0.96f))
    }
    val sheen = if (night) 0.16f else 0.42f
    val stroke = if (night) StageGlassStroke else LocalKittyPalette.current.hairline
    return frosted
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = sheen), Color.Transparent),
            ),
        )
        .border(1.dp, Color.White.copy(alpha = if (night) 0.20f else 0.44f), shape)
        .border(1.dp, stroke, shape)
}

@Composable
internal fun Modifier.stagePredictive(): Modifier {
    val pose = LocalStageBackPose.current
    if (pose == StageBackPose.Rest) return this
    return this.graphicsLayer {
        translationX = size.width * pose.translationFraction
        alpha = pose.alpha
        scaleX = pose.scale
        scaleY = pose.scale
    }
}

@Composable
internal fun frostBlurEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < 31) return false
    val context = LocalContext.current
    return remember(context) { !isLowRam(context) }
}

internal fun isLowRam(context: Context): Boolean {
    val manager = context.getSystemService(ActivityManager::class.java) ?: return false
    return manager.isLowRamDevice
}
