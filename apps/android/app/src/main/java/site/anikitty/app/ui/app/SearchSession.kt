// Copyright 2026 fampep
// Licensed under the Apache License, Version 2.0. See the LICENSE file.

package site.anikitty.app.ui.app

import site.anikitty.app.data.anilist.AnimeSummary

/** Last search grid, so a tab switch does not show an empty "nothing matches". */
internal object SearchSession {
    var query: String = ""
    var items: List<AnimeSummary> = emptyList()

    fun restore(query: String): List<AnimeSummary> {
        if (query.trim().length < 2) return emptyList()
        if (!this.query.equals(query.trim(), ignoreCase = true)) return emptyList()
        return items
    }

    fun save(query: String, items: List<AnimeSummary>) {
        this.query = query.trim()
        this.items = items
    }
}
