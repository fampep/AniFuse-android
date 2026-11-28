// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

/**
 * Glass Race dock labels — LEAD stays on the selected chip; everyone else
 * shows live CDN pulse so the dock drives the resolve race, not just decoration.
 */
internal fun raceDockPulseLabel(
    selected: Boolean,
    cold: Boolean,
    score: Int,
    latencyMs: Long?,
): String? = when {
    // Lead still owns the chip, but surface the live RTT so the dock feels wired.
    selected -> raceDockLeadLabel(latencyMs)
    cold || score < 0 -> "DOWN"
    latencyMs != null && latencyMs > 0L -> "${latencyMs.coerceAtMost(9_999L)}ms"
    score > 0 -> "HOT"
    else -> null
}

/** Selected chip: LEAD, or LEAD · 42ms when a probe has answered. */
internal fun raceDockLeadLabel(latencyMs: Long?): String {
    val ms = latencyMs?.takeIf { it > 0L }?.coerceAtMost(9_999L)
    return if (ms != null) "LEAD · ${ms}ms" else "LEAD"
}

internal fun <T> raceDockOrdered(
    servers: List<T>,
    scoreOf: (T) -> Int,
): List<T> = servers.sortedByDescending(scoreOf)
