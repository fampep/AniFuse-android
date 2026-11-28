// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.anikitty.app.ui.components.stageGlassPlate
import site.anikitty.app.ui.theme.prefersReducedMotion

/** Phone scrubbers stay compact; tablets get a wider glass preview card. */
internal fun seekPreviewCardWidthDp(maxWidthDp: Float): Float =
    if (maxWidthDp < 480f) 118f else 156f

/** Footer under the still — `CHAPTER · mm:ss` or just the clock. */
internal fun seekPreviewFooter(timeLabel: String, chapterLabel: String?): String {
    val chapter = chapterLabel?.takeIf { it.isNotBlank() } ?: return timeLabel
    return "$chapter · $timeLabel"
}

/** TalkBack string for the floating scrub card. */
internal fun seekPreviewDescription(timeLabel: String, chapterLabel: String?): String {
    val chapter = chapterLabel?.takeIf { it.isNotBlank() }
    return if (chapter != null) {
        "Seek preview, $chapter, $timeLabel"
    } else {
        "Seek preview, $timeLabel"
    }
}

/**
 * Thumbnail above the scrubber with a single glass footer.
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
    frameKey: Long = 0L,
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
        val cardShape = RoundedCornerShape(16.dp)
        val fadeMs = scrubPreviewCrossfadeMs(prefersReducedMotion())
        val footer = seekPreviewFooter(timeLabel, chapterLabel)
        val a11y = seekPreviewDescription(timeLabel, chapterLabel)
        Column(
            modifier = Modifier
                .offset(x = x)
                .width(cardWidth)
                .clearAndSetSemantics { contentDescription = a11y },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .stageGlassPlate(shape = cardShape, elevated = true)
                    .border(
                        width = 1.dp,
                        color = seekChapterGlassBorder(chapterKind),
                        shape = cardShape,
                    ),
            ) {
                Column {
                    // Crossfade on bucket key — retain keeps [frame] stable across
                    // decode misses so the plate never blanks mid-drag.
                    Crossfade(
                        targetState = if (frame != null) frameKey else Long.MIN_VALUE,
                        animationSpec = tween(fadeMs),
                        label = "seekPreviewFrame",
                    ) { key ->
                        val bmp = frame
                        if (key != Long.MIN_VALUE && bmp != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(cardHeight)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                    .background(Color(0xCC101012)),
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(cardHeight),
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(cardHeight)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.White.copy(alpha = seekChapterGlassSheen(chapterKind)),
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.28f),
                                                ),
                                            ),
                                        ),
                                )
                            }
                        }
                    }
                    Text(
                        footer,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
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
