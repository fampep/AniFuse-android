// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Home and search posters share this with the title backdrop.
 * Null outside the shared transition, so other screens stay plain.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class PosterMotion(
    val scope: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
    val bounds: BoundsTransform,
)

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalPosterMotion = compositionLocalOf<PosterMotion?> { null }

/**
 * One shared element per title. Home can show the same show in several rows;
 * the first poster in composition order owns the key.
 */
class PosterFlight {
    private val claimed = HashSet<Int>()

    fun claim(id: Int): Boolean = claimed.add(id)
}

val LocalPosterFlight = compositionLocalOf<PosterFlight?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPoster(id: Int): Modifier {
    val motion = LocalPosterMotion.current ?: return this
    val flight = LocalPosterFlight.current
    if (id <= 0) return this
    // Remembered so a press or image update does not drop the shared element.
    val owns = remember(id, flight) {
        flight?.claim(id) ?: true
    }
    if (!owns) return this
    return with(motion.scope) {
        sharedElement(
            rememberSharedContentState(key = "art-$id"),
            animatedVisibilityScope = motion.visibility,
            boundsTransform = motion.bounds,
        )
    }
}
