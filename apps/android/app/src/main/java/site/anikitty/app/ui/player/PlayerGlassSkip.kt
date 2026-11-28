// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.anikitty.app.ui.components.stageGlassPlate
import site.anikitty.app.ui.theme.KittyHaptics

/** Human label for AniSkip / credits chips (Plex-style CTA copy). */
internal fun glassSkipLabel(kind: String): String = when (kind) {
    "intro" -> "Skip Intro"
    "credits" -> "Skip Outro"
    "recap" -> "Skip Recap"
    "preview" -> "Skip Preview"
    else -> "Skip"
}

/**
 * Bottom inset so the Skip chip clears the floating glass dock when chrome is up.
 * Cast transport is slightly taller; chrome-down stays flush to the safe edge.
 */
internal fun glassSkipBottomPaddingDp(chromeUp: Boolean, casting: Boolean): Dp = when {
    !chromeUp -> 22.dp
    casting -> 136.dp
    else -> 128.dp
}

@Composable
internal fun GlassSkipChip(
    kind: String,
    casting: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(if (casting) 16.dp else 14.dp)
    Text(
        glassSkipLabel(kind),
        color = androidx.compose.ui.graphics.Color.White,
        fontSize = if (casting) 15.sp else 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .stageGlassPlate(shape = shape, elevated = true)
            .clickable(role = Role.Button) {
                KittyHaptics.perform(view, KittyHaptics.Kind.Confirm)
                onSkip()
            }
            .clearAndSetSemantics { contentDescription = skipActionDescription(kind) }
            .padding(
                horizontal = if (casting) 18.dp else 16.dp,
                vertical = if (casting) 12.dp else 10.dp,
            ),
    )
}
