// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.player

import site.anikitty.app.data.skip.SkipTimes

/**
 * Fraction edges for intro/credits/recap/preview windows on the scrubber.
 * Crossing one while dragging should tick — the glass rail is the map.
 */
internal fun scrubMarkerEdges(skipTimes: SkipTimes, durationMs: Long): List<Float> {
    if (durationMs <= 0L || skipTimes.isEmpty) return emptyList()
    val dur = durationMs.toFloat()
    val edges = LinkedHashSet<Float>()
    for ((_, iv) in skipTimes.segments) {
        edges += (iv.startMs / dur).coerceIn(0f, 1f)
        edges += (iv.endMs / dur).coerceIn(0f, 1f)
    }
    return edges.sorted()
}

/** True when the scrub finger crossed a marker edge between [from] and [to]. */
internal fun scrubCrossedMarker(from: Float, to: Float, edges: List<Float>): Boolean {
    if (edges.isEmpty() || from == to) return false
    val a = from.coerceIn(0f, 1f)
    val b = to.coerceIn(0f, 1f)
    return edges.any { edge ->
        val left = a - edge
        val right = b - edge
        left == 0f || right == 0f || left * right < 0f
    }
}
