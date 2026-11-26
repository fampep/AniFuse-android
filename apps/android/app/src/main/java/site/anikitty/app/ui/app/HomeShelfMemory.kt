// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import site.anikitty.app.data.anilist.AnimeSummary

/**
 * Last Home shelves, kept for this process so leaving the tab does not paint
 * the shimmer and refetch trending before the user sees a poster.
 */
internal object HomeShelfMemory {
    var trending: List<AnimeSummary> = emptyList()
        private set
    var seasonal: List<AnimeSummary> = emptyList()
        private set
    var popular: List<AnimeSummary> = emptyList()
        private set
    var topTen: List<AnimeSummary> = emptyList()
        private set
    private var savedAt: Long = 0L

    fun fresh(now: Long = System.currentTimeMillis()): Boolean =
        trending.isNotEmpty() && now - savedAt < FRESH_MS

    fun save(
        trending: List<AnimeSummary>,
        seasonal: List<AnimeSummary>,
        popular: List<AnimeSummary>,
        topTen: List<AnimeSummary>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (trending.isEmpty()) return
        this.trending = trending
        this.seasonal = seasonal
        this.popular = popular
        this.topTen = topTen
        savedAt = now
    }

    private const val FRESH_MS = 30L * 60 * 1000
}
