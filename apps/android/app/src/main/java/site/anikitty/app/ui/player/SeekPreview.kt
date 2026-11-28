// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import site.anikitty.app.ui.theme.prefersReducedMotion

/** Phone scrubbers stay compact; tablets get a wider glass preview card. */
internal fun seekPreviewCardWidthDp(maxWidthDp: Float): Float =
    if (maxWidthDp < 480f) 118f else 156f

/**
 * Thumbnail above the scrubber, optional chapter chip + timestamp under it.
 * The card is clamped to the bar so a phone edge never clips it.
 */
@Composable
internal fun SeekPreview(
    fraction: Float,
    timeLabel: String,
    frame: Bitmap?,
    modifier: Modifier = Modifier,
    chapterLabel: String? = null,
    chapterKind: String? = null,
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
        val cardWidth = seekPreviewCardWidthDp(maxWidth.value).dp
        val cardHeight = cardWidth * 9f / 16f
        val clamped = fraction.coerceIn(0f, 1f)
        val x = (maxWidth * clamped - cardWidth / 2).coerceIn(0.dp, (maxWidth - cardWidth).coerceAtLeast(0.dp))
        val frameShape = RoundedCornerShape(12.dp)
        val fadeMs = scrubPreviewCrossfadeMs(prefersReducedMotion())
        Column(
            modifier = Modifier
                .offset(x = x)
                .width(cardWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Empty plate is noise — time chip alone until the first VTT frame lands.
            // Crossfade bucket hops so retained plates do not hard-cut.
            Crossfade(
                targetState = frame,
                animationSpec = tween(fadeMs),
                label = "seekPreviewFrame",
            ) { bmp ->
                if (bmp != null) {
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
                            bitmap = bmp.asImageBitmap(),
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
            }
            val chipShape = RoundedCornerShape(999.dp)
            AnimatedContent(
                targetState = chapterLabel?.takeIf { it.isNotBlank() },
                transitionSpec = {
                    fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs))
                },
                label = "seekPreviewChapter",
            ) { label ->
                if (label != null) {
                    val kind = chapterKind
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(chipShape)
                            .background(Color(0xE6101012))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = seekChapterGlassSheen(kind)),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                            .border(1.dp, seekChapterGlassBorder(kind), chipShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = timeLabel,
                transitionSpec = {
                    fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs))
                },
                label = "seekPreviewTime",
                modifier = Modifier.padding(top = if (chapterLabel.isNullOrBlank()) 6.dp else 4.dp),
            ) { label ->
                Box(
                    modifier = Modifier
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
                        label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                    )
                }
            }
            // Glass stem ties the floating card to the scrub rail.
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(2.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.42f),
                                Color.White.copy(alpha = 0.08f),
                            ),
                        ),
                    ),
            )
        }
    }
}
