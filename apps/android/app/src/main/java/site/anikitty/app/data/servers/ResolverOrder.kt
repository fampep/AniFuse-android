// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.data.servers

/**
 * The resolver the viewer picked stays first. Everyone else marked down by
 * the last provider-health snapshot moves behind the healthy ones, so the
 * race's head start is spent on sources that were answering.
 */
internal fun <T> sinkUnhealthy(
    ordered: List<T>,
    down: Set<String>,
    id: (T) -> String,
    name: (T) -> String,
): List<T> {
    if (ordered.size < 2 || down.isEmpty()) return ordered
    val needles = down.map { it.lowercase() }.toSet()
    val head = ordered.first()
    val (live, dead) = ordered.drop(1).partition { item ->
        val keys = listOf(id(item), name(item)).map { it.lowercase() }
        keys.none { it in needles }
    }
    return listOf(head) + live + dead
}

/**
 * Head start before the next resolvers join the race.
 * A preferred host we already know is down must not hold the episode for 900ms.
 */
internal fun resolveHeadStartMs(anyDown: Boolean, preferredCold: Boolean): Long = when {
    preferredCold -> 0L
    anyDown -> 350L
    else -> 900L
}
