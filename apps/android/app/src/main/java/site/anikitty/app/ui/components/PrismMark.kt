// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The AniFuse mark — a rounded play triangle in solid white.
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
                color = Color.White.copy(alpha = 0.08f),
                radius = s * 0.48f,
                center = Offset(cx, cy),
            )
        }

        val path = Path().apply {
            moveTo(pt(31f, 40f).x, pt(31f, 40f).y)
            lineTo(pt(31f, 68f).x, pt(31f, 68f).y)
            lineTo(pt(77f, 54f).x, pt(77f, 54f).y)
            close()
        }
        drawPath(path, color = ink)
        drawPath(
            path,
            color = ink,
            style = Stroke(width = s * 0.045f, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }
}
