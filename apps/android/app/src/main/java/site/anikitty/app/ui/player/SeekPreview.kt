// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Thumbnail above the scrubber, timestamp under it. The card is clamped
 * to the bar so a phone edge never clips it.
 */
@Composable
internal fun SeekPreview(
    fraction: Float,
    timeLabel: String,
    frame: Bitmap?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // The card draws above the bar. Reporting height 0 keeps the
                // scrubber and the pills from jumping when a frame arrives.
                layout(placeable.width, 0) {
                    placeable.placeRelative(0, -placeable.height)
                }
            },
    ) {
        val narrow = maxWidth < 480.dp
        val cardWidth = if (narrow) 118.dp else 156.dp
        val cardHeight = cardWidth * 9f / 16f
        val clamped = fraction.coerceIn(0f, 1f)
        val x = (maxWidth * clamped - cardWidth / 2).coerceIn(0.dp, (maxWidth - cardWidth).coerceAtLeast(0.dp))
        val frameShape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .offset(x = x)
                .width(cardWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Empty plate is noise — time chip alone until the VTT frame lands.
            if (frame != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .shadow(
                            elevation = 18.dp,
                            shape = frameShape,
                            ambientColor = Color.Black.copy(alpha = 0.55f),
                            spotColor = Color.Black.copy(alpha = 0.4f),
                        )
                        .clip(frameShape)
                        .background(Color(0xCC101012))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            ),
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.28f), frameShape),
                ) {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(cardHeight),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardHeight)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.22f),
                                    ),
                                ),
                            ),
                    )
                }
            }
            val chipShape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(chipShape)
                    .background(Color(0xCC101012))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), chipShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    timeLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}
