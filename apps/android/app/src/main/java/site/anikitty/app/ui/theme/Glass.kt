// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val KittyGlassShape = RoundedCornerShape(24.dp)
val KittyGlassShapeTight = RoundedCornerShape(16.dp)
val KittyGlassShapePill = RoundedCornerShape(999.dp)

/** Floating dock / top chrome — translucent so artwork bleeds through. */
val KittyDockFill = Color(0xB30E0E0E)
val KittyDockStroke = Color(0x33FFFFFF)
val KittyChromeFill = Color(0xCC121212)
val KittyChromeStroke = Color(0x26FFFFFF)

/** Solid dark panel — no frosted sheen. */
fun Modifier.kittyGlass(
    shape: Shape = KittyGlassShape,
    elevated: Boolean = false,
): Modifier = this
    .shadow(
        elevation = if (elevated) 14.dp else 8.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.58f),
        spotColor = Color.Black.copy(alpha = 0.42f),
    )
    .clip(shape)
    .background(if (elevated) KittySurfaceElevated else KittySurface)
    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    .border(1.dp, KittyOutlineSoft, shape)

fun Modifier.kittyGlassSoft(shape: Shape = KittyGlassShape): Modifier = this
    .clip(shape)
    .background(KittyGlass)
    .border(1.dp, Color.White.copy(alpha = 0.08f), shape)

fun Modifier.kittyGlassField(shape: Shape = KittyGlassShapeTight): Modifier = this
    .clip(shape)
    .background(KittySurface)
    .border(1.dp, KittyOutlineSoft, shape)

fun Modifier.kittyGlassMirror(
    shape: Shape = KittyGlassShape,
    elevation: Dp = 12.dp,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.52f),
        spotColor = Color.Black.copy(alpha = 0.36f),
    )
    .clip(shape)
    .background(KittyGlassStrong)
    .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    .border(1.dp, KittyOutline, shape)

/** Floating chrome capsule used by the home bar and glass sheets. */
fun Modifier.kittyFloatingChrome(
    shape: Shape = KittyGlassShapePill,
    elevation: Dp = 16.dp,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.55f),
        spotColor = Color.Black.copy(alpha = 0.38f),
    )
    .clip(shape)
    .background(KittyChromeFill)
    .border(1.dp, KittyChromeStroke, shape)
    .border(1.dp, KittyOutlineSoft, shape)

/** Page atmosphere — dark glass gradient, or flat black in AMOLED mode. */
@Composable
fun KittyAtmosphere(modifier: Modifier = Modifier) {
    LookState.ensure(androidx.compose.ui.platform.LocalContext.current)
    val glass = LookState.look != "amoled"
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(kittyPageBrush(glass))
            .then(
                if (glass) {
                    Modifier.drawBehind {
                        // Top soft lift — reads as glass depth without colour.
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.035f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = size.height * 0.28f,
                            ),
                        )
                        // Bottom vignette so the floating dock sits on ink, not flat grey.
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.55f),
                                ),
                                startY = size.height * 0.72f,
                                endY = size.height,
                            ),
                        )
                        // Hairline horizon under the status area.
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(0f, size.height * 0.02f),
                            end = Offset(size.width, size.height * 0.02f),
                            strokeWidth = 1f,
                        )
                    }
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
fun KittyGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = KittyGlassShape,
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.kittyGlass(shape = shape, elevated = elevated),
        content = content,
    )
}
