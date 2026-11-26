// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The AniFuse mark — a bold capital A in solid white.
 * Artwork carries colour; the logo does not.
 */
@Composable
fun PrismMark(
    modifier: Modifier = Modifier,
    glow: Boolean = true,
    contentDescription: String? = "AniFuse",
) {
    Canvas(
        modifier = modifier.semantics {
            if (contentDescription != null) {
                this.contentDescription = contentDescription
            }
        },
    ) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f

        fun pt(x: Float, y: Float) = Offset(cx + (x - 54f) / 108f * s, cy + (y - 54f) / 108f * s)

        val ink = Color.White

        if (glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = s * 0.52f,
                ),
                radius = s * 0.52f,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = s * 0.38f,
                center = Offset(cx, cy),
                style = Stroke(width = s * 0.016f),
            )
        }

        // Geometric capital A with a cut-out crossbar counter.
        val letter = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(pt(54f, 28f).x, pt(54f, 28f).y)
            lineTo(pt(72.5f, 78f).x, pt(72.5f, 78f).y)
            lineTo(pt(64.2f, 78f).x, pt(64.2f, 78f).y)
            lineTo(pt(59.6f, 64.5f).x, pt(59.6f, 64.5f).y)
            lineTo(pt(48.4f, 64.5f).x, pt(48.4f, 64.5f).y)
            lineTo(pt(43.8f, 78f).x, pt(43.8f, 78f).y)
            lineTo(pt(35.5f, 78f).x, pt(35.5f, 78f).y)
            close()
            moveTo(pt(50.6f, 57.8f).x, pt(50.6f, 57.8f).y)
            lineTo(pt(57.4f, 57.8f).x, pt(57.4f, 57.8f).y)
            lineTo(pt(54f, 47.2f).x, pt(54f, 47.2f).y)
            close()
        }
        drawPath(letter, color = ink)
    }
}
